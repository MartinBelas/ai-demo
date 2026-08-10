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

Phase 1 – Core LLM application
✓ LLM client
✓ Ollama integration
✓ Configuration
✓ Error handling
✓ Logging
✓ Prompt model
✓ Prompt composition
✓ Prompt templates
✓ Streaming

Phase 2 – Conversation & observability
→ Conversation memory
→ Token usage
→ Conversation persistence
→ Metrics
→ Structured logging
→ LLM performance metrics
→ Langfuse integration  
→ Tracing
→ Prompt + response logging
→ Token usage analytics
→ Latency + cost dashboards

Phase 3 – Agent
→ Agent abstraction
→ Agent loop
→ Tool abstraction
→ Tool calling
→ Multi-step execution
→ Agent state

Phase 4 – Data & RAG
→ Document ingestion
→ Document parsing
→ Chunking
→ Embeddings
→ Vector store
→ Retrieval
→ RAG pipeline
→ Citation generation
→ n8n integration  
→ ingestion workflows
→ scheduled crawlers
→ preprocessing pipelines
→ external API enrichment

Phase 5 – Evaluation
→ Evaluation dataset
→ Automated evaluation
→ Relevance
→ Correctness
→ Citation coverage
→ Confidence scoring
→ Regression tests

Phase 6 – Production engineering
→ Retry / resilience
→ Concurrency
→ Performance benchmarks
→ Context-window management
→ Caching
→ Metrics

Phase 7 – Integration & deployment
→ REST API
→ Docker
→ GitHub Actions CI
→ Integration tests
→ Deployment

## License

This project is intended for educational purposes.