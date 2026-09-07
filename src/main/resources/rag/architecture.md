# Architecture

The chat flow is HTTP endpoint -> ChatService -> Agent -> AgentLlmGateway -> LlmClient. Concrete chat clients implement LlmClient; services do not depend on provider-specific DTOs.

RAG uses a separate EmbeddingClient abstraction and VectorStore abstraction. The initial implementations are OllamaEmbeddingClient and InMemoryVectorStore. The vector store ranks chunks by cosine similarity. The selected chat provider can differ from the embedding provider.

Bundled UTF-8 TXT and Markdown documents are normalized and divided into bounded chunks. Their embeddings are prepared once per process on the first RAG request and reused for subsequent questions. Each RAG question gets its own embedding. Ordinary chat requests do not call the embedding API.

RAG is an explicit per-request option, not an agent-selected tool. It does not require an MCP server and does not retrain the language model.
