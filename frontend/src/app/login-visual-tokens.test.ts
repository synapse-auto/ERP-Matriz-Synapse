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

  it("empilha o herói e o cartão no celular em vez de esconder a apresentação", () => {
    const css = readFileSync(resolve(process.cwd(), "src/app/identidade-synapse.css"), "utf8");
    const mobile = css.split("@media (max-width: 880px)")[1] ?? "";

    expect(mobile).not.toMatch(/\.synapse-login__apresentacao\s*\{[^}]*display:\s*none/);
    expect(mobile).toMatch(/margin-top:\s*-52px/);
    expect(mobile).toMatch(/\.synapse-login__destaques--cartao\s*\{[\s\S]*?display:\s*grid/);
  });

  it("permite rolagem vertical no login porque o body recorta overflow", () => {
    const css = readFileSync(resolve(process.cwd(), "src/app/identidade-synapse.css"), "utf8");
    const base = css.split("@media")[0] ?? "";
    const mobile = css.split("@media (max-width: 880px)")[1] ?? "";

    expect(base).toMatch(/\.synapse-login\s*\{[\s\S]*?overflow-y:\s*auto/);
    expect(base).toMatch(/\.synapse-login\s*\{[\s\S]*?min-height:\s*0/);
    expect(mobile).not.toMatch(/min-height:\s*100dvh/);
  });
});
