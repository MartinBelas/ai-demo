# AI Demo

AI Demo is a Java 21 console application demonstrating provider-independent LLM integration, streaming responses, conversation persistence, reasoning output, and tool calling.

The supported providers are local Ollama, OpenAI, GroqCloud, and Gemini API. Application and service layers depend on abstractions, so providers can be selected without changing business logic.

## Features

- Java 21
- Clean layered architecture
- Provider-independent `LlmClient`
- Ollama integration using Java `HttpClient`
- OpenAI Responses API integration
- Provider selection through configuration
- Runtime provider switching without losing conversation history
- Streaming content and thinking output
- Persistent conversation history
- Agent-based tool calling
- Built-in calculator tool
- Prompt templates
- Token usage and response-duration reporting
- Application-specific exceptions
- SLF4J and Logback logging
- JUnit 6 and Mockito tests
- Google Java Format through Spotless

## Architecture

```text
ai.demo
├── agent        Agent orchestration and tool calling
├── api          HTTP server, REST endpoints, and SSE transport
├── client       External LLM provider communication
├── config       Application configuration
├── console      Console user interface and commands
├── exception    Application-specific exceptions
├── model        Provider-independent domain models
├── persistence  Conversation storage
├── prompt       Prompt composition and templates
└── service      Application logic
```

The main request flow is:

```text
ConsoleChat
    |
    v
ChatService
    |
    v
Agent
    |
    v
AgentLlmGateway
    |
    v
LlmClient
    |
    v
OllamaClient
```

Services do not depend directly on Ollama or its DTOs.

## Agent and Tool Calling

The agent first asks the LLM whether a tool is required.

Without a tool:

```text
user request → LLM → final response
```

With a tool:

```text
user request
    → LLM tool decision
    → local tool execution
    → LLM processes the tool result
    → final response
```

Tool-based requests normally take longer and use more tokens because the LLM is called twice.

### Calculator Tool

The built-in calculator supports:

- addition: `+`
- subtraction: `-`
- multiplication: `*`
- division: `/`
- parentheses
- decimal numbers

Example:

```text
23 * 45
```

Unsupported syntax includes exponentiation and unary operators such as:

```text
2 ** 8
-5 + 3
1--2
```

Invalid expressions and division by zero are returned as tool failures rather than crashing the application.

## Requirements

- Java 21
- Maven 3.9+
- Ollama only when using the local Ollama provider

## Optional Ollama Setup

Install Ollama from:

https://ollama.com/download

Download a model:

```bash
ollama pull qwen3:4b
```

Start the Ollama server:

```bash
ollama serve
```

The default Ollama endpoint is:

```text
http://localhost:11434
```

## Configuration

Configuration is loaded from:

```text
src/main/resources/application.properties
```

Example:

```properties
app.interface=console
server.port=8080

llm.provider=ollama
llm.temperature=0.4
llm.max-output-tokens=1000
llm.system-message=You are a helpful AI assistant. Be concise and clear. If you are uncertain, state it directly.

ollama.model=qwen3:4b
ollama.enabled=true
ollama.base-url=http://localhost:11434
ollama.context-window=4096
ollama.repeat-penalty=1.18

openai.model=gpt-5.4-mini
openai.base-url=https://api.openai.com/v1
openai.api-key-env=OPENAI_API_KEY

groq.model=openai/gpt-oss-20b
groq.base-url=https://api.groq.com/openai/v1
groq.api-key-env=GROQ_API_KEY

gemini.model=gemini-3.7-flash
gemini.base-url=https://generativelanguage.googleapis.com/v1beta
gemini.api-key-env=GEMINI_API_KEY

conversation.file=conversation.json
```

Invalid or missing configuration results in a `ConfigurationException`.

`app.interface` selects `console` or `http`. HTTP mode exposes `GET /api/health` and uses
`server.port` locally. The `APP_INTERFACE` and `PORT` environment variables override these values,
which allows the same configuration and artifact to run on Cloud Run.

## HTTP API

HTTP mode currently exposes:

```text
GET /api/health
GET /api/llm/providers
GET /openapi.yaml
```

The OpenAPI-first contract is maintained in `src/main/resources/openapi.yaml`. The application
serves the same document through `/openapi.yaml`, and automated tests validate its structure.

### Bruno collection

The `bruno` directory contains requests and tests for the currently implemented HTTP API. Open
this directory as a collection in Bruno and select the `Local` environment. Start the application
in HTTP mode before sending the requests.

The collection can also be executed with the Bruno CLI:

```shell
bru run bruno --env Local
```

The local environment uses `http://localhost:8080` by default. Change `baseUrl` in
`bruno/environments/Local.bru` when the server runs on another port.

All configured Ollama generation options are forwarded inside the provider's `options` object.

`ollama.enabled` controls whether Ollama is available in the current deployment. The
`OLLAMA_ENABLED` environment variable overrides the property. Local runs default to enabled;
Cloud Run must set `OLLAMA_ENABLED=false`.

`llm.provider` selects the startup provider. Each provider has its own model. To use or switch to OpenAI, provide the API key through the environment variable named by `openai.api-key-env`. Never store API keys in the properties file. The OpenAI client is created lazily, so a missing key does not prevent startup with Ollama.

For local development, copy `.env.example` to `.env` and add the key:

```dotenv
OPENAI_API_KEY=your-api-key
GROQ_API_KEY=your-api-key
GEMINI_API_KEY=your-api-key
```

The `.env` file is ignored by Git and must never be committed. A value already defined in the system environment takes precedence over `.env`.

For the current PowerShell session, set the key before starting the application:

```powershell
$env:OPENAI_API_KEY = "your-api-key"
```

To store it for the current Windows user, run the following once and then open a new terminal:

```powershell
[Environment]::SetEnvironmentVariable("OPENAI_API_KEY", "your-api-key", "User")
```

## Console Commands

| Command | Description |
|---|---|
| `/help` | Show available commands |
| `/new` | Start a new conversation |
| `/history` | Show conversation history |
| `/llm OLLAMA` | Switch subsequent requests to Ollama |
| `/llm OPENAI` | Switch subsequent requests to OpenAI |
| `/llm GROQ` | Switch subsequent requests to GroqCloud |
| `/llm GEMINI` | Switch subsequent requests to Gemini API |
| `/llm STATUS` | Show the active provider |
| `/thinking ON` | Show streamed reasoning |
| `/thinking MINIMAL` | Show up to the first 200 reasoning characters |
| `/thinking OFF` | Hide reasoning |
| `/thinking STATUS` | Show the current thinking mode |
| `/exit` | Exit the application |

The default thinking mode is `MINIMAL`.

Changing the provider does not clear or rewrite the conversation. The next provider receives the existing provider-independent history. If switching fails, the previous provider remains active.

## Response Summary

Each successful response includes:

```text
AI Response Summary
Model:             qwen3:4b
Prompt tokens:     120
Completion tokens: 45
Total tokens:      165
Duration:          00:03.421
Response:          Example response
```

Duration is stored as `durationMs` and displayed as `mm:ss.xxx`.

For tool calls, token usage is aggregated across both LLM requests.

## Conversation Persistence

Conversation history is stored in the file configured by:

```properties
conversation.file=conversation.json
```

Provider-specific response objects are not persisted. The stored conversation uses provider-independent domain models.

The `/new` command starts a new in-memory conversation. The next successful response replaces the persisted conversation with the new one.

## Build and Verification

Compile the project:

```bash
mvn compile
```

Create and verify the JAR:

```bash
mvn verify
```

Run all tests:

```bash
mvn test
```

Run the complete verification lifecycle:

```bash
mvn verify
```

Format Java sources:

```bash
mvn spotless:apply
```

Check formatting without modifying files:

```bash
mvn spotless:check
```

## Testing

The project uses:

- JUnit 6
- Mockito

External HTTP communication is mocked in unit tests. Tests do not call a real Ollama server.

Covered areas include:

- configuration loading and validation
- prompt composition
- Ollama response parsing and streaming
- agent decisions and tool execution
- token aggregation
- conversation persistence
- console thinking modes and response summary

## Current Limitations

- Ollama, OpenAI, GroqCloud, and Gemini API are the implemented LLM providers.
- Only the calculator tool is available.
- A tool request requires two LLM calls.
- The agent supports a single tool execution before the final response.
- There is no retry or timeout recovery strategy beyond HTTP error handling.
- The application currently exposes only a console interface.
- Conversation history is stored in a local JSON file.

## Roadmap

MVP – Public demo target (two weeks)
✓ Define MVP scope, limits, and acceptance criteria in PRD
→ Add local and cloud runtime configuration
✓ Add HTTP mode and health endpoint
✓ Add configurable Ollama availability
✓ Add OpenAPI specification foundation
✓ Add LLM provider availability endpoint
→ Add stateless chat REST API
→ Add SSE chat streaming and REST tests
✓ Add Bruno API collection foundation
→ Add simple web chat with browser-local history
→ Add provider-independent embedding and vector store abstractions
→ Add TXT and Markdown ingestion, chunking, and retrieval
→ Add RAG context assembly and source attribution
→ Add Firestore-backed demo quotas and LLM limits
→ Add public aggregate metrics endpoint and status page
→ Add Dockerfile and Docker Compose with optional Ollama
→ Add end-to-end and deployment smoke tests
→ Deploy the Docker image to Google Cloud Run
→ Complete MVP documentation and release verification

Phase 1 – Core LLM application
✓ LLM client abstraction
✓ Ollama integration
✓ Native Java HTTP transport
✓ Configuration loading
✓ Configuration validation
✓ Application-specific exceptions
✓ Provider-independent models
✓ Ollama DTO isolation
✓ Prompt model
✓ Prompt composition
✓ Prompt templates
✓ Streaming responses
✓ Streaming thinking output
✓ Token usage extraction
✓ Request and response logging
✓ LLM client tests
✓ Ollama client tests
✓ Configuration loading tests
✓ Configuration validation tests
✓ Prompt composition tests
✓ Prompt template tests
✓ Forward all configured generation options to Ollama
✓ Additional LLM providers
✓ Provider selection through configuration
→ Ollama integration tests

Phase 2 – Conversation & observability
✓ Conversation model
✓ Conversation memory
✓ Conversation persistence
✓ New conversation command
✓ Conversation history command
✓ Prompt token reporting
✓ Completion token reporting
✓ Total token reporting
✓ Response duration
✓ Aggregated token usage for tool calls
✓ Request identifiers in logs
✓ Configurable thinking output
✓ Minimal thinking preview
✓ Conversation model tests
✓ Conversation persistence tests
✓ Console command tests
✓ Thinking output regression tests
✓ Response summary regression tests
→ Conversation history limits
→ Context-window usage tracking
→ Automatic conversation trimming
→ Prompt + response logging controls
→ Sensitive-data filtering
→ Metrics
→ Structured logging improvements
→ LLM performance metrics
→ Langfuse integration
→ Tracing
→ Token usage analytics
→ Latency + cost dashboards
✓ End-to-end console tests

Phase 3 – Agent
✓ Agent abstraction
✓ Agent event model
✓ Agent LLM gateway
✓ Structured agent decisions
✓ Tool abstraction
✓ Tool description formatting
✓ Tool calling
✓ Tool call events
✓ Tool result events
✓ Calculator tool
✓ Tool result conversation history
✓ Final response after tool execution
✓ Token aggregation across agent steps
✓ Agent gateway tests
✓ Agent decision tests
✓ Tool calling tests
✓ Calculator tool tests
✓ Token aggregation tests
→ Configurable tool registry
→ Multiple tool calls per request
→ Bounded multi-step agent loop
→ Maximum agent-step configuration
→ Tool execution timeout
→ Tool authorization policies
→ Tool input schema validation
→ Parallel tool execution
→ Agent state
→ Agent execution trace
✓ One repair attempt for invalid agent output
→ Repeated tool-call recovery

Phase 4 – Data & RAG
→ Document ingestion
→ Document format detection
→ Document parsing
→ Text normalization
→ Chunking
→ Chunk metadata
→ Embedding client abstraction
→ Ollama embedding integration
→ Vector store abstraction
→ Local vector store implementation
→ Semantic retrieval
→ Metadata filtering
→ Retrieval ranking
→ Context assembly
→ RAG prompt templates
→ Citation generation
→ Source attribution
→ Retrieval diagnostics
→ Document ingestion tests
→ Document parsing tests
→ Chunking tests
→ Embedding client tests
→ Vector store tests
→ Retrieval tests
→ RAG pipeline tests
→ Citation generation tests
→ n8n integration
→ Ingestion workflows
→ Scheduled crawlers
→ Preprocessing pipelines
→ External API enrichment

Phase 5 – Evaluation
✓ Google Java Format enforcement
✓ Maven verification lifecycle
→ Evaluation dataset
→ Automated evaluation
→ Tool selection evaluation
→ Tool result correctness
→ Retrieval relevance
→ Answer correctness
→ Citation coverage
→ Confidence scoring
→ Prompt regression evaluation
→ Model comparison benchmarks
→ Performance regression evaluation
→ SonarQube quality gate

Phase 6 – Production engineering
✓ HTTP connection timeout
✓ Graceful HTTP client shutdown
✓ Interrupted request handling
✓ Communication exception mapping
✓ Invalid configuration handling
✓ Invalid provider response handling
✓ Application startup error tests
✓ Communication error tests
→ Request timeout configuration
→ Retry policy
→ Exponential backoff
→ Circuit breaker
→ Rate limiting
→ Request cancellation
→ Concurrency
→ Thread-safety review
→ Context-window management
→ Response caching
→ Tool result caching
→ Performance benchmarks
→ Load testing
→ Resilience tests
→ Concurrency tests
→ Performance tests
→ Health checks
→ Readiness checks
→ Metrics endpoint
→ Secure secret management
→ Log redaction
→ Configuration profiles

Phase 7 – Integration & deployment
→ REST API
→ Streaming REST endpoint
✓ OpenAPI specification foundation
→ Interactive API documentation
→ REST API tests
→ Web user interface
→ Docker
→ Docker Compose
→ Ollama container integration
→ GitHub Actions CI
→ Automated tests
→ Formatting verification
→ SonarQube analysis
→ Dependency vulnerability scanning
→ Container vulnerability scanning
→ Integration tests
→ Deployment smoke tests
→ Release packaging
→ Versioned releases
→ Deployment documentation
→ Production deployment

## License

This project is intended for educational purposes.
