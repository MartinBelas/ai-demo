export interface AppStatus {
  trackingEnabled: boolean;
  persistent: boolean;
  period: string;
  requests: number;
  tokens: number;
  completed: number;
  failed: number;
  disconnected: number;
  averageDurationMs: number | null;
  activeStreams: number;
  uptimeSeconds: number;
  dailyRequestLimit: number | null;
  requestsRemaining: number | null;
}

export async function fetchStatus(signal?: AbortSignal): Promise<AppStatus> {
  const response = await fetch("/api/app/status", { signal });
  if (!response.ok) throw new Error("Application statistics are temporarily unavailable. Please try again.");
  const value: unknown = await response.json();
  if (!isStatus(value)) throw new Error("Application statistics could not be read. Please try again.");
  return value;
}

function isStatus(value: unknown): value is AppStatus {
  if (typeof value !== "object" || value === null) return false;
  const data = value as Record<string, unknown>;
  const count = (key: string) => typeof data[key] === "number" && Number.isFinite(data[key]) && data[key] >= 0;
  return typeof data.trackingEnabled === "boolean" && typeof data.persistent === "boolean"
    && typeof data.period === "string" && /^\d{4}-\d{2}-\d{2}$/.test(data.period)
    && ["requests", "tokens", "completed", "failed", "disconnected", "activeStreams", "uptimeSeconds"].every(count)
    && ["averageDurationMs", "dailyRequestLimit", "requestsRemaining"].every(key => data[key] === null || count(key));
}

export function formatMetric(value: number | null): string {
  return value === null ? "Unavailable" : value.toLocaleString("en-US");
}

export function formatDuration(milliseconds: number | null): string {
  return milliseconds === null ? "Unavailable" : `${(milliseconds / 1000).toLocaleString("en-US", { maximumFractionDigits: 1 })} s`;
}

export function formatUptime(seconds: number): string {
  if (seconds < 60) return `${Math.floor(seconds)} s`;
  if (seconds < 3600) return `${Math.floor(seconds / 60)} min`;
  const hours = Math.floor(seconds / 3600);
  return hours < 24 ? `${hours} h ${Math.floor(seconds % 3600 / 60)} min` : `${Math.floor(hours / 24)} d ${hours % 24} h`;
}
