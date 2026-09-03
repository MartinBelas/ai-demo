import { useEffect, useState } from "preact/hooks";
import { Composer } from "./components/Composer";
import { ConversationThread } from "./components/ConversationThread";
import { Masthead } from "./components/Masthead";
import { ProjectIntroduction } from "./components/ProjectIntroduction";
import { ProviderPanel } from "./components/ProviderPanel";
import { FaqPage } from "./components/FaqPage";
import { StatusPage } from "./components/StatusPage";
import { SiteFooter } from "./components/SiteFooter";
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
        let targetId = "main";
        if (faqSection) targetId = faqSection;
        else if (nextRoute === "chat") targetId = window.location.hash.slice(1) || "main";
        document.getElementById(targetId)?.scrollIntoView();
      }, 0);
    };
    window.addEventListener("hashchange", handleHashChange);
    handleHashChange();
    return () => window.removeEventListener("hashchange", handleHashChange);
  }, []);
  if (route === "chat") return <ChatPage />;
  return <div class="app-shell"><Masthead route={route} />{route === "faq" ? <FaqPage /> : <StatusPage />}<SiteFooter /></div>;
}

function ChatPage() {
  const providerState = useProviders();
  const conversation = useConversation(providerState.providerId);
  const [suggestion, setSuggestion] = useState("");
  const canClear = conversation.messages.length > 0 || Boolean(conversation.partial);

  return <div class="app-shell">
    <Masthead route="chat" />
    <ProjectIntroduction />
    <main id="main" class="workbench">
      <ProviderPanel providers={providerState.providers} providerId={providerState.providerId} activeProvider={providerState.activeProvider} loading={providerState.loading} error={providerState.error} streaming={conversation.streaming} onChange={providerState.setProviderId} onRetry={() => void providerState.load()} />
      <section class="conversation" aria-label="Conversation">
        <ConversationThread {...conversation} onSuggestion={setSuggestion} />
        <Composer providerAvailable={Boolean(providerState.providerId)} streaming={conversation.streaming} suggestion={suggestion} canClear={canClear} onSuggestionUsed={() => setSuggestion("")} onSubmit={conversation.submit} onStop={conversation.stop} onClear={conversation.clear} />
      </section>
    </main>
    <SiteFooter />
  </div>;
}

export type Route = "chat" | "faq" | "status";
export function routeFromHash(hash: string): Route {
  if (hash === "#/status") return "status";
  if (hash.startsWith("#/faq")) return "faq";
  return "chat";
}
