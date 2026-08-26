import { describe, expect, it } from "vitest";
import { HISTORY_KEY, readHistory, validateHistory } from "./history";

describe("conversation history", () => {
  it("accepts valid messages and discards invalid entries", () => {
    expect(validateHistory([
      { role: "USER", content: "Hello" },
      { role: "SYSTEM", content: "hidden" },
      { role: "ASSISTANT", content: "Hi" },
      null,
    ])).toEqual([
      { role: "USER", content: "Hello" },
      { role: "ASSISTANT", content: "Hi" },
    ]);
  });

  it("protects startup from malformed JSON", () => {
    const storage = { getItem: (key: string) => key === HISTORY_KEY ? "{" : null };
    expect(readHistory(storage)).toEqual([]);
  });
});
