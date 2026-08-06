# AI Demo

A Java 21 project demonstrating how to build AI applications from scratch using clean architecture, modern Java, and Large Language Models (LLMs).

The project is developed incrementally, with each step focusing on clean design, testability, and software engineering best practices.

## Features

* Java 21
* Native `HttpClient`
* Jackson for JSON serialization
* SLF4J + Logback logging
* Conversation-based chat API
* Streaming responses
* Unit tests with JUnit and Mockito

## Requirements

* Java 21 or later
* Maven 3.9 or later
* Ollama (currently the only supported LLM provider)

## Running a Local LLM

The application is designed to support different Large Language Model (LLM) providers. The architecture is provider-independent, allowing additional providers to be integrated in the future.

At the moment, **Ollama** is the only implemented LLM provider.

### Install Ollama

Download and install Ollama from:

https://ollama.com/download

### Download a Model

You can use any model supported by Ollama. For example:

```bash
ollama pull qwen3:4b
```

Configure the selected model in `application.properties`:

```properties
llm.base-url=http://localhost:11434
llm.model=qwen3:4b
llm.temperature=0.7
llm.max-tokens=100
llm.context-window=4096
```

### Start Ollama

Start the Ollama server before running the application:

```bash
ollama serve
```

By default, the application connects to:

```text
http://localhost:11434
```

## Build

Compile the project:

```bash
mvn clean compile
```

Run the tests:

```bash
mvn test
```

Run all verification steps:

```bash
mvn clean verify
```

Format the source code:

```bash
mvn spotless:apply
```

## Roadmap

Phase 1: Core Chat ✅
- Chat conversations
- Prompt templates
- Prompt rendering
- Streaming responses
- Ollama integration
- Logging
- Unit tests

Phase 2: Context Management
- Token estimation
- Context window management
- Conversation summarization
- Persistent conversation memory

Phase 3: Knowledge Retrieval
- Document loaders
- Text chunking
- Embeddings
- Vector store
- Retrieval-Augmented Generation (RAG)

Phase 4: Tool Integration
- Structured outputs
- Tool calling
- Tool registry
- Multi-tool workflows

Phase 5: AI Agents
- Planning
- Agent execution loop
- Long-term memory
- Multi-agent collaboration

## License

This project is intended for educational purposes.