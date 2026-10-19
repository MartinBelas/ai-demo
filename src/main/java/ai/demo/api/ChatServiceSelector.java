package ai.demo.api;

import ai.demo.exception.AgentModeDisabledException;
import ai.demo.service.ChatService;

/**
 * Chooses the chat service for a request: the agent-mode service when the request asks for agent
 * mode, otherwise the service of the selected LLM provider. The two paths stay independent.
 */
public final class ChatServiceSelector {

  private final ChatServiceResolver providerServices;
  private final ChatService agentService;

  /**
   * @param agentService the agent-mode chat service, or {@code null} when agent mode is disabled
   */
  public ChatServiceSelector(ChatServiceResolver providerServices, ChatService agentService) {
    this.providerServices = providerServices;
    this.agentService = agentService;
  }

  ChatService select(ResolvedChatRequest request) {
    if (!request.agent()) {
      return providerServices.resolve(request.provider());
    }
    if (agentService == null) {
      throw new AgentModeDisabledException();
    }
    return agentService;
  }

  boolean agentModeEnabled() {
    return agentService != null;
  }
}
