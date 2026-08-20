package ai.demo.agent;

import ai.demo.client.LlmResponse;
import ai.demo.client.StreamingResult;
import ai.demo.model.chat.ChatChunk;
import ai.demo.model.chat.Conversation;
import java.util.Map;
import java.util.function.Consumer;

public interface AgentLlmGateway {

  LlmResponse request(Conversation conversation, Map<String, String> variables);

  StreamingResult stream(
      Conversation conversation, Map<String, String> variables, Consumer<ChatChunk> consumer);
}
