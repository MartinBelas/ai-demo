import { describe, expect, it } from "vitest";
import { HISTORY_KEY, readHistory, removeIncompleteTurn, validateHistory } from "./history";

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

  it("removes an interrupted turn before it is reused as context", () => {
    expect(removeIncompleteTurn([
      { role: "USER", content: "Completed question" },
      { role: "ASSISTANT", content: "Completed answer" },
      { role: "USER", content: "Interrupted question" },
    ])).toEqual([
      { role: "USER", content: "Completed question" },
      { role: "ASSISTANT", content: "Completed answer" },
    ]);
  });

  it("does not restore an interrupted turn from browser storage", () => {
    const storage = {
      getItem: () => JSON.stringify([
        { role: "USER", content: "Completed question" },
        { role: "ASSISTANT", content: "Completed answer" },
        { role: "USER", content: "Interrupted question" },
      ]),
    };

    expect(readHistory(storage)).toEqual([
      { role: "USER", content: "Completed question" },
      { role: "ASSISTANT", content: "Completed answer" },
    ]);
  });
});
