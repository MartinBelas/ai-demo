package ai.demo.api;

import ai.demo.config.LlmProvider;
import ai.demo.service.ChatService;

/** Resolves a provider-specific chat service without exposing provider clients to HTTP handlers. */
@FunctionalInterface
public interface ChatServiceResolver {

  ChatService resolve(LlmProvider provider);
}
