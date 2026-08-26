import type { LlmProvider } from "../types";

interface Props {
  providers: LlmProvider[];
  providerId: string;
  activeProvider?: LlmProvider;
  loading: boolean;
  error: string;
  streaming: boolean;
  onChange: (providerId: string) => void;
  onRetry: () => void;
}

export function ProviderPanel(props: Props) {
  return <aside class="context-panel" aria-label="Model context">
    <p class="eyebrow">01 / Model</p>
    <h1>Choose the voice behind the response.</h1>
    <ProviderControl {...props} />
    {props.activeProvider && <ModelFacts provider={props.activeProvider} />}
    <p class="privacy-note"><span aria-hidden="true">↳</span>Your conversation stays in this browser. Only the latest 10 messages are sent with each request.</p>
  </aside>;
}

function ProviderControl({ providers, providerId, loading, error, streaming, onChange, onRetry }: Props) {
  if (error) return <ProviderState title="Connection interrupted" message={error} action="Try again" onRetry={onRetry} error />;
  if (!loading && providers.length === 0) return <ProviderState title="No models available" message="Start or configure an LLM provider, then retry." action="Check again" onRetry={onRetry} />;
  return <label class="provider-field" htmlFor="llm-provider">
    <span>LLM provider</span>
    <select id="llm-provider" value={providerId} onChange={(event) => onChange(event.currentTarget.value)} disabled={loading || streaming}>
      {loading && <option>Loading providers…</option>}
      {providers.map((provider) => <option key={provider.id} value={provider.id}>{provider.id} · {provider.model}</option>)}
    </select>
  </label>;
}

function ProviderState({ title, message, action, onRetry, error = false }: { title: string; message: string; action: string; onRetry: () => void; error?: boolean }) {
  return <div class="provider-state" role={error ? "alert" : undefined}><strong>{title}</strong><p>{message}</p><button type="button" onClick={onRetry}>{action}</button></div>;
}

function ModelFacts({ provider }: { provider: LlmProvider }) {
  return <dl class="model-facts"><div><dt>Provider</dt><dd>{provider.id}</dd></div><div><dt>Model</dt><dd>{provider.model}</dd></div></dl>;
}
