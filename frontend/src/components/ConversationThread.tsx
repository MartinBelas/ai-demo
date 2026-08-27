import { useEffect, useRef, useState } from "preact/hooks";
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
  const [thinkingExpanded, setThinkingExpanded] = useState(true);
  useEffect(() => {
    threadEnd.current?.scrollIntoView({ behavior: "smooth", block: "end" });
  }, [props.messages, props.partial, props.thinking, props.toolActivity]);
  useEffect(() => {
    if (props.streaming && props.thinking) setThinkingExpanded(true);
  }, [props.streaming, props.thinking]);
  useEffect(() => {
    if (props.streaming || !props.completion || !props.thinking) return;
    const collapseTimer = window.setTimeout(() => setThinkingExpanded(false), 300);
    return () => window.clearTimeout(collapseTimer);
  }, [props.streaming, props.completion, props.thinking]);

  const showLive = props.streaming || Boolean(props.partial) || Boolean(props.error);
  const showCompletedActivity = Boolean(props.completion) && hasActivity(props.thinking, props.toolActivity);
  return <div class="thread">
    <span class="visually-hidden" role="status" aria-live="polite">{streamStatusMessage(props.streamState)}</span>
    <ConversationHeader onSuggestion={props.onSuggestion} />
    <div class="message-list">
      {props.messages.map((message, index) => <Message key={`${index}-${message.content.slice(0, 12)}`} message={message} />)}
    {showLive && <LiveResponse {...props} thinkingExpanded={thinkingExpanded} onThinkingToggle={setThinkingExpanded} />}
    {showCompletedActivity && <ActivityTrace thinking={props.thinking} toolActivity={props.toolActivity} thinkingExpanded={thinkingExpanded} onThinkingToggle={setThinkingExpanded} />}
    {props.completion && <CompletionMeta value={props.completion} />}
      <div class="thread-end" ref={threadEnd} />
    </div>
  </div>;
}

interface ConversationHeaderProps {
  onSuggestion: (prompt: string) => void;
}

function ConversationHeader({ onSuggestion }: ConversationHeaderProps) {
  return <header class="conversation-header">
    <div><p class="eyebrow">02 / Conversation</p><h1>Ask something worth examining.</h1><p>Compare an explanation, explore a technical idea, or see how the model reasons in real time.</p></div>
    <button class="starting-prompt" type="button" onClick={() => onSuggestion("Explain retrieval-augmented generation in plain English.")}>Try a starting prompt <span>→</span></button>
  </header>;
}

function Message({ message }: { message: ChatMessage }) {
  const role = message.role === "USER" ? "You" : "Assistant";
  return <article class={`message message-${message.role.toLowerCase()}`}><p class="role-label">{role}</p><p>{message.content}</p></article>;
}

interface ThinkingToggleProps {
  thinkingExpanded: boolean;
  onThinkingToggle: (expanded: boolean) => void;
}

function LiveResponse({ streamState, thinking, partial, toolActivity, error, thinkingExpanded, onThinkingToggle }: Props & ThinkingToggleProps) {
  return <article class="live-response">
    <div class={`signal-rail signal-${streamState}`} aria-hidden="true" />
    <div class="live-body">
      <p class="role-label">Assistant <span>{streamState}</span></p>
      <ActivityTrace thinking={thinking} toolActivity={toolActivity} thinkingExpanded={thinkingExpanded} onThinkingToggle={onThinkingToggle} />
      {partial && <p class="answer-text">{partial}</p>}
      {!partial && isWaiting(streamState) && <p class="waiting">{waitingMessage(streamState)}</p>}
      {error && <p class="stream-error" role="alert">{error}</p>}
    </div>
  </article>;
}

type ActivityTraceProps = Pick<Props, "thinking" | "toolActivity"> & ThinkingToggleProps;

function ActivityTrace({ thinking, toolActivity, thinkingExpanded, onThinkingToggle }: ActivityTraceProps) {
  if (!hasActivity(thinking, toolActivity)) return null;
  return <div class="activity-trace">
    {thinking && <details class="thinking" open={thinkingExpanded} onToggle={(event) => onThinkingToggle(event.currentTarget.open)}><summary>Model thinking</summary><p>{thinking}</p></details>}
    {toolActivity && <ToolTrace activity={toolActivity} />}
  </div>;
}

function hasActivity(thinking: string, toolActivity: ToolActivity | null): boolean {
  return Boolean(thinking) || Boolean(toolActivity);
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
  return state === "thinking" ? "Working through the prompt…" : "Analyzing the request…";
}

function streamStatusMessage(state: StreamState): string {
  switch (state) {
    case "connecting": return "Analyzing the request.";
    case "thinking": return "Model thinking received.";
    case "tooling": return "A tool is running.";
    case "answering": return "Assistant response started.";
    case "complete": return "Assistant response complete.";
    case "error": return "Assistant response failed.";
    case "idle": return "";
  }
}

function CompletionMeta({ value }: { value: Completion }) {
  const duration = value.durationMs < 1000 ? `${value.durationMs} ms` : `${(value.durationMs / 1000).toFixed(1)} s`;
  return <dl class="completion-meta" aria-label="Response details"><div><dt>Model</dt><dd>{value.model}</dd></div><div><dt>Prompt</dt><dd>{value.tokenUsage.promptTokens}</dd></div><div><dt>Completion</dt><dd>{value.tokenUsage.completionTokens}</dd></div><div><dt>Total</dt><dd>{value.tokenUsage.totalTokens}</dd></div><div><dt>Duration</dt><dd>{duration}</dd></div></dl>;
}
