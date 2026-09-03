import { afterEach, describe, expect, it, vi } from "vitest";
import { fetchStatus, formatDuration, formatMetric, formatUptime } from "./status";
import { routeFromHash } from "./App";

const snapshot = { trackingEnabled: true, persistent: false, period: "2026-09-03", requests: 8, tokens: 900, completed: 5, failed: 1, disconnected: 1, averageDurationMs: 1234, activeStreams: 1, uptimeSeconds: 900, dailyRequestLimit: 100, requestsRemaining: 92 };
afterEach(() => vi.unstubAllGlobals());

describe("application status", () => {
  it("opens the status route without changing chat or FAQ routes", () => {
    expect(routeFromHash("#/status")).toBe("status");
    expect(routeFromHash("#/faq")).toBe("faq");
    expect(routeFromHash("#main")).toBe("chat");
  });
  it("loads the public aggregate snapshot and passes cancellation through", async () => {
    const fetch = vi.fn().mockResolvedValue(new Response(JSON.stringify(snapshot)));
    vi.stubGlobal("fetch", fetch);
    const signal = new AbortController().signal;
    expect(await fetchStatus(signal)).toEqual(snapshot);
    expect(fetch).toHaveBeenCalledWith("/api/app/status", { signal });
  });
  it("accepts disabled tracking and unavailable averages or limits", async () => {
    const disabled = { ...snapshot, trackingEnabled: false, averageDurationMs: null, dailyRequestLimit: null, requestsRemaining: null };
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response(JSON.stringify(disabled))));
    expect(await fetchStatus()).toEqual(disabled);
  });
  it("rejects malformed statistics rather than displaying misleading totals", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response(JSON.stringify({ ...snapshot, requests: "8" }))));
    await expect(fetchStatus()).rejects.toThrow("could not be read");
  });
  it("uses a public error when the endpoint fails", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response("private details", { status: 503 })));
    await expect(fetchStatus()).rejects.toThrow("temporarily unavailable");
  });
  it("distinguishes unavailable measurements from measured zero", () => {
    expect(formatMetric(null)).toBe("Unavailable");
    expect(formatMetric(0)).toBe("0");
    expect(formatMetric(12345)).toBe("12,345");
    expect(formatDuration(null)).toBe("Unavailable");
    expect(formatDuration(0)).toBe("0 s");
    expect(formatDuration(1250)).toBe("1.3 s");
  });
  it("keeps instance uptime readable across minutes, hours and days", () => {
    expect(formatUptime(59)).toBe("59 s");
    expect(formatUptime(120)).toBe("2 min");
    expect(formatUptime(3660)).toBe("1 h 1 min");
    expect(formatUptime(90000)).toBe("1 d 1 h");
  });
});
