import { readFileSync } from "node:fs";
import { createRequire } from "node:module";
import { describe, expect, it } from "vitest";

const require = createRequire(import.meta.url);

describe("compatibilidade do seletor de emoji com React 19", () => {
  it("falha se voltar @emoji-mart/react ou override de peer incompatível", () => {
    const pkg = JSON.parse(readFileSync("package.json", "utf8")) as {
      dependencies: Record<string, string>;
      devDependencies?: Record<string, string>;
      overrides?: unknown;
    };
    const lock = JSON.parse(readFileSync("package-lock.json", "utf8")) as {
      packages?: Record<string, unknown>;
      overrides?: unknown;
    };

    expect(pkg.overrides).toBeUndefined();
    expect(pkg.dependencies["@emoji-mart/react"]).toBeUndefined();
    expect(pkg.devDependencies?.["@emoji-mart/react"]).toBeUndefined();
    expect(lock.packages?.["node_modules/@emoji-mart/react"]).toBeUndefined();
    expect(JSON.stringify(lock.overrides ?? {})).not.toMatch(/@emoji-mart\/react/);
    expect(pkg.dependencies.react).toMatch(/^19\./);
    expect(pkg.dependencies["emoji-mart"]).toBe("5.6.0");
    expect(pkg.dependencies["@emoji-mart/data"]).toBe("1.2.1");
  });

  it("emoji-mart não declara peer de React", () => {
    const emojiMart = require("emoji-mart/package.json") as {
      peerDependencies?: { react?: string };
    };
    expect(emojiMart.peerDependencies?.react).toBeUndefined();
  });

  it("o adapter monta o Web Component sem importar o wrapper React", () => {
    const fonte = readFileSync("src/components/mensagens/seletor-emoji-completo.tsx", "utf8");
    expect(fonte).not.toContain("@emoji-mart/react");
    expect(fonte).toContain('from "emoji-mart"');
    expect(fonte).toContain("new Picker");
  });
});
