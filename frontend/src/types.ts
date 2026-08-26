export type Role = "USER" | "ASSISTANT";

export interface ChatMessage {
  role: Role;
  content: string;
}

export interface LlmProvider {
  id: string;
  model: string;
}

export interface TokenUsage {
  promptTokens: number;
  completionTokens: number;
  totalTokens: number;
}

export interface Completion {
  model: string;
  tokenUsage: TokenUsage;
  durationMs: number;
}

export type StreamEvent =
  | { type: "thinking" | "content"; content: string }
  | { type: "completion"; completion: Completion }
  | { type: "error"; message: string };
