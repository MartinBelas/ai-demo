import { useState } from "preact/hooks";

interface Props {
  providerAvailable: boolean;
  streaming: boolean;
  suggestion: string;
  onSuggestionUsed: () => void;
  onSubmit: (content: string) => Promise<void>;
  onStop: () => void;
}

export function Composer({ providerAvailable, streaming, suggestion, onSuggestionUsed, onSubmit, onStop }: Props) {
  const [prompt, setPrompt] = useState("");
  const displayedPrompt = suggestion || prompt;
  const setDisplayedPrompt = (value: string) => {
    if (suggestion) onSuggestionUsed();
    setPrompt(value);
  };
  const submit = async () => {
    const content = displayedPrompt.trim();
    if (!content || streaming || !providerAvailable) return;
    setPrompt("");
    onSuggestionUsed();
    await onSubmit(content);
  };

  return <form class="composer" onSubmit={(event) => { event.preventDefault(); void submit(); }}>
    <label htmlFor="prompt">Message</label>
    <textarea id="prompt" rows={3} value={displayedPrompt} onInput={(event) => setDisplayedPrompt(event.currentTarget.value)} onKeyDown={(event) => handleShortcut(event, submit)} placeholder={providerAvailable ? "Ask the selected model…" : "Select an available provider first…"} disabled={streaming || !providerAvailable} />
    <div class="composer-footer"><span><kbd>Ctrl</kbd> / <kbd>⌘</kbd> + <kbd>Enter</kbd> to send</span>{streaming ? <button class="stop-button" type="button" onClick={onStop}>Stop <i /></button> : <button class="send-button" type="submit" disabled={!displayedPrompt.trim() || !providerAvailable}>Send <span>↗</span></button>}</div>
  </form>;
}

function handleShortcut(event: KeyboardEvent, submit: () => Promise<void>): void {
  if (!(event.ctrlKey || event.metaKey) || event.key !== "Enter") return;
  event.preventDefault();
  void submit();
}
