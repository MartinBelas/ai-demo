import { describe, expect, it } from "vitest";
import { routeFromHash } from "./App";
describe("routeFromHash", () => { it("opens the FAQ and its deep-linked sections", () => { expect(routeFromHash("#/faq")).toBe("faq"); expect(routeFromHash("#/faq/docker")).toBe("faq"); expect(routeFromHash("#main")).toBe("chat"); expect(routeFromHash("#about")).toBe("chat"); expect(routeFromHash("")).toBe("chat"); }); });
