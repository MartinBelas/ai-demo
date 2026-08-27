import { useState } from "preact/hooks";
import { Composer } from "./components/Composer";
import { ConversationThread } from "./components/ConversationThread";
import { Masthead } from "./components/Masthead";
import { ProjectIntroduction } from "./components/ProjectIntroduction";
import { ProviderPanel } from "./components/ProviderPanel";
import { useConversation } from "./hooks/useConversation";
import { useProviders } from "./hooks/useProviders";

export function App() {
  const providerState = useProviders();
  const conversation = useConversation(providerState.providerId);
  const [suggestion, setSuggestion] = useState("");
  const canClear = conversation.messages.length > 0 || Boolean(conversation.partial);

  return <div class="app-shell">
    <Masthead loading={providerState.loading} providerError={providerState.error} providerCount={providerState.providers.length} canClear={canClear} onClear={conversation.clear} />
    <ProjectIntroduction />
    <main id="main" class="workbench">
      <ProviderPanel providers={providerState.providers} providerId={providerState.providerId} activeProvider={providerState.activeProvider} loading={providerState.loading} error={providerState.error} streaming={conversation.streaming} onChange={providerState.setProviderId} onRetry={() => void providerState.load()} />
      <section class="conversation" aria-label="Conversation">
        <ConversationThread {...conversation} onSuggestion={setSuggestion} />
        <Composer providerAvailable={Boolean(providerState.providerId)} streaming={conversation.streaming} suggestion={suggestion} onSuggestionUsed={() => setSuggestion("")} onSubmit={conversation.submit} onStop={conversation.stop} />
      </section>
    </main>
  </div>;
}
