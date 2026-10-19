import { useEffect, useState } from "preact/hooks";
import { Composer } from "./components/Composer";
import { ConversationThread } from "./components/ConversationThread";
import { Masthead } from "./components/Masthead";
import { ProviderPanel } from "./components/ProviderPanel";
import { FaqPage } from "./components/FaqPage";
import { StatusPage } from "./components/StatusPage";
import { SiteFooter } from "./components/SiteFooter";
import { useConversation } from "./hooks/useConversation";
import { useProviders } from "./hooks/useProviders";
import { fetchAgentStatus, fetchRagStatus } from "./api";

export function App() {
  const [route, setRoute] = useState(() => routeFromHash(window.location.hash));
  useEffect(() => {
    const scrollToTarget = () => {
      const faqSection = /^#\/faq\/(why|limitations|local|docker|rag)$/.exec(window.location.hash)?.[1];
      const nextRoute = routeFromHash(window.location.hash);
      let targetId = "main";
      if (faqSection) targetId = faqSection;
      else if (nextRoute === "chat") targetId = window.location.hash.slice(1) || "main";
      document.getElementById(targetId)?.scrollIntoView({ behavior: "auto" });
    };
    const handleHashChange = () => {
      setRoute(routeFromHash(window.location.hash));
      requestAnimationFrame(() => requestAnimationFrame(scrollToTarget));
    };
    window.addEventListener("hashchange", handleHashChange);
    if (window.location.hash) handleHashChange();
    return () => window.removeEventListener("hashchange", handleHashChange);
  }, []);
  if (route === "chat") return <ChatPage />;
  return <div class="app-shell"><Masthead route={route} />{route === "faq" ? <FaqPage /> : <StatusPage />}<SiteFooter /></div>;
}

function ChatPage() {
  const providerState = useProviders();
  const [rag, setRag] = useState(false);
  const [ragAvailable, setRagAvailable] = useState(false);
  const [agent, setAgent] = useState(false);
  const [agentAvailable, setAgentAvailable] = useState(false);
  useEffect(() => {
    let active = true;
    void fetchRagStatus().then(value => { if (active) setRagAvailable(value); }).catch(() => {});
    void fetchAgentStatus().then(value => { if (active) setAgentAvailable(value); }).catch(() => {});
    return () => { active = false; };
  }, []);
  // Agent mode and RAG are mutually exclusive: turning one on turns the other off.
  const toggleRag = (enabled: boolean) => { setRag(enabled); if (enabled) setAgent(false); };
  const toggleAgent = (enabled: boolean) => { setAgent(enabled); if (enabled) setRag(false); };
  const agentActive = agent && agentAvailable;
  const conversation = useConversation(providerState.providerId, rag && ragAvailable, agentActive);
  const [suggestion, setSuggestion] = useState("");
  const canClear = conversation.messages.length > 0 || Boolean(conversation.partial);

  return <div class="app-shell">
    <Masthead route="chat" />
    <main id="main" class="workbench">
      <ProviderPanel providers={providerState.providers} providerId={providerState.providerId} activeProvider={providerState.activeProvider} loading={providerState.loading} error={providerState.error} streaming={conversation.streaming} onChange={providerState.setProviderId} onRetry={() => void providerState.load()} />
      <section class="conversation" aria-label="Conversation">
        <ConversationThread {...conversation} onSuggestion={setSuggestion} />
        {ragAvailable && <div class="rag-toggle">
          <label class="rag-toggle-control">
            <input type="checkbox" checked={rag} disabled={conversation.streaming} onChange={event => toggleRag(event.currentTarget.checked)} />
            <span>Use project documents (RAG)</span>
          </label>
          <p class="rag-toggle-hint">Search project information and show source passages. <a href="/#/faq/rag">How this works &amp; example questions →</a></p>
        </div>}
        {agentAvailable && <div class="rag-toggle agent-toggle">
          <label class="rag-toggle-control">
            <input type="checkbox" checked={agent} disabled={conversation.streaming} onChange={event => toggleAgent(event.currentTarget.checked)} />
            <span>Agent mode</span>
          </label>
          <p class="rag-toggle-hint">A separate agent that can search the web for up-to-date information answers instead of the selected provider, and lists the pages it used.</p>
        </div>}
        <Composer providerAvailable={canAnswer(providerState.providerId, agentActive)} streaming={conversation.streaming} suggestion={suggestion} canClear={canClear} onSuggestionUsed={() => setSuggestion("")} onSubmit={conversation.submit} onStop={conversation.stop} onClear={conversation.clear} />
      </section>
    </main>
    <SiteFooter />
  </div>;
}

/** Agent mode does not use the LLM provider, so it can answer even when no provider is available. */
export function canAnswer(providerId: string, agentActive: boolean): boolean {
  return agentActive || Boolean(providerId);
}

export type Route = "chat" | "faq" | "status";
export function routeFromHash(hash: string): Route {
  if (hash === "#/status") return "status";
  if (hash.startsWith("#/faq")) return "faq";
  return "chat";
}
