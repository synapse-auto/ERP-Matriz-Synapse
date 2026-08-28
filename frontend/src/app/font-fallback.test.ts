import { readFileSync } from "node:fs";
import { resolve } from "node:path";

import { describe, expect, it } from "vitest";

describe("tipografia global", () => {
  const css = readFileSync(resolve(process.cwd(), "src/app/globals.css"), "utf8");

  it("resolve font-sans na Inter mesmo sem tema da instância", () => {
    expect(css).toMatch(/--font-sans:\s*var\(--fonte-base-carregada\),\s*var\(--fonte-base\)/);
    expect(css).toMatch(/--fonte-base:\s*"Inter",\s*sans-serif/);
  });

  it("carrega Inter via next/font/google (arquivos self-hosted no build) e compartilha a família com títulos", () => {
    const layout = readFileSync(resolve(process.cwd(), "src/app/layout.tsx"), "utf8");

    expect(layout).toContain('from "next/font/google"');
    expect(layout).toContain("const inter = Inter(");
    expect(layout).toContain('variable: "--fonte-base-carregada"');
    expect(css).toMatch(/--font-heading:\s*var\(--fonte-base-carregada\),\s*var\(--fonte-base\)/);
  });
});
