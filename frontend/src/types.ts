export type Role = "USER" | "ASSISTANT";

export interface ChatMessage {
  role: Role;
  content: string;
  sources?: RagSource[];
  webSources?: WebSource[];
}

export interface RagSource { id: string; document: string; chunk: number; text: string }

/** A web page found by the agent in agent mode; `url` is always an absolute http(s) URL. */
export interface WebSource { title: string; url: string }

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
  sources?: RagSource[];
  webSources?: WebSource[];
  model: string;
  tokenUsage: TokenUsage;
  durationMs: number;
}

export type ToolStatus = "RUNNING" | "COMPLETED";

export interface ToolActivity {
  name: string;
  status: ToolStatus;
}

export type StreamEvent =
  | { type: "thinking" | "content"; content: string }
  | { type: "tool"; tool: ToolActivity }
  | { type: "completion"; completion: Completion }
  | { type: "error"; message: string };
