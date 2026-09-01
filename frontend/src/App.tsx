import { useEffect, useState } from "preact/hooks";
import { Composer } from "./components/Composer";
import { ConversationThread } from "./components/ConversationThread";
import { Masthead } from "./components/Masthead";
import { ProjectIntroduction } from "./components/ProjectIntroduction";
import { ProviderPanel } from "./components/ProviderPanel";
import { FaqPage } from "./components/FaqPage";
import { useConversation } from "./hooks/useConversation";
import { useProviders } from "./hooks/useProviders";

export function App() {
  const [route, setRoute] = useState(() => routeFromHash(window.location.hash));
  useEffect(() => {
    const handleHashChange = () => {
      const nextRoute = routeFromHash(window.location.hash);
      setRoute(nextRoute);
      window.setTimeout(() => {
        const faqSection = /^#\/faq\/(why|local|docker)$/.exec(window.location.hash)?.[1];
        document.getElementById(faqSection ?? (window.location.hash.slice(1) || "main"))?.scrollIntoView();
      }, 0);
    };
    window.addEventListener("hashchange", handleHashChange);
    handleHashChange();
    return () => window.removeEventListener("hashchange", handleHashChange);
  }, []);
  return route === "faq" ? <div class="app-shell"><Masthead route={route} /><FaqPage /></div> : <ChatPage />;
}

function ChatPage() {
  const providerState = useProviders();
  const conversation = useConversation(providerState.providerId);
  const [suggestion, setSuggestion] = useState("");
  const canClear = conversation.messages.length > 0 || Boolean(conversation.partial);

  return <div class="app-shell">
    <Masthead route="chat" loading={providerState.loading} providerError={providerState.error} providerCount={providerState.providers.length} />
    <ProjectIntroduction />
    <main id="main" class="workbench">
      <ProviderPanel providers={providerState.providers} providerId={providerState.providerId} activeProvider={providerState.activeProvider} loading={providerState.loading} error={providerState.error} streaming={conversation.streaming} onChange={providerState.setProviderId} onRetry={() => void providerState.load()} />
      <section class="conversation" aria-label="Conversation">
        <ConversationThread {...conversation} onSuggestion={setSuggestion} />
        <Composer providerAvailable={Boolean(providerState.providerId)} streaming={conversation.streaming} suggestion={suggestion} canClear={canClear} onSuggestionUsed={() => setSuggestion("")} onSubmit={conversation.submit} onStop={conversation.stop} onClear={conversation.clear} />
      </section>
    </main>
  </div>;
}

export type Route = "chat" | "faq";
export function routeFromHash(hash: string): Route { return hash.startsWith("#/faq") ? "faq" : "chat"; }
