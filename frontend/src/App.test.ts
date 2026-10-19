import { describe, expect, it } from "vitest";
import { canAnswer, routeFromHash } from "./App";
describe("routeFromHash", () => { it("opens the FAQ and its deep-linked sections", () => { expect(routeFromHash("#/faq")).toBe("faq"); expect(routeFromHash("#/faq/docker")).toBe("faq"); expect(routeFromHash("#main")).toBe("chat"); expect(routeFromHash("#about")).toBe("chat"); expect(routeFromHash("")).toBe("chat"); }); });
describe("canAnswer", () => {
  it("needs a provider outside agent mode", () => {
    expect(canAnswer("", false)).toBe(false);
    expect(canAnswer("OPENAI", false)).toBe(true);
  });
  it("does not need a provider in agent mode", () => {
    expect(canAnswer("", true)).toBe(true);
  });
});
