package ai.demo.agent;

import ai.demo.client.LlmClient;
import ai.demo.client.LlmResponse;
import ai.demo.model.chat.Conversation;
import ai.demo.model.prompt.Prompt;
import ai.demo.prompt.PromptComposer;
import java.util.Map;
import java.util.Objects;

public class DefaultAgentLlmGateway implements AgentLlmGateway {

  private final LlmClient llmClient;
  private final PromptComposer promptComposer;

  public DefaultAgentLlmGateway(LlmClient llmClient, PromptComposer promptComposer) {

    this.llmClient = Objects.requireNonNull(llmClient);
    this.promptComposer = Objects.requireNonNull(promptComposer);
  }

  @Override
  public LlmResponse request(Conversation conversation, Map<String, String> variables) {

    Prompt prompt = promptComposer.compose(conversation, variables);

    return llmClient.chat(prompt);
  }
}
