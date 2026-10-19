package ai.demo.api;

import ai.demo.config.LlmProvider;
import ai.demo.model.chat.Conversation;

/**
 * A parsed chat request. In agent mode ({@code agent}) the provider is resolved but not used: the
 * external agent service chooses its own model.
 */
record ResolvedChatRequest(
    LlmProvider provider, Conversation conversation, boolean rag, boolean agent) {}
