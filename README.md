# AI Demo

A Java 21 project demonstrating how to build AI applications from scratch using clean architecture, modern Java, and Large Language Models (LLMs).

The project is developed incrementally, with each step focusing on clean design, testability, and software engineering best practices.

## Features

* Java 21
* Native `HttpClient`
* Jackson for JSON serialization
* SLF4J + Logback logging
* Conversation-based chat API
* Unit tests with JUnit and Mockito

## Requirements

* Java 21 or later
* Maven 3.9 or later
* Ollama

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

* Streaming responses
* Prompt templates
* Conversation memory
* Configuration improvements
* Retrieval-Augmented Generation (RAG)
* Spring Boot integration
* Integration tests
* GitHub Actions CI

## License

This project is intended for educational purposes.
