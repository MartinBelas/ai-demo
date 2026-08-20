package ai.demo.service;

import ai.demo.agent.Agent;
import ai.demo.agent.AgentResult;
import ai.demo.client.LlmClient;
import ai.demo.client.StreamingResult;
import ai.demo.model.chat.ChatChunk;
import ai.demo.model.chat.ChatResponse;
import ai.demo.model.chat.Conversation;
import ai.demo.model.prompt.Prompt;
import ai.demo.prompt.PromptComposer;
import java.util.Objects;
import java.util.function.Consumer;

public class ChatService {

  private final Agent agent;

  private final LlmClient llmClient;
  private final PromptComposer promptComposer;

  public ChatService(Agent agent, LlmClient llmClient, PromptComposer promptComposer) {

    this.agent = Objects.requireNonNull(agent);
    this.llmClient = Objects.requireNonNull(llmClient);
    this.promptComposer = Objects.requireNonNull(promptComposer);
  }

  public ChatResponse ask(Conversation conversation) {

    validateConversation(conversation);

    long start = System.currentTimeMillis();

    AgentResult result = agent.execute(conversation);

    long duration = System.currentTimeMillis() - start;

    return new ChatResponse(result.answer(), result.model(), duration);
  }

  public StreamingResult askStreaming(Conversation conversation, Consumer<ChatChunk> consumer) {

    validateConversation(conversation);

    if (consumer == null) {
      throw new IllegalArgumentException("consumer must not be null");
    }

    Prompt prompt = promptComposer.compose(conversation);

    return llmClient.stream(prompt, consumer);
  }

  private void validateConversation(Conversation conversation) {

    if (conversation == null) {
      throw new IllegalArgumentException("conversation must not be null");
    }

    if (conversation.isEmpty()) {
      throw new IllegalStateException("conversation must not be empty");
    }
  }
}
