import { describe, expect, it } from "vitest";
import { parseSse } from "./api";

describe("SSE parser", () => {
  it("parses events split across network chunks", async () => {
    const encoder = new TextEncoder();
    const stream = new ReadableStream<Uint8Array>({
      start(controller) {
        controller.enqueue(encoder.encode("event: thinking\ndata: {\"content\":\"Plan"));
        controller.enqueue(encoder.encode("ning\"}\n\nevent: content\ndata: {\"content\":\"Hello\"}\n\n"));
        controller.close();
      },
    });
    const events: unknown[] = [];
    await parseSse(stream, (event) => events.push(event));
    expect(events).toEqual([
      { type: "thinking", content: "Planning" },
      { type: "content", content: "Hello" },
    ]);
  });

  it("parses a completion payload", async () => {
    const payload = { model: "qwen3:4b", tokenUsage: { promptTokens: 3, completionTokens: 4, totalTokens: 7 }, durationMs: 1200 };
    const stream = new Blob([`event: completion\ndata: ${JSON.stringify(payload)}\n\n`]).stream();
    const events: unknown[] = [];
    await parseSse(stream, (event) => events.push(event));
    expect(events).toEqual([{ type: "completion", completion: payload }]);
  });

  it("parses running and completed tool activity", async () => {
    const stream = new Blob([
      "event: tool\ndata: {\"name\":\"calculator\",\"status\":\"RUNNING\"}\n\n"
        + "event: tool\ndata: {\"name\":\"calculator\",\"status\":\"COMPLETED\"}\n\n",
    ]).stream();
    const events: unknown[] = [];
    await parseSse(stream, (event) => events.push(event));
    expect(events).toEqual([
      { type: "tool", tool: { name: "calculator", status: "RUNNING" } },
      { type: "tool", tool: { name: "calculator", status: "COMPLETED" } },
    ]);
  });

  it("ignores malformed tool events", async () => {
    const stream = new Blob([
      "event: tool\ndata: {\"name\":\"\",\"status\":\"RUNNING\"}\n\n"
        + "event: tool\ndata: {\"name\":\"calculator\",\"status\":\"FAILED\"}\n\n"
        + "event: tool\ndata: {\"name\":42,\"status\":\"COMPLETED\"}\n\n",
    ]).stream();
    const events: unknown[] = [];
    await parseSse(stream, (event) => events.push(event));
    expect(events).toEqual([]);
  });

  it("ignores malformed typed events", async () => {
    const stream = new Blob([
      "event: content\ndata: {\"content\":42}\n\n"
        + "event: completion\ndata: {\"model\":\"qwen3:4b\"}\n\n",
    ]).stream();
    const events: unknown[] = [];
    await parseSse(stream, (event) => events.push(event));
    expect(events).toEqual([]);
  });

  it("handles a CRLF delimiter split across network chunks", async () => {
    const encoder = new TextEncoder();
    const stream = new ReadableStream<Uint8Array>({
      start(controller) {
        controller.enqueue(encoder.encode("event: content\r\ndata: {\"content\":\"Hello\"}\r"));
        controller.enqueue(encoder.encode("\n\r\n"));
        controller.close();
      },
    });
    const events: unknown[] = [];
    await parseSse(stream, (event) => events.push(event));
    expect(events).toEqual([{ type: "content", content: "Hello" }]);
  });
});
