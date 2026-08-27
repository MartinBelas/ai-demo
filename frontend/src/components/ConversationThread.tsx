import { useEffect, useRef } from "preact/hooks";
import type { StreamState } from "../hooks/useConversation";
import type { ChatMessage, Completion, ToolActivity } from "../types";

interface Props {
  messages: ChatMessage[];
  thinking: string;
  partial: string;
  completion: Completion | null;
  toolActivity: ToolActivity | null;
  streamState: StreamState;
  error: string;
  streaming: boolean;
  onSuggestion: (prompt: string) => void;
}

export function ConversationThread(props: Props) {
  const threadEnd = useRef<HTMLDivElement>(null);
  useEffect(() => {
    threadEnd.current?.scrollIntoView({ behavior: "smooth", block: "end" });
  }, [props.messages, props.partial, props.thinking, props.toolActivity]);

  const showEmpty = props.messages.length === 0 && !props.partial;
  const showLive = props.streaming || Boolean(props.partial) || Boolean(props.error);
  return <div class="thread" aria-live="polite">
    {showEmpty && <EmptyState onSuggestion={props.onSuggestion} />}
    {!showEmpty && props.messages.map((message, index) => <Message key={`${index}-${message.content.slice(0, 12)}`} message={message} />)}
    {showLive && <LiveResponse {...props} />}
    {props.completion && props.toolActivity && <ToolTrace activity={props.toolActivity} />}
    {props.completion && <CompletionMeta value={props.completion} />}
    <div ref={threadEnd} />
  </div>;
}

function EmptyState({ onSuggestion }: { onSuggestion: (prompt: string) => void }) {
  return <div class="empty-state"><p class="eyebrow">02 / Conversation</p><h1>Ask something worth examining.</h1><p>Compare an explanation, explore a technical idea, or see how the model reasons in real time.</p><button type="button" onClick={() => onSuggestion("Explain retrieval-augmented generation in plain English.")}>Try a starting prompt <span>→</span></button></div>;
}

function Message({ message }: { message: ChatMessage }) {
  const role = message.role === "USER" ? "You" : "Assistant";
  return <article class={`message message-${message.role.toLowerCase()}`}><p class="role-label">{role}</p><p>{message.content}</p></article>;
}

function LiveResponse({ streamState, thinking, partial, toolActivity, error }: Props) {
  return <article class="live-response">
    <div class={`signal-rail signal-${streamState}`} aria-hidden="true"><i /></div>
    <div class="live-body">
      <p class="role-label">Assistant <span>{streamState}</span></p>
      {thinking && <details class="thinking" open={streamState === "thinking"}><summary>Model thinking</summary><p>{thinking}</p></details>}
      {toolActivity && <ToolTrace activity={toolActivity} />}
      {partial && <p class="answer-text">{partial}</p>}
      {!partial && isWaiting(streamState) && <p class="waiting">{waitingMessage(streamState)}</p>}
      {error && <p class="stream-error" role="alert">{error}</p>}
    </div>
  </article>;
}

function ToolTrace({ activity }: { activity: ToolActivity }) {
  const completed = activity.status === "COMPLETED";
  const toolName = humanizeToolName(activity.name);
  const message = completed ? `${toolName} completed` : `Using ${toolName.toLowerCase()}`;
  return <div class={`tool-trace tool-trace-${activity.status.toLowerCase()}`} role="status" aria-live="polite" aria-atomic="true">
    <span class="tool-trace-marker" aria-hidden="true" />
    <span class="tool-trace-label">Tool</span>
    <span class="tool-trace-message">{message}</span>
    {!completed && <span class="tool-trace-motion" aria-hidden="true" />}
  </div>;
}

function humanizeToolName(name: string): string {
  const words = name.trim().replaceAll(/[_-]+/g, " ");
  return words.charAt(0).toUpperCase() + words.slice(1);
}

function isWaiting(state: StreamState): boolean {
  return state === "connecting" || state === "thinking";
}

function waitingMessage(state: StreamState): string {
  return state === "thinking" ? "Working through the prompt…" : "Establishing a live stream…";
}

function CompletionMeta({ value }: { value: Completion }) {
  const duration = value.durationMs < 1000 ? `${value.durationMs} ms` : `${(value.durationMs / 1000).toFixed(1)} s`;
  return <dl class="completion-meta" aria-label="Response details"><div><dt>Model</dt><dd>{value.model}</dd></div><div><dt>Prompt</dt><dd>{value.tokenUsage.promptTokens}</dd></div><div><dt>Completion</dt><dd>{value.tokenUsage.completionTokens}</dd></div><div><dt>Total</dt><dd>{value.tokenUsage.totalTokens}</dd></div><div><dt>Duration</dt><dd>{duration}</dd></div></dl>;
}
