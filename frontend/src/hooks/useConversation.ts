import { useCallback, useEffect, useRef, useState } from "preact/hooks";
import { streamChat } from "../api";
import { HISTORY_KEY, readHistory, removeIncompleteTurn } from "../history";
import type { ChatMessage, Completion, StreamEvent, ToolActivity } from "../types";

export type StreamState = "idle" | "connecting" | "thinking" | "tooling" | "answering" | "complete" | "error";

export function useConversation(providerId: string) {
  const [messages, setMessages] = useState<ChatMessage[]>(initialHistory);
  const [thinking, setThinking] = useState("");
  const [partial, setPartial] = useState("");
  const [completion, setCompletion] = useState<Completion | null>(null);
  const [toolActivity, setToolActivity] = useState<ToolActivity | null>(null);
  const [streamState, setStreamState] = useState<StreamState>("idle");
  const [error, setError] = useState("");
  const controller = useRef<AbortController | null>(null);
  const requestSequence = useRef(0);
  const streaming = isStreaming(streamState);

  useEffect(() => writeHistory(messages), [messages]);

  const clear = useCallback(() => {
    requestSequence.current += 1;
    controller.current?.abort();
    controller.current = null;
    setMessages([]);
    resetResponse(setThinking, setPartial, setCompletion, setToolActivity, setError, setStreamState);
  }, []);

  const stop = useCallback(() => {
    requestSequence.current += 1;
    controller.current?.abort();
    controller.current = null;
    setMessages((value) => removeIncompleteTurn(value));
    resetResponse(setThinking, setPartial, setCompletion, setToolActivity, setError, setStreamState);
    setError("Response stopped.");
    setStreamState("error");
  }, []);

  const submit = useCallback(async (content: string) => {
    if (!content || isStreaming(streamState) || !providerId) return;
    const requestMessages = [...messages, { role: "USER", content } satisfies ChatMessage];
    const requestId = ++requestSequence.current;
    const abortController = new AbortController();
    controller.current = abortController;
    setMessages(requestMessages);
    resetResponse(setThinking, setPartial, setCompletion, setToolActivity, setError, setStreamState);
    setStreamState("connecting");
    await runStream(providerId, requestMessages, requestId, abortController);
  }, [messages, providerId, streamState]);

  async function runStream(
    selectedProvider: string,
    requestMessages: ChatMessage[],
    requestId: number,
    abortController: AbortController,
  ): Promise<void> {
    let answer = "";
    let completed = false;
    const current = () => requestSequence.current === requestId;
    const handleEvent = (event: StreamEvent) => {
      if (!current()) return;
      const result = applyStreamEvent(event, answer, { setThinking, setPartial, setCompletion, setToolActivity, setError, setStreamState });
      answer = result.answer;
      completed = result.completed || completed;
      if (result.completed && answer.trim()) {
        setMessages((value) => [...value, { role: "ASSISTANT", content: answer }]);
      }
    };

    try {
      await streamChat(selectedProvider, requestMessages, abortController.signal, handleEvent);
    } catch (reason) {
      if (current()) handleStreamFailure(reason, abortController.signal.aborted, setError, setStreamState);
    } finally {
      if (current()) {
        if (answer.trim() && !completed) setPartial(answer);
        controller.current = null;
      }
    }
  }

  return { messages, thinking, partial, completion, toolActivity, streamState, error, streaming, submit, clear, stop };
}

type TextSetter = (value: string | ((current: string) => string)) => void;
type StateSetter<T> = (value: T) => void;

interface ResponseSetters {
  setThinking: TextSetter;
  setPartial: TextSetter;
  setCompletion: StateSetter<Completion | null>;
  setToolActivity: StateSetter<ToolActivity | null>;
  setError: TextSetter;
  setStreamState: StateSetter<StreamState>;
}

function applyStreamEvent(
  event: StreamEvent,
  answer: string,
  setters: ResponseSetters,
): { answer: string; completed: boolean } {
  switch (event.type) {
    case "thinking":
      setters.setStreamState("thinking");
      setters.setThinking((value) => value + event.content);
      return { answer, completed: false };
    case "tool":
      setters.setToolActivity(event.tool);
      setters.setStreamState("tooling");
      return { answer, completed: false };
    case "content": {
      const nextAnswer = answer + event.content;
      setters.setStreamState("answering");
      setters.setPartial(nextAnswer);
      return { answer: nextAnswer, completed: false };
    }
    case "completion":
      setters.setCompletion(event.completion);
      setters.setStreamState("complete");
      setters.setPartial("");
      return { answer, completed: true };
    case "error":
      setters.setError(event.message);
      setters.setStreamState("error");
      return { answer, completed: false };
  }
}

function resetResponse(
  setThinking: TextSetter,
  setPartial: TextSetter,
  setCompletion: StateSetter<Completion | null>,
  setToolActivity: StateSetter<ToolActivity | null>,
  setError: TextSetter,
  setStreamState: StateSetter<StreamState>,
): void {
  setThinking("");
  setPartial("");
  setCompletion(null);
  setToolActivity(null);
  setError("");
  setStreamState("idle");
}

function handleStreamFailure(
  reason: unknown,
  aborted: boolean,
  setError: TextSetter,
  setStreamState: StateSetter<StreamState>,
): void {
  let message = "The response failed.";
  if (aborted) message = "Response stopped.";
  else if (reason instanceof Error) message = reason.message;
  setError(message);
  setStreamState("error");
}

function isStreaming(state: StreamState): boolean {
  return state === "connecting" || state === "thinking" || state === "tooling" || state === "answering";
}

function initialHistory(): ChatMessage[] {
  if (typeof localStorage === "undefined") return [];
  return readHistory(localStorage);
}

function writeHistory(messages: ChatMessage[]): void {
  try {
    localStorage.setItem(HISTORY_KEY, JSON.stringify(messages));
  } catch {
    // The conversation still works in memory when storage is unavailable or full.
  }
}
