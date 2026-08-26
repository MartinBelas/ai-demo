interface Props {
  loading: boolean;
  providerError: string;
  providerCount: number;
  canClear: boolean;
  onClear: () => void;
}

export function Masthead({ loading, providerError, providerCount, canClear, onClear }: Props) {
  const status = providerStatus(loading, providerError, providerCount);
  return <header class="masthead">
    <a class="brand" href="#main"><strong>AI Demo</strong><span>LLM workbench</span></a>
    <div class="masthead-actions">
      <span class={`backend-status ${status.className}`}><i />{status.label}</span>
      <button class="text-button" type="button" onClick={onClear} disabled={!canClear}>Clear chat</button>
    </div>
  </header>;
}

function providerStatus(loading: boolean, error: string, count: number) {
  if (loading) return { label: "Connecting", className: "" };
  if (error) return { label: "Backend unavailable", className: "is-error" };
  return { label: `${count} provider${count === 1 ? "" : "s"} ready`, className: "is-live" };
}
