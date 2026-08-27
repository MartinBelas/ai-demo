import type { ChatMessage } from "./types";

export const HISTORY_KEY = "ai-demo.conversation";

export function validateHistory(value: unknown): ChatMessage[] {
  if (!Array.isArray(value)) return [];

  return value.filter(
    (item): item is ChatMessage =>
      typeof item === "object" &&
      item !== null &&
      (item as ChatMessage).role !== undefined &&
      ["USER", "ASSISTANT"].includes((item as ChatMessage).role) &&
      typeof (item as ChatMessage).content === "string" &&
      (item as ChatMessage).content.trim().length > 0,
  );
}

export function readHistory(storage: Pick<Storage, "getItem">): ChatMessage[] {
  try {
    const stored = storage.getItem(HISTORY_KEY);
    return stored === null ? [] : removeIncompleteTurn(validateHistory(JSON.parse(stored)));
  } catch {
    return [];
  }
}

export function removeIncompleteTurn(messages: ChatMessage[]): ChatMessage[] {
  let completeMessageCount = messages.length;
  while (completeMessageCount > 0 && messages[completeMessageCount - 1].role === "USER") {
    completeMessageCount -= 1;
  }
  return messages.slice(0, completeMessageCount);
}
