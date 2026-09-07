# AI Demo

AI Demo is an educational Java 21 application that demonstrates provider-independent AI integration without an AI framework. It has a local console and a web chat served by an embedded HTTP server.

The chat supports streaming responses, a calculator tool, and configurable LLM providers: Ollama, OpenAI, GroqCloud, and Gemini. Provider availability depends on deployment configuration and credentials. Switching the chat provider does not switch the embedding model.

The web chat offers an optional "Use project documents" mode. It searches the bundled project documents and sends selected text passages to the chat agent along with the question. Retrieved source excerpts are displayed with the answer.
