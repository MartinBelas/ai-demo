# AI Demo – Product Requirements

## Product summary

AI Demo is an educational Java 21 console application that demonstrates how to build an LLM application without hiding its core behavior behind an AI framework. It uses clean architecture, provider-independent models, streaming responses, persistent conversations, configurable thinking output, and agent-based tool calling.

Ollama is the current provider, but provider-specific details must remain isolated so another provider can be added without changing application logic.

## Target users

- Java developers learning how LLM applications work internally
- Developers evaluating clean architecture for AI integrations
- Trainers and developers experimenting with local Ollama models

## Product goals

- Keep the complete request, streaming, agent, and tool flow understandable
- Demonstrate provider-independent LLM integration
- Support local model experimentation without cloud credentials
- Include realistic configuration, persistence, logging, error handling, and tests
- Remain small enough to serve as a reference implementation

## Product principles

- Educational readability before framework-like generalization
- Provider independence outside the client implementation
- Explicit data flow and clear layer responsibilities
- Deterministic unit tests without real external services
- Test-driven development for new behavior and bug fixes where practical
- Simple designs before additional abstractions
- New behavior includes tests and relevant documentation

## Current scope

- Interactive console chat
- Ollama chat and streaming integration
- Conversation history persisted in a local JSON file
- Thinking modes `ON`, `OFF`, `MINIMAL`, and `STATUS`
- Response summary with model, token usage, duration, and complete answer
- Agent decision between a direct reply and a registered tool
- Calculator tool with a single tool execution before the final reply
- Aggregated token usage across agent steps
- Maven verification and Google Java Format

## Functional requirements

### Chat and streaming

- A user can submit a question through the console.
- Content and thinking chunks are processed in their received order.
- Empty provider chunks are ignored.
- A successful answer is added to the conversation and persisted.
- Provider failures produce an application-specific LLM exception.

### Thinking output

- `ON` displays streamed thinking.
- `OFF` suppresses thinking.
- `MINIMAL` displays at most the first 200 thinking characters for every question.
- `MINIMAL` remains active until the user changes it.
- `STATUS` reports the active mode without changing it.

### Tool calling

- The model returns a structured direct-reply or tool-call decision.
- JSON wrapped in a Markdown code block is accepted.
- An invalid decision triggers at most one repair request.
- Only registered tools can be executed.
- The assistant tool decision and tool result are preserved in conversation order.
- The model produces the final reply after receiving the tool result.
- Token usage from all LLM calls is aggregated.
- Invalid tool input returns a tool failure instead of terminating the application.

### Conversation persistence

- Conversation data is stored as provider-independent JSON.
- A missing or empty file creates a new conversation.
- Unknown JSON properties are ignored.
- Read and write failures produce `PersistenceException`.

### Response summary

- The summary displays the model name.
- Prompt, completion, and total tokens are displayed.
- Duration is stored as `durationMs` and formatted as `mm:ss.xxx`.
- The complete response is displayed.

## Out of scope for the current version

- Production authentication and authorization
- Multiple simultaneous users
- REST or web interfaces
- Cloud LLM providers
- Unbounded autonomous agent loops
- Arbitrary code execution
- RAG and vector storage
- Distributed persistence and production deployment

These capabilities may be introduced through later roadmap phases, but must not complicate the current design prematurely.

## Constraints and known limitations

- Java 21, Maven, and Ollama are required.
- Ollama is the only implemented LLM provider.
- Conversation persistence uses a local file.
- Only the calculator tool is currently registered.
- Tool-based answers require two LLM calls and normally use more time and tokens.
- The agent supports one tool execution before the final response.
- Small local models may return invalid structured output.
- Some loaded generation settings are not yet forwarded to Ollama.

## Success criteria

- A developer can understand the main request flow from the package structure.
- A provider can be added through `LlmClient` without changing service logic.
- A tool can be added without changing `ChatService`.
- Unit tests run without a live Ollama server.
- Direct and tool-assisted requests complete without losing conversation state.
- `mvn verify` succeeds.
- README, PRD, tests, and implementation describe the same behavior.

## Definition of done

A product change is complete when:

1. It is consistent with the current scope or the approved scope change is documented.
2. Architecture, exceptions, compatibility, and provider independence were considered.
3. Automated tests cover the changed behavior where practical.
4. Existing tests continue to pass.
5. `mvn verify` succeeds.
6. README or PRD is updated when user-visible behavior, scope, or constraints change.

## Document responsibilities

- `PRD.md` defines product goals, scope, requirements, constraints, and acceptance criteria.
- `AGENTS.md` defines architecture, engineering practices, testing rules, and AI-agent workflow.
- `README.md` describes current features, setup, usage, and the implementation roadmap.

If these documents conflict:

1. `PRD.md` is authoritative for product behavior and scope.
2. `AGENTS.md` is authoritative for engineering and workflow rules.
3. The conflict must be reported before implementation continues.
