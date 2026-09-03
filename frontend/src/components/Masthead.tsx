import type { Route } from "../App";
interface Props { route: Route; }

export function Masthead({ route }: Props) {
  return <header class="masthead">
    <a class="brand" href="/#main"><strong>AI Demo</strong><span>LLM workbench</span></a>
    <nav class="primary-navigation" aria-label="Primary navigation">
      <a href="/#main" aria-current={route === "chat" ? "page" : undefined}>Chat</a>
      <a href="/#/faq" aria-current={route === "faq" ? "page" : undefined}>Q&amp;A</a>
      <a href="/#/status" aria-current={route === "status" ? "page" : undefined}>Status</a>
      <a href="/api/health" target="_blank" rel="noopener noreferrer"><span>Health</span><span class="external-mark" aria-hidden="true">↗</span><span class="visually-hidden">Opens in a new tab</span></a>
    </nav>
  </header>;
}
