# AI-Demo Project Guidelines

## Overview

AI-Demo is a Java 21 application demonstrating integration with Large Language Models.

The application is designed with a clean layered architecture and provider-independent LLM integration.

The current LLM provider is Ollama, but the architecture must allow adding other providers in the future.

---

# Architecture

Follow these layers:

```text
ai.demo
├── client          External LLM provider communication
├── config          Application configuration
├── exception       Application specific exceptions
├── model           Domain models
├── service         Application/business logic
└── console         User interface
```

## Layer responsibilities

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

Good:

```text
ChatService -> LlmClient
```

Bad:

```text
ChatService -> OllamaClient
```

The service layer must not know which LLM provider is used.

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

---

# LLM Client Architecture

## Why LlmClient interface exists

The application must not be coupled to a specific LLM provider.

The abstraction:

```java
public interface LlmClient {

    LlmResponse chat(Conversation conversation);

}
```

allows adding new providers without changing application logic.

Example:

```text
                 ChatService

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

New providers must not be referenced directly from services.

Examples:

- OllamaClient
- OpenAiClient
- AnthropicClient

The service layer works only with `LlmClient`.

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

├── ConfigurationException
├── LlmException
└── LlmCommunicationException
```

External communication problems should be converted into Llm exceptions.

Configuration problems should be converted into ConfigurationException.

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