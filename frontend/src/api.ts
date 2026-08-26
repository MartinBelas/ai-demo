import type { ChatMessage, Completion, LlmProvider, StreamEvent, TokenUsage } from "./types";

export async function fetchProviders(): Promise<LlmProvider[]> {
  const response = await fetch("/api/llm/providers");
  if (!response.ok) throw new Error(await publicError(response, "Unable to load LLM providers."));
  const body: unknown = await response.json();
  return isProviderResponse(body) ? body.providers : [];
}

export async function streamChat(provider: string, messages: ChatMessage[], signal: AbortSignal, onEvent: (event: StreamEvent) => void): Promise<void> {
  const response = await fetch("/api/chat/stream", {
    method: "POST",
    headers: { "Content-Type": "application/json", Accept: "text/event-stream" },
    body: JSON.stringify({ provider, messages: messages.slice(-10) }),
    signal,
  });
  if (!response.ok) throw new Error(await publicError(response, "Unable to start the response."));
  if (!response.body) throw new Error("The streaming response is unavailable.");
  await parseSse(response.body, onEvent);
}

export async function parseSse(stream: ReadableStream<Uint8Array>, onEvent: (event: StreamEvent) => void): Promise<void> {
  const reader = stream.getReader();
  const decoder = new TextDecoder();
  let buffer = "";
  let streamComplete = false;
  while (!streamComplete) {
    const chunk = await reader.read();
    streamComplete = chunk.done;
    buffer += decoder.decode(chunk.value, { stream: !streamComplete });
    const frames = buffer.replaceAll("\r\n", "\n").split("\n\n");
    buffer = frames.pop() ?? "";
    for (const frame of frames) emitFrame(frame, onEvent);
  }
  if (buffer.trim()) emitFrame(buffer, onEvent);
}

function emitFrame(frame: string, onEvent: (event: StreamEvent) => void): void {
  const fields = parseFrameFields(frame);
  if (fields.data.length === 0) return;
  const parsed: unknown = JSON.parse(fields.data.join("\n"));
  const event = toStreamEvent(fields.eventName, parsed);
  if (event) onEvent(event);
}

function parseFrameFields(frame: string): { eventName: string; data: string[] } {
  let eventName = "message";
  const data: string[] = [];
  for (const line of frame.split("\n")) {
    if (line.startsWith("event:")) eventName = line.slice(6).trim();
    else if (line.startsWith("data:")) data.push(line.slice(5).trimStart());
  }
  return { eventName, data };
}

function toStreamEvent(eventName: string, value: unknown): StreamEvent | null {
  if (eventName === "thinking" || eventName === "content") {
    return isRecord(value) && typeof value.content === "string" ? { type: eventName, content: value.content } : null;
  }
  if (eventName === "completion") return isCompletion(value) ? { type: "completion", completion: value } : null;
  if (eventName === "error") {
    const message = isRecord(value) && typeof value.message === "string" ? value.message : "The response failed.";
    return { type: "error", message };
  }
  return null;
}

function isProviderResponse(value: unknown): value is { providers: LlmProvider[] } {
  return isRecord(value) && Array.isArray(value.providers) && value.providers.every(isLlmProvider);
}

function isLlmProvider(value: unknown): value is LlmProvider {
  return isRecord(value) && typeof value.id === "string" && typeof value.model === "string";
}

function isCompletion(value: unknown): value is Completion {
  return isRecord(value) && typeof value.model === "string" && isTokenUsage(value.tokenUsage) && typeof value.durationMs === "number";
}

function isTokenUsage(value: unknown): value is TokenUsage {
  return isRecord(value) && typeof value.promptTokens === "number" && typeof value.completionTokens === "number" && typeof value.totalTokens === "number";
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null;
}

async function publicError(response: Response, fallback: string): Promise<string> {
  try {
    const body: unknown = await response.json();
    return isApiError(body) && body.message.trim() ? body.message : fallback;
  } catch {
    return fallback;
  }
}

function isApiError(value: unknown): value is { message: string } {
  return isRecord(value) && typeof value.message === "string";
}
