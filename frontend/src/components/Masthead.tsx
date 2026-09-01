import type { Route } from "../App";
interface Props { route: Route; loading?: boolean; providerError?: string; providerCount?: number; }

export function Masthead({ route, loading, providerError = "", providerCount = 0 }: Props) {
  const status = loading === undefined ? undefined : providerStatus(loading, providerError, providerCount);
  return <header class="masthead">
    <a class="brand" href="/#main"><strong>AI Demo</strong><span>LLM workbench</span></a>
    <nav class="primary-navigation" aria-label="Primary navigation">
      <a href="/#main" aria-current={route === "chat" ? "page" : undefined}>Chat</a>
      <a href="/#about">About</a>
      <a href="/#/faq" aria-current={route === "faq" ? "page" : undefined}>Q&amp;A</a>
      <a href="/api/health" target="_blank" rel="noopener noreferrer"><span>Health</span><span class="external-mark" aria-hidden="true">↗</span><span class="visually-hidden">Opens in a new tab</span></a>
    </nav>
    {status
      ? <div class="masthead-actions"><span class={`backend-status ${status.className}`}><i />{status.label}</span></div>
      : <p class="masthead-edition">Java 21 / field guide</p>}
  </header>;
}

function providerStatus(loading: boolean, error: string, count: number) {
  if (loading) return { label: "Connecting", className: "" };
  if (error) return { label: "Backend unavailable", className: "is-error" };
  return { label: `${count} provider${count === 1 ? "" : "s"} ready`, className: "is-live" };
}
