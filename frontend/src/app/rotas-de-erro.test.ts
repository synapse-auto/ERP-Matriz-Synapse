import { existsSync, readFileSync } from "node:fs";
import { resolve } from "node:path";
import { describe, expect, it } from "vitest";

describe("descoberta nativa de rota inexistente", () => {
  it("não aceita qualquer URL como placeholder 200; mantém só as rotas planejadas", () => {
    const raiz = resolve(process.cwd(), "src/app");
    expect(existsSync(resolve(raiz, "(shell)/[...slug]/page.tsx"))).toBe(false);
    for (const rota of ["banco-arquivos", "horarios", "relatorios"]) {
      expect(readFileSync(resolve(raiz, `(shell)/${rota}/page.tsx`), "utf8")).toContain("<Placeholder />");
    }
    expect(readFileSync(resolve(process.cwd(), "next.config.ts"), "utf8")).toContain("globalNotFound: true");
  });
});
