# AI-Demo Project Guidelines

## Overview

AI-Demo is a Java 21 application demonstrating integration with Large Language Models.

The application is designed with a clean layered architecture and provider-independent LLM integration.

The current LLM provider is Ollama, but the architecture must allow adding other providers in the future.

## Product requirements

Product goals, current scope, functional requirements, constraints, and acceptance criteria are defined in:

```text
PRD.md
```

Before designing or implementing a feature:

1. Read the relevant sections of `PRD.md`.
2. Verify that the change is within the current product scope.
3. Use the documented requirements when designing tests.
4. Report conflicts between the request, PRD, and architecture.
5. Update `PRD.md` when an approved change modifies product behavior, scope, or constraints.

`AGENTS.md` is authoritative for engineering and workflow rules. `PRD.md` is authoritative for product behavior and scope. `README.md` describes current usage, setup, and the implementation roadmap.

---

# Architecture

Follow these layers:

```text
ai.demo
├── agent          Agent orchestration and tool calling
├── client         External LLM provider communication
├── config         Application configuration
├── console        User interface and commands
├── exception      Application-specific exceptions
├── model          Provider-independent domain models
├── persistence    Conversation storage
├── prompt         Prompt composition and templates
└── service        Application/business logic
```

## Layer responsibilities

### agent

Contains agent orchestration, structured decisions, events, and tool execution.

The agent layer may use `AgentLlmGateway` and tool abstractions. It must not depend on a concrete LLM provider.

### client

Contains communication with external AI providers.

Examples:

- LlmClient
- OllamaClient
- LoggingLlmClient

The client layer is responsible only for communication with LLM providers.

It must not contain business logic.

---

### service

Contains application logic.

Services must depend on abstractions, not concrete providers.

Current flow:

```text
ChatService -> Agent -> AgentLlmGateway -> LlmClient
```

Services must not bypass the agent or reference provider implementations.

Bad:

```text
ChatService -> OllamaClient
```

The service layer must not know which LLM provider or tools are used.

---

### model

Contains domain objects.

Examples:

- Conversation
- ChatMessage
- ChatResponse
- Role

Domain models must not depend on external providers.

---

### config

Contains application configuration handling.

Configuration must be loaded through:

```text
AppConfigLoader
```

Do not read configuration directly inside services or clients.

---

### exception

Contains application specific exceptions.

Do not use generic exceptions for application errors.

### persistence

Contains provider-independent conversation storage.

Persistence failures must be converted into `PersistenceException`.

### prompt

Contains prompt composition, loading, rendering, and templates.

Prompt logic must not depend on a concrete LLM provider.

---

# LLM Client Architecture

## Why LlmClient interface exists

The application must not be coupled to a specific LLM provider.

The abstraction:

```java
public interface LlmClient {

    LlmResponse chat(Prompt prompt);

    StreamingResult stream(Prompt prompt, Consumer<ChatChunk> consumer);

}
```

allows adding new providers without changing application logic.

Example:

```text
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
        +-------------+-------------+
        |             |             |
        v             v             v

 OllamaClient   OpenAiClient   OtherClient
```

---

## Provider implementations

Every LLM provider must implement:

```text
LlmClient
```

New providers must not be referenced directly from services or agents.

Examples:

- OllamaClient
- OpenAiClient
- AnthropicClient

The agent gateway works only with `LlmClient`. The service layer works with `Agent`.

---

# Response Models

## Why LlmResponse exists separately from Ollama DTOs

External provider responses are implementation details.

Example:

```text
Ollama API response

        |
        v

OllamaResponse

        |
        | mapping

        v

LlmResponse
```

The application works with:

```text
LlmResponse
```

not:

```text
OllamaResponse
```

Benefits:

- provider independence
- easier testing
- easier migration to another provider
- prevents external API models leaking into business logic

---

# Configuration

Configuration flow:

```text
application.properties

        |
        v

AppConfigLoader

        |
        v

AppConfig

        |
        v

Application components
```

Configuration validation belongs inside configuration classes.

Invalid configuration should result in:

```text
ConfigurationException
```

---

# Exceptions

Avoid generic exceptions:

```text
RuntimeException
IllegalStateException
```

Use application specific exceptions.

Current exceptions:

```text
ai.demo.exception

├── AgentDecisionException
├── ConfigurationException
├── LlmException
├── LlmCommunicationException
├── PersistenceException
└── PromptTemplateException
```

External communication problems should be converted into Llm exceptions.

Configuration problems should be converted into ConfigurationException.

Persistence problems should be converted into PersistenceException.

Prompt template problems should be converted into PromptTemplateException.

---

# Java

Use:

- Java 21
- records for immutable data
- constructor injection
- final variables where reasonable
- Google Java Format

Prefer:

- simple designs
- clear responsibilities
- readable code

Avoid:

- unnecessary abstractions
- premature optimization
- mixing layers

---

# Testing

Every new feature or refactoring must include tests.

## Test-driven development

Prefer test-driven development for new behavior and bug fixes:

1. Write or update a test that describes the required behavior.
2. Run the test and confirm that it fails for the expected reason.
3. Implement the smallest change that makes the test pass.
4. Refactor while keeping all tests green.
5. Run the complete relevant test suite and `mvn verify` before considering the change complete.

For bug fixes, add a regression test that reproduces the defect before changing the implementation whenever practical.

Do not write tests only to mirror implementation details. Tests should express observable behavior, business rules, layer contracts, and documented acceptance criteria.

Testing stack:

- JUnit 6
- Mockito

Rules:

- services require unit tests
- configuration loading requires tests
- external clients require isolated tests
- do not call real external services in unit tests

Before modifying code consider:

1. What behavior changes?
2. What tests verify this behavior?
3. Does the change affect existing functionality?

---

# Logging

Use SLF4J.

Preferred style:

```java
private static final Logger log =
    LoggerFactory.getLogger(MyClass.class);
```

Logging should provide useful operational information.

Avoid:

- excessive logging
- logging sensitive data
- leaking implementation details

---

# HTTP Clients

External HTTP communication belongs to the client layer.

Use:

- dedicated DTOs for external APIs
- mapping between external DTOs and internal models

Do not expose provider-specific DTOs outside the client package.

---

# Git

Use short imperative commit messages.

Start with an action verb:

- Add
- Update
- Improve
- Fix
- Extract
- Remove
- Rename

Examples:

```text
Add application specific exceptions

Extract configuration loading into AppConfigLoader

Add AppConfigLoader tests

Rename Maven artifact to ai-demo

Update project documentation
```

# Git Operations

AI agents must not perform Git operations automatically.

The agent must never:

* create commits
* modify commit history
* run `git commit`
* run `git push`
* run `git pull`
* run `git rebase`
* run `git reset`
* force push changes

The developer is responsible for all Git operations.

When changes are complete, the agent may:

* suggest a commit message
* summarize changed files
* show recommended next Git commands

Before any Git operation, always ask for explicit user confirmation.

---

# Before modifying code

Always consider:

1. Architecture impact
2. Required tests
3. Exception handling
4. Backward compatibility
5. Whether the abstraction still makes sense

---

When adding a new feature:

1. Update the model if needed
2. Update interfaces before implementations
3. Add implementation
4. Add tests
5. Update documentation if architecture changes
6. Update `PRD.md` if product behavior, scope, or constraints change
