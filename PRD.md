# AI Demo – Product Requirements

## Product summary

AI Demo is an educational Java 21 LLM application that demonstrates provider-independent AI integration without hiding its core behavior behind an AI framework. It provides a console interface for local experimentation and a stateless REST and web interface for a public multi-user demo.

The application supports streaming responses, agent-based tool calling, retrieval-augmented generation (RAG), configurable thinking output, Docker-based local execution, and deployment to Google Cloud Run. Ollama is available only when explicitly enabled for local use. OpenAI, GroqCloud, and Gemini API can be used locally and in the cloud.

## Target users

- Java developers learning how LLM applications work internally
- Developers evaluating clean architecture for LLM, agent, and RAG integrations
- Trainers and developers experimenting with local Ollama models
- Visitors trying the public web demo without creating an account

## Product goals

- Keep the complete request, streaming, agent, tool, and retrieval flow understandable
- Demonstrate provider-independent LLM and embedding integration
- Support local Ollama experimentation and cloud API providers
- Provide a small but complete MVP through console, REST, and web interfaces
- Include realistic configuration, observability, quota protection, error handling, and tests
- Package the application as a Docker image that runs locally and on Cloud Run
- Remain small enough to serve as a reference implementation

## Product principles

- Educational readability before framework-like generalization
- Provider independence outside client implementations
- Explicit data flow and clear layer responsibilities
- Stateless cloud requests unless persistence is explicitly required
- Deterministic tests without real external services
- Test-driven development for new behavior and bug fixes where practical
- Simple designs before additional abstractions
- New behavior includes tests and relevant documentation
- Public demo cost and abuse protection takes precedence over availability

## MVP scope

### Existing capabilities

- Interactive console chat
- Ollama, OpenAI, GroqCloud, and Gemini API integrations
- Provider selection through configuration
- Runtime provider switching without losing console conversation history
- Console conversation history persisted in a local JSON file
- Streaming content and thinking output
- Thinking modes `ON`, `OFF`, `MINIMAL`, and `STATUS`
- Agent decision between a direct reply and a registered tool
- Calculator tool with a single tool execution before the final reply
- Aggregated token usage across agent steps
- Response summary with model, token usage, duration, and complete answer
- Maven verification and Google Java Format

### MVP additions

- Stateless REST API
- Server-Sent Events (SSE) chat streaming endpoint
- Simple responsive web chat interface
- Browser-local conversation history
- Bruno collection covering the public REST API
- Multiple anonymous users without authentication
- RAG over a bounded collection of `.txt` and `.md` documents
- Source attribution in RAG answers
- Dockerfile for a production application image
- Docker Compose for local application and optional Ollama execution
- Deployment to Google Cloud Run
- Separate local and cloud capabilities through configuration
- Firestore-backed global usage counters for the cloud deployment
- Configurable request, concurrency, input, retrieval, and LLM token limits
- Public demo metrics/status page containing non-sensitive aggregate metrics

## Runtime modes and provider availability

The application uses one codebase and one application image. Capabilities are controlled independently through configuration rather than separate source variants.

Required runtime configuration includes:

```properties
app.interface=console|http
ollama.enabled=true|false
rag.enabled=true|false
demo.limits.enabled=true|false
```

Local execution may enable Ollama in either console or HTTP mode. The Cloud Run deployment must set `app.interface=http`, disable Ollama, and enable demo limits.

`GET /api/llm/providers` exposes only providers available in the current deployment. A request naming a disabled or unconfigured provider is rejected with HTTP `400` and:

```json
{
  "code": "LLM_PROVIDER_UNAVAILABLE",
  "message": "The selected LLM provider is not available."
}
```

The cloud web interface must not display Ollama.

## Functional requirements

### Chat and streaming

- A user can submit a question through the console or REST API.
- The REST streaming endpoint uses SSE.
- Content and thinking chunks are processed in their received order.
- Empty provider chunks are ignored.
- The server sends typed SSE events named `content`, `thinking`, `tool`, `completion`, and `error`.
- Tool events expose the tool name and lifecycle status without exposing tool input or output.
- A successful console answer is added to the local conversation and persisted.
- The cloud server does not persist conversation history.
- Provider failures produce an application-specific LLM exception and a safe API error that
  identifies the selected provider and suggests trying another provider or trying again later.

### Stateless web conversations

- The browser keeps its conversation in memory and may mirror it to `localStorage`.
- Each chat request includes the bounded conversation context needed by the model.
- The backend does not assign persistent user identities.
- Refreshing or clearing browser storage must not affect other users.
- The browser sends at most the configured maximum number of history messages.
- Stopping a web stream discards its incomplete user turn so a later request receives only completed conversation context.
- Browser history loaded after an interrupted stream ignores trailing user messages without an assistant response.
- The web interface provides an English Q&A view at `/#/faq` covering the project purpose, local setup, and Docker/Ollama setup.
- The top navigation remains visible while scrolling and links to Chat, About, Q&A, Status, and the external health endpoint.
- The conversation uses separate header, scrollable message, and composer zones. The composer never covers the header or messages; on extremely short viewports, the panel extends into normal page scrolling.

### REST API

The MVP API includes at least:

```text
GET    /api/health
GET    /api/llm/providers
POST   /api/chat
POST   /api/chat/stream
GET    /api/rag/documents
POST   /api/rag/documents
DELETE /api/rag/documents/{id}
GET    /api/app/status
GET    /openapi.yaml
```

- REST models are provider independent.
- REST handlers call application services and must not call provider clients directly.
- JSON errors use a stable error code and a safe English message. Request validation errors include
  optional field-level `details` that help callers correct their input. Server and provider failures
  do not expose internal details; their technical causes are logged instead.
- Bruno files cover successful requests, validation failures, limit failures, RAG, and streaming where supported.
- `src/main/resources/openapi.yaml` is the authoritative REST API contract.
- The OpenAPI document is served publicly through `GET /openapi.yaml`.
- REST endpoints, tests, Bruno files, and documentation must remain consistent with the contract.
- Generated server code is not required for the MVP.

### Web interface

- The web interface supports sending a prompt and displaying streamed output.
- It displays the available provider choices returned by the backend.
- It can enable or disable RAG for a request.
- It shows source attribution when RAG sources are returned.
- It presents API and SSE errors in a readable form.
- It provides a small status page for non-sensitive aggregate demo metrics.
- It contains no secrets or provider API keys.
- Chat, Q&A, and Status share a footer linking to the source repository, the author's website,
  and the author's LinkedIn profile. External footer links open in a new tab with an accessible notice.

### Thinking output

- `ON` displays streamed thinking.
- `OFF` suppresses thinking.
- `MINIMAL` displays at most the first 200 thinking characters for every question.
- `MINIMAL` remains active until the user changes it.
- `STATUS` reports the active mode without changing it.

### Tool calling

- If no registered tool can resolve the request deterministically, the model returns a structured decision: either a direct reply or a tool call.
- Unambiguous numeric expressions and localized number-word expressions in Czech, English, German, Slovak, and Polish use the calculator without an LLM routing call; additional languages are routed by the LLM without requiring language-specific application code. The exact matching rules (accepted typo tolerance, accepted conjunction forms) are implementation detail defined in `AGENTS.md`.
- When the LLM recognizes arithmetic in another language or despite a spelling mistake, it normalizes the expression and calls the calculator immediately without discussing the language or performing the calculation itself.
- JSON wrapped in a Markdown code block is accepted.
- An invalid decision triggers at most one repair request.
- Only registered tools can be executed.
- The assistant tool decision and tool result are preserved in conversation order.
- Tools may declare their result final. Calculator requests return the formatted tool result directly after either deterministic or model-based routing, avoiding an unnecessary second LLM call.
- Token usage from all LLM calls is aggregated.
- Invalid tool input returns a tool failure instead of terminating the application.
- Successful calculator results use a compact equation with spaces around operators, for example `2 + 3 = 5`.
- If a decision stream exhausts its output on reasoning before producing content, the agent retries once with an explicit decision-only instruction.

### RAG

- The MVP accepts `.txt` and `.md` documents only.
- Documents are normalized and divided into bounded chunks with source metadata.
- Embedding access is defined through a provider-independent abstraction.
- Vector storage and semantic retrieval are defined through provider-independent abstractions.
- The initial vector index may be held in memory.
- A default shared document corpus may be built into the Docker image.
- Documents uploaded to a Cloud Run instance are temporary and may disappear after restart or scale-to-zero.
- Retrieval selects at most the configured number of chunks.
- Retrieved context is clearly separated from user instructions in the prompt.
- RAG answers include source attribution.
- PDF, Word, crawling, reranking, and persistent document storage are outside the MVP.
- Anonymous upload is bounded by `demo.limits.max-rag-upload-bytes` per file and `demo.limits.max-rag-documents` for the total corpus, and may be disabled in the public deployment.
- A request to a `/api/rag/documents` endpoint while `rag.enabled=false` is rejected with HTTP `400` and:

```json
{
  "code": "RAG_DISABLED",
  "message": "Retrieval-augmented generation is not enabled in this deployment."
}
```

### Conversation persistence

- Console conversation data is stored as provider-independent JSON.
- A missing or empty file creates a new conversation.
- Unknown JSON properties are ignored.
- Read and write failures produce `PersistenceException`.
- REST and web conversation history is not stored by the server in the MVP.

### Provider selection

- Ollama, OpenAI, GroqCloud, and Gemini API can be selected locally when enabled and configured.
- `/llm OLLAMA`, `/llm OPENAI`, `/llm GROQ`, `/llm GEMINI`, and `/llm STATUS` are supported in the console.
- Switching providers preserves the provider-independent console conversation.
- Alternate providers are initialized lazily.
- A failed switch leaves the previously active provider unchanged.
- Provider secrets can be supplied through the system environment or a local `.env` file.
- System environment values take precedence over `.env`, and `.env` is excluded from Git.
- Ollama is disabled in the Cloud Run deployment and requires no tunnel to a user's computer.

### Demo usage limits

The public deployment applies the following initial limits:

```properties
demo.limits.daily-requests=200
demo.limits.hourly-requests-per-ip=20
demo.limits.concurrent-streams=5
demo.limits.max-input-characters=20000
demo.limits.max-history-messages=10
demo.limits.max-rag-chunks=5
demo.limits.max-output-tokens-per-call=1000
demo.limits.max-rag-upload-bytes=200000
demo.limits.max-rag-documents=20
```

- `demo.limits.daily-requests` resets at `00:00 UTC`. `demo.limits.hourly-requests-per-ip` uses a rolling one-hour window per hashed client identifier.
- `demo.limits.concurrent-streams` bounds only concurrent in-flight `POST /api/chat/stream` requests. Non-streaming `POST /api/chat` requests are not subject to a concurrency limit, only to the daily and hourly request-rate limits.
- Limit values are configurable through application configuration and environment variables.
- Firestore stores only aggregate usage counters and hashed client identifiers required for rate limiting.
- Raw IP addresses, prompts, responses, conversation history, and provider secrets must not be stored in Firestore.
- The IP hash uses a server-side secret salt.
- A Firestore transaction checks and reserves quota before a paid LLM request begins.
- Token usage from every agent step is aggregated and reconciled against the reservation.
- A concurrency slot is released when a stream completes or fails.
- If Firestore quota enforcement is unavailable in cloud mode, paid LLM requests fail closed with HTTP `503` and:

```json
{
  "code": "DEMO_LIMIT_UNAVAILABLE",
  "message": "The public demo is temporarily unavailable."
}
```

- Local development may disable demo limits or use the Firestore emulator.

When a demo request-rate quota (daily, hourly, or concurrency) is reached before streaming starts, the API returns:

```http
HTTP/1.1 429 Too Many Requests
Content-Type: application/json
```

```json
{
  "code": "DEMO_LIMIT_EXCEEDED",
  "message": "A usage limit for this demo application has been reached. Please try again later."
}
```

- The same public code and English message are used for every demo request-rate quota rejection, regardless of which specific limit (daily, hourly, or concurrency) was reached.
- `Retry-After` is included when the retry time is known.
- Once an SSE response has started, its HTTP status cannot change. `DEMO_LIMIT_EXCEEDED` can therefore only be returned before streaming starts, as an HTTP `429`. A demo-quota failure discovered after streaming has started — for example a token-usage reconciliation failure — instead terminates the stream with an `error` event containing code `DEMO_LIMIT_UNAVAILABLE` and its public message.
- Provider-side failures, including a provider reporting its own quota or rate limit exhausted, are not distinguished from other provider communication failures: both produce `LLM_COMMUNICATION_ERROR` (HTTP `502` before streaming starts, or a terminal `error` event with the same code once streaming has started). The client cannot currently tell a provider quota failure apart from a provider outage.
- Internal logs may record the actual limit type but must not expose secrets, raw IP addresses, prompts, or responses.

### Application metrics and status

- `GET /api/app/status` exposes safe aggregate metrics for the current public demo period.
- The web status page may display requests used and remaining, aggregate token usage, active streams, configured public limits, provider availability, and service uptime.
- Metrics must not expose raw or hashed IP identifiers, prompts, responses, API keys, Firestore identifiers, or per-user activity.
- Metrics are read-only and may be cached briefly to avoid unnecessary Firestore reads.
- Operationally sensitive diagnostics remain available only in server logs or cloud monitoring.
- The first status page is available at `/#/status` and shows today's accepted chat requests,
  reconciled tokens, completed/failed/disconnected outcomes, average completed-request processing
  time, daily quota remaining, current-instance active streams, and uptime.
- Daily metrics are collected when demo limits are enabled. Disabled tracking must be shown as
  unavailable rather than measured zero usage. Firestore counters survive instance restarts;
  local in-memory counters reset on restart.
- Daily periods follow UTC and requests that cross midnight remain assigned to their admission day.
  Status storage reads are cached for up to ten seconds, with a fresh read on UTC date changes.
- Accepted requests include later failures; validation and quota rejections are excluded. Outcome
  counts begin with this feature and are best-effort: interrupted processes or telemetry storage
  failures may leave accepted requests without a recorded outcome. Telemetry failures must not
  change an already delivered chat result. Tokens may omit failed or disconnected requests whose
  usage was never returned/reconciled, and are not an exact billing total.
- Average duration covers completed requests only and is null when no samples exist. No visitor
  identities, page views, or unique visitor counts are collected by this first version.
- Metrics read failures return HTTP `503` with code `APP_METRICS_UNAVAILABLE` and message
  `Application metrics are temporarily unavailable.`

### Response summary

- The console summary displays the model name.
- Prompt, completion, and total tokens are displayed.
- Duration is stored as `durationMs` and formatted as `mm:ss.xxx`.
- The complete response is displayed.
- REST completion events include provider-independent usage and duration data required by the web interface.

## Packaging and deployment

- The application is packaged as a Docker image using a multi-stage build where practical.
- The container listens on the port supplied through the `PORT` environment variable and binds to `0.0.0.0`.
- The production image contains only runtime dependencies and required static resources.
- Docker Compose supports local server execution and an optional `ollama/ollama` service profile.
- Ollama models are downloaded separately and stored in a Docker volume.
- The cloud MVP is deployed to Google Cloud Run in a European region.
- The initial Cloud Run configuration uses request-based billing, minimum instances `0`, maximum instances `1`, bounded concurrency, and a bounded request timeout.
- Google Cloud budget alerts supplement but do not replace application quota enforcement.
- Cloud Run and Firestore are colocated where practical to minimize latency and network charges.

## Out of scope for the MVP

- Production authentication and authorization
- Server-side conversation history for web users
- User accounts and cross-device synchronization
- Production relational database
- Persistent user-uploaded RAG document storage
- PDF and Word ingestion
- Website crawling and scheduled ingestion
- External production vector database
- Distributed conversation persistence
- Unbounded autonomous agent loops
- Arbitrary code execution
- Multiple tool calls per agent request
- Local Ollama access from the cloud deployment
- Cloudflare Tunnel or another tunnel to a user's Ollama
- Guaranteed zero-cost operation
- Production SLA and high availability

## Constraints and known limitations

- Java 21 and Maven are required; Docker is required for container workflows.
- Ollama is required only when using the local Ollama provider.
- Adding a new LLM provider requires a new `LlmClient` implementation; there is no plugin or dynamic provider mechanism.
- Console conversation persistence uses a local file.
- Cloud Run filesystems and in-memory RAG indexes are disposable.
- Only the calculator tool is currently registered.
- Tool-based answers require multiple LLM calls and normally use more time and tokens.
- The agent supports one tool execution before the final response.
- Small local models may return invalid structured output.
- Localized calculator routing does not cover every language or every regional phrasing; the matching implementation is documented in `AGENTS.md`.
- Provider-specific generation settings differ and unsupported settings are not forwarded.
- Anonymous IP-based rate limiting is an abuse deterrent, not a reliable user identity system.
- A provider may report quota exhaustion after an SSE stream has already begun; this surfaces as the same terminal `LLM_COMMUNICATION_ERROR` event used for any other provider communication failure, since provider-reported quota exhaustion is not currently distinguished from other provider errors.

## Success criteria

- A developer can understand the main request flow from the package structure.
- A provider can be added through `LlmClient` without changing service or REST logic.
- A tool can be added without changing `ChatService`.
- An embedding or vector-store implementation can be replaced without changing REST handlers.
- Unit and integration tests run without live LLM providers or production Firestore.
- Console, REST, and web chat requests complete without leaking provider-specific models.
- Multiple anonymous web clients can stream independent responses without server-side conversation history.
- A RAG answer includes relevant source attribution.
- Configured demo limits prevent a new paid request before quota is consumed and return the documented error.
- The same Docker image runs locally and on Cloud Run with different configuration.
- The Cloud Run deployment exposes a working health endpoint, web UI, streaming API, and status page.
- `mvn verify` succeeds.
- README, PRD, Bruno files, tests, and implementation describe the same behavior.

## Definition of done

A product change is complete when:

1. It is consistent with the current scope or the approved scope change is documented.
2. Architecture, exceptions, compatibility, provider independence, and public cost protection were considered.
3. Automated tests cover the changed behavior where practical.
4. Existing tests continue to pass.
5. `mvn verify` succeeds.
6. README or PRD is updated when user-visible behavior, scope, limits, or constraints change.
7. Public endpoints do not leak secrets or sensitive operational information.

## Document responsibilities

- `PRD.md` defines product goals, scope, requirements, constraints, and acceptance criteria.
- `AGENTS.md` defines architecture, engineering practices, testing rules, and AI-agent workflow.
- `README.md` describes implemented features, setup, usage, deployment, and the implementation roadmap.

If these documents conflict:

1. `PRD.md` is authoritative for product behavior and scope.
2. `AGENTS.md` is authoritative for engineering and workflow rules.
3. The conflict must be reported before implementation continues.
