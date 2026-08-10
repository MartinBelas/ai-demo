package ai.demo.model.chat;

public record ChatChunk(String content, ChatChunkType type, boolean finished) {}
