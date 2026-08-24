import { readFileSync } from "node:fs";
import { resolve } from "node:path";

import { describe, expect, it } from "vitest";

describe("fallback tipográfico do login", () => {
  it("resolve font-sans na fonte local mesmo sem tema da instância", () => {
    const css = readFileSync(resolve(process.cwd(), "src/app/globals.css"), "utf8");

    expect(css).toMatch(/--font-sans:\s*var\(--fonte-base-carregada\),\s*var\(--fonte-base\)/);
    expect(css).toMatch(/--fonte-base:\s*"Hanken Grotesk",\s*system-ui,\s*sans-serif/);
  });
});
