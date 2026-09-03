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

### Application status and usage metrics

Open `/#/status` (the **Status** navigation link) or request `GET /api/app/status` for
today's aggregate usage in UTC: accepted chat requests, reconciled tokens, completed/failed/
disconnected outcomes, average completed-request duration, and daily quota remaining. The page
also shows active streams and uptime for the current server instance. Use Refresh to update;
storage reads are cached for up to ten seconds.

Collection uses `demo.limits.enabled=true`. With limits disabled the page explicitly says tracking
is disabled; zeros are not measured usage. With `demo.limits.firestore.enabled=true`, daily
counters are retained in `demoDailyUsage`, and per-client quota counters use `demoHourlyClients`.
In-memory development counters reset on restart.

Requests count quota admissions, including requests that subsequently fail; rejected requests are
excluded. Outcomes are recorded on a best-effort basis from this release onward, so old requests,
in-flight requests, process crashes and failed metric writes may leave totals unmatched. Token
totals may omit usage on failed or interrupted requests and should not be treated as billing data.
Requests crossing midnight belong to their admission day. This version does not count page views
or unique visitors and stores no message content or new visitor identifiers. A storage read failure
returns `503 APP_METRICS_UNAVAILABLE`; the page provides a retry action.

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
- Calculator results can be returned directly; deterministic routing needs no LLM call, while
  model-based routing needs a decision call (and may retry or repair the decision).
- The agent supports a single tool execution before the final response.
- There is no retry or timeout recovery strategy beyond HTTP error handling.
- RAG ingestion, retrieval, and document endpoints are not implemented. The public status page is
  implemented; daily metric collection requires demo limits to be enabled.
- Console history is stored in a local JSON file; web history stays in the browser.
- Per-client request limits currently use fixed UTC hours, not the rolling one-hour window required
  by PRD. Completing that requirement remains on the roadmap.

## Docker and Google Cloud Run

The multi-stage `Dockerfile` builds the Preact client, packages it into a Java JAR with runtime dependencies in `lib/`, and runs
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

Reviewed against this checkout on 2026-09-03. Checked items have an implementation in the repository;
they do not certify a successful cloud deployment or CI run. Unchecked items are still pending,
including the remaining scope explicitly named on partially implemented features. Later phases
include ideas beyond the current MVP, not additional MVP commitments.

Evidence: `src/main/java`, `src/test/java`, `frontend/src`, `Dockerfile`, `compose.yaml`,
`.github/workflows/publish-container.yaml`, and `deploy/`. Cloud deployment status has not been
verified in this review.

### MVP – Public demo target

- [x] Define MVP scope, limits, and acceptance criteria in PRD
- [x] Add console/HTTP, provider availability, port, and demo-limit runtime configuration
- [ ] Add RAG enablement and document-upload limit configuration
- [x] Add HTTP mode and health endpoint
- [x] Add configurable Ollama availability
- [x] Add OpenAPI specification foundation
- [x] Add LLM provider availability endpoint
- [x] Add stateless chat REST API
- [x] Add SSE chat streaming and REST tests
- [x] Add Bruno API collection foundation
- [x] Add simple web chat with browser-local history
- [x] Serve the production frontend build from the HTTP application
- [ ] Add provider-independent embedding and vector store abstractions
- [ ] Add TXT and Markdown ingestion, chunking, and retrieval
- [ ] Add RAG context assembly and source attribution
- [x] Add Firestore-backed demo request quotas and LLM limits
- [x] Add public aggregate metrics endpoint and status page
- [x] Add production Dockerfile
- [x] Add Docker Compose with optional Ollama
- [x] Add deployment smoke tests
- [x] Add Cloud Run deployment script and environment example
- [ ] Verify deployment of the current Docker image to Google Cloud Run
- [ ] Complete MVP documentation and release verification

### Phase 1 – Core LLM application

- [x] LLM client abstraction
- [x] Ollama integration
- [x] Native Java HTTP transport
- [x] Configuration loading
- [x] Configuration validation
- [x] Application-specific exceptions
- [x] Provider-independent models
- [x] Ollama DTO isolation
- [x] Prompt model
- [x] Prompt composition
- [x] Prompt templates
- [x] Streaming responses
- [x] Streaming thinking output
- [x] Token usage extraction
- [x] Request/response metadata logging (not conversation content)
- [x] LLM client tests
- [x] Ollama client tests
- [x] Configuration loading tests
- [x] Configuration validation tests
- [x] Prompt composition tests
- [x] Prompt template tests
- [x] Forward all configured generation options to Ollama
- [x] Additional LLM providers
- [x] Provider selection through configuration
- [ ] Integration tests against a real local Ollama server (current client tests mock HTTP transport)

### Phase 2 – Conversation & observability

- [x] Conversation model
- [x] Conversation memory
- [x] Conversation persistence
- [x] New conversation command
- [x] Conversation history command
- [x] Prompt token reporting
- [x] Completion token reporting
- [x] Total token reporting
- [x] Response duration
- [x] Aggregated token usage for tool calls
- [x] Request identifiers in logs
- [x] Configurable thinking output
- [x] Minimal thinking preview
- [x] Conversation model tests
- [x] Conversation persistence tests
- [x] Console command tests
- [x] Thinking output regression tests
- [x] Response summary regression tests
- [x] Web request history limited to ten messages; configurable server bound when demo limits are enabled
- [ ] Console history limits and browser history retention limits
- [ ] Context-window usage tracking
- [ ] Automatic conversation trimming
- [ ] Prompt + response logging controls
- [ ] Sensitive-data filtering
- [x] Daily aggregate demo metrics and public Status page
- [ ] Structured logging improvements
- [x] Average completed-request duration in demo metrics
- [ ] Per-provider performance metrics, latency percentiles, and time to first token
- [ ] Langfuse integration
- [ ] Tracing
- [x] Daily aggregate reconciled token usage
- [ ] Historical and per-provider token usage analytics
- [ ] Latency + cost dashboards
- [x] End-to-end console tests

### Phase 3 – Agent

- [x] Agent abstraction
- [x] Agent event model
- [x] Agent LLM gateway
- [x] Structured agent decisions
- [x] Tool abstraction
- [x] Tool description formatting
- [x] Tool calling
- [x] Tool call events
- [x] Tool result events
- [x] Calculator tool
- [x] Tool result conversation history
- [x] Final response after tool execution
- [x] Direct final calculator result without a follow-up LLM call
- [x] Deterministic localized arithmetic routing
- [x] Token aggregation across agent steps
- [x] Agent gateway tests
- [x] Agent decision tests
- [x] Tool calling tests
- [x] Calculator tool tests
- [x] Token aggregation tests
- [ ] Configurable tool registry
- [ ] Multiple tool calls per request
- [ ] Bounded multi-step agent loop
- [ ] Maximum agent-step configuration
- [ ] Tool execution timeout
- [ ] Tool authorization policies
- [x] Calculator input validation
- [ ] General tool input schema validation
- [ ] Parallel tool execution
- [ ] Agent state
- [ ] Agent execution trace
- [x] One repair attempt for invalid agent output
- [x] One decision retry when reasoning exhausts the output budget
- [ ] Repeated tool-call recovery

### Phase 4 – Data & RAG

- [ ] Document ingestion
- [ ] Document format detection
- [ ] Document parsing
- [ ] Text normalization
- [ ] Chunking
- [ ] Chunk metadata
- [ ] Embedding client abstraction
- [ ] Ollama embedding integration
- [ ] Vector store abstraction
- [ ] Local vector store implementation
- [ ] Semantic retrieval
- [ ] Metadata filtering
- [ ] Retrieval ranking
- [ ] Context assembly
- [ ] RAG prompt templates
- [ ] Citation generation
- [ ] Source attribution
- [ ] Retrieval diagnostics
- [ ] Document ingestion tests
- [ ] Document parsing tests
- [ ] Chunking tests
- [ ] Embedding client tests
- [ ] Vector store tests
- [ ] Retrieval tests
- [ ] RAG pipeline tests
- [ ] Citation generation tests
- [ ] n8n integration
- [ ] Ingestion workflows
- [ ] Scheduled crawlers
- [ ] Preprocessing pipelines
- [ ] External API enrichment

### Phase 5 – Evaluation

- [x] Google Java Format enforcement
- [x] Maven verification lifecycle
- [ ] Evaluation dataset
- [ ] Automated evaluation
- [ ] Tool selection evaluation
- [ ] Tool result correctness
- [ ] Retrieval relevance
- [ ] Answer correctness
- [ ] Citation coverage
- [ ] Confidence scoring
- [ ] Prompt regression evaluation
- [ ] Model comparison benchmarks
- [ ] Performance regression evaluation
- [ ] SonarQube quality gate

### Phase 6 – Production engineering

- [x] HTTP connection timeout
- [x] Graceful HTTP client shutdown
- [x] Interrupted request handling
- [x] Communication exception mapping
- [x] Invalid configuration handling
- [x] Invalid provider response handling
- [x] Application startup error tests
- [x] Communication error tests
- [ ] Configurable application/provider request deadlines (Cloud Run script sets a 300-second timeout)
- [ ] Retry policy
- [ ] Exponential backoff
- [ ] Circuit breaker
- [x] Daily request quota and fixed-hour per-client quota
- [ ] Rolling one-hour per-client quota required by PRD
- [x] Browser Stop action aborts the stream and discards the incomplete turn
- [ ] End-to-end provider request cancellation
- [x] Current-instance concurrent-stream limit
- [ ] Distributed concurrency control
- [ ] Thread-safety review
- [ ] Context-window management
- [ ] Response caching
- [ ] Tool result caching
- [ ] Performance benchmarks
- [ ] Load testing
- [x] Isolated provider-error and quota-storage failure tests
- [ ] Extended resilience and recovery tests
- [ ] Parallel quota-reservation and concurrent-stream stress tests
- [ ] Performance tests
- [x] HTTP liveness endpoint at `/api/health`
- [ ] Readiness checks
- [x] Public aggregate metrics endpoint at `/api/app/status`
- [x] Environment/.env credential loading and Secret Manager references in the deployment script
- [ ] Verify deployed secret access policies and rotation
- [ ] Log redaction
- [x] External properties file and environment overrides for local/cloud execution
- [ ] Named configuration profiles

### Phase 7 – Integration & deployment

- [x] REST API for health, providers, chat, and application status
- [ ] RAG document REST endpoints
- [x] Streaming REST endpoint
- [x] OpenAPI specification foundation
- [ ] Interactive API documentation
- [x] REST and SSE tests with embedded server and mocked application services
- [x] Web chat, Q&A, Status, and shared project/author links
- [x] Multi-stage Docker build
- [x] Docker Compose
- [x] Optional Ollama container and model-download service
- [x] GitHub Actions container build/publish workflow
- [x] Automated Java and frontend test suites runnable locally
- [x] Formatting verification in `mvn verify`
- [ ] CI test and formatting gates (the publish workflow uses a Docker build that skips tests)
- [ ] SonarQube analysis
- [ ] Dependency vulnerability scanning
- [ ] Container vulnerability scanning
- [x] Embedded HTTP and console integration tests with mocked providers
- [ ] Live provider, Firestore emulator, and container integration tests
- [x] Deployment smoke script for health, provider availability, frontend, and OpenAPI
- [ ] Extend deployment smoke checks to application status and quota behavior
- [x] JAR/runtime dependency and container packaging
- [ ] Versioned releases (tag-triggered image publishing is configured)
- [x] Local Docker and Cloud Run deployment instructions
- [ ] Verify current production deployment

## License

This project is intended for educational purposes.
