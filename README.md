# AI Demo

AI Demo is a Java 21 application demonstrating provider-independent LLM integration through console, REST, SSE, and a responsive web interface.

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
- Production Preact frontend served by the Java application
- Multi-stage Docker image for Google Cloud Run
- Configurable public-demo request, input, history, output-token, and stream limits
- Transactional Firestore request and aggregate token counters

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

`LLM_PROVIDER`, `APP_INTERFACE`, `PORT`, `OLLAMA_ENABLED`, `OLLAMA_MODEL`, and `OLLAMA_BASE_URL`
override their corresponding
properties. Cloud deployments should select `OPENAI`, `GROQ`, or `GEMINI`; API keys are read only
from the provider's configured environment variable. Do not put API keys into properties, images,
deployment environment files, or source control.

### Public demo limits

Public limits are disabled for local development by default. Cloud Run must enable both limit and
Firestore enforcement. Supported environment overrides are:

```text
DEMO_LIMITS_ENABLED=true
DEMO_LIMITS_FIRESTORE_ENABLED=true
DEMO_LIMITS_DAILY_REQUESTS=200
DEMO_LIMITS_HOURLY_REQUESTS_PER_IP=20
DEMO_LIMITS_CONCURRENT_STREAMS=5
DEMO_LIMITS_MAX_INPUT_CHARACTERS=20000
DEMO_LIMITS_MAX_HISTORY_MESSAGES=10
DEMO_LIMITS_MAX_OUTPUT_TOKENS_PER_CALL=1000
GOOGLE_CLOUD_PROJECT=your-project-id
FIRESTORE_DATABASE_ID=(default)
```

Client addresses are salted and SHA-256 hashed before being used as Firestore document keys. Set
`DEMO_IP_HASH_SALT` from Secret Manager. Raw addresses, prompts, responses, and conversation
history are not stored. Firestore transactions reserve daily and hourly request quota before an LLM
call and reconcile aggregate token usage afterward. Because the deployment is bounded to one Cloud
Run instance, active SSE concurrency is enforced in that process. If Firestore is unavailable,
requests fail closed with HTTP `503`.

## HTTP API

HTTP mode currently exposes:

```text
GET /api/health
GET /api/llm/providers
POST /api/chat
POST /api/chat/stream
GET /openapi.yaml
```

The OpenAPI-first contract is maintained in `src/main/resources/openapi.yaml`. The application
serves the same document through `/openapi.yaml`, and automated tests validate its structure.

`POST /api/chat` is stateless. Its request contains the complete conversation as `USER` and
`ASSISTANT` messages, ending with a `USER` message. The optional `provider` field selects an
available LLM provider; when omitted, the configured `llm.provider` is used. The server does not
save HTTP conversation history.

`POST /api/chat/stream` accepts the same request and returns `text/event-stream`. It emits typed
`thinking`, `tool`, `content`, `completion`, and terminal `error` events. Tool events contain only a
safe tool name and lifecycle status, without the tool input or output. Because the request uses POST,
browser clients consume it with `fetch()` and a `ReadableStream` rather than `EventSource`.
Thinking can arrive incrementally; the current structured agent emits final answer content after
its decision has been validated.

Non-success responses use a stable `code` and safe English `message`. Request validation errors
also include optional `details` entries with `field` and `message` so the caller can correct its
input; provider and internal failures do not expose their underlying details.

### Bruno collection

The `bruno` directory contains requests and tests for the currently implemented HTTP API. Open
this directory as a collection in Bruno and select the `Local` environment. Start the application
in HTTP mode before sending the requests.

The collection can also be executed with the Bruno CLI:

```shell
bru run bruno --env Local
```

The local environment uses `http://localhost:8080` and `OLLAMA` by default. Change `baseUrl` or
`llmProvider` in `bruno/environments/Local.bru` when using another port or provider.

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

For frontend development, start the Vite server from the project root (the HTTP backend must already be
running on port `8080`):

```powershell
.\scripts\start-frontend.ps1
```

On its first run, the script installs the frontend dependencies. Vite is normally available at
`http://localhost:5173` and proxies API calls to the backend.

IntelliJ IDEA also discovers the shared `AI Demo Frontend` run configuration from the `.run`
directory. It starts Vite through `npm run dev` after the frontend dependencies have been installed.

Compile the project:

```bash
mvn compile
```

Build the production frontend before packaging locally:

```powershell
npm ci --prefix frontend
npm test --prefix frontend
npm run build --prefix frontend
mvn verify
```

Maven copies `frontend/dist` into the application classpath. The executable JAR then serves the web
client from `/` and the API from `/api`, so production needs only one process and one origin.

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
- Uploaded RAG documents, the public status page, and persistent RAG storage are not implemented.
- Conversation history is stored in a local JSON file.

## Docker and Google Cloud Run

The multi-stage `Dockerfile` builds the Preact client, packages it into a shaded Java JAR, and runs
the result as a non-root user on the `PORT` supplied by Cloud Run.

Build and test the image locally:

```powershell
docker build -t ai-demo:local .
docker run --rm -p 8080:8080 `
  -e LLM_PROVIDER=OPENAI `
  -e OPENAI_API_KEY=$env:OPENAI_API_KEY `
  ai-demo:local
```

### Run the published application with local Ollama

Docker Compose starts the published application image, an Ollama server, and a one-time model
download. The model is stored in the named `ollama-data` volume and is reused after restarts.

```powershell
docker compose --profile ollama pull
docker compose --profile ollama up -d
```

The first start takes longer while Ollama downloads `qwen3:4b`. Follow its progress with:

```powershell
docker compose logs -f ollama-model
```

After the download completes, open `http://localhost:8080`. Stop the containers without deleting
the downloaded model using:

```powershell
docker compose --profile ollama down
```

To select another model or host port, copy `.env.example` to `.env` and change `OLLAMA_MODEL` or
`AI_DEMO_PORT`. Removing the model data is an explicit destructive operation:

```powershell
docker compose --profile ollama down --volumes
```

To build the application image from the current checkout instead of downloading it, run:

```powershell
docker compose --profile ollama up -d --build
```

The `Publish container image` GitHub Actions workflow publishes `latest`, Git tag, and commit-SHA
tags to GitHub Container Registry. The package must be public for unauthenticated `docker compose
pull`; otherwise users must authenticate with `docker login ghcr.io` first.

For Cloud Run:

1. Create a Firestore Native-mode database in the same European region as Cloud Run.
2. Grant the Cloud Run service account `roles/datastore.user` and Secret Manager access only to
   the selected provider key and `demo-ip-hash-salt`.
3. Create a provider-key secret and a long random `demo-ip-hash-salt` secret in Secret Manager.
4. Copy `deploy/cloudrun.env.yaml.example` to the ignored `deploy/cloudrun.env.yaml` file.
5. Authenticate `gcloud`, then run:

```powershell
.\deploy\deploy-cloud-run.ps1 `
  -ProjectId YOUR_PROJECT_ID `
  -Region europe-west1 `
  -Provider OPENAI `
  -ApiKeySecret openai-api-key
```

The deployment uses request-based billing, zero minimum instances, one maximum instance, bounded
concurrency, and Secret Manager environment mounts. The script runs read-only smoke checks against
the deployed health endpoint, provider list, frontend, and OpenAPI document. It deliberately does
not send a paid chat request. Run the smoke checks independently with:

```powershell
.\deploy\smoke-test.ps1 -BaseUrl https://YOUR_SERVICE_URL
```

After verifying the generated `run.app` URL, point an `ai-demo` subdomain at Cloud Run through the
recommended Google Cloud external Application Load Balancer. The separate portfolio root domain
can continue to use GitHub Pages or any other hosting.

## Roadmap

MVP – Public demo target (two weeks)
✓ Define MVP scope, limits, and acceptance criteria in PRD
→ Add local and cloud runtime configuration
✓ Add HTTP mode and health endpoint
✓ Add configurable Ollama availability
✓ Add OpenAPI specification foundation
✓ Add LLM provider availability endpoint
✓ Add stateless chat REST API
✓ Add SSE chat streaming and REST tests
✓ Add Bruno API collection foundation
✓ Add simple web chat with browser-local history
✓ Serve the production frontend build from the HTTP application
→ Add provider-independent embedding and vector store abstractions
→ Add TXT and Markdown ingestion, chunking, and retrieval
→ Add RAG context assembly and source attribution
✓ Add Firestore-backed demo request quotas and LLM limits
→ Add public aggregate metrics endpoint and status page
✓ Add production Dockerfile
✓ Add Docker Compose with optional Ollama
✓ Add deployment smoke tests
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
