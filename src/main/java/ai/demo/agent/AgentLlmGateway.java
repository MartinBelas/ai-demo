package ai.demo.agent;

import ai.demo.client.LlmResponse;
import ai.demo.model.chat.Conversation;
import java.util.Map;

public interface AgentLlmGateway {

  LlmResponse request(Conversation conversation, Map<String, String> variables);
}
