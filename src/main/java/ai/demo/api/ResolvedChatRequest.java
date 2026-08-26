package ai.demo.api;

import ai.demo.config.LlmProvider;
import ai.demo.model.chat.Conversation;

record ResolvedChatRequest(LlmProvider provider, Conversation conversation) {}
