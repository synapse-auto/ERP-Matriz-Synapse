import { readFileSync } from "node:fs";
import { resolve } from "node:path";

import { describe, expect, it } from "vitest";

describe("tokens visuais do login", () => {
  it("aplica o raio medido do protótipo aos campos e ao botão", () => {
    const css = readFileSync(resolve(process.cwd(), "src/app/identidade-synapse.css"), "utf8");

    expect(css).toMatch(/--raio-login:\s*10px/);
    expect(css).toMatch(/\.synapse-login__input\s*\{[\s\S]*?border-radius:\s*var\(--raio-login\)/);
    expect(css).toMatch(/\.synapse-login__botao\s*\{[\s\S]*?border-radius:\s*var\(--raio-login\)/);
  });
});
