import { afterEach, describe, expect, it, vi } from "vitest";

import { copiarTexto } from "./copiar-texto";

afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

describe("copiarTexto", () => {
  it("usa clipboard.writeText quando disponível", async () => {
    const writeText = vi.fn().mockResolvedValue(undefined);
    vi.stubGlobal("navigator", { clipboard: { writeText } });
    await expect(copiarTexto("olá")).resolves.toBe(true);
    expect(writeText).toHaveBeenCalledWith("olá");
  });

  it("informa falha quando clipboard rejeita e o fallback também falha", async () => {
    const writeText = vi.fn().mockRejectedValue(new Error("denied"));
    vi.stubGlobal("navigator", { clipboard: { writeText } });
    await expect(copiarTexto("olá")).resolves.toBe(false);
  });

  it("cai no fallback quando clipboard não existe", async () => {
    vi.stubGlobal("navigator", {});
    Object.defineProperty(document, "execCommand", {
      configurable: true,
      writable: true,
      value: vi.fn().mockReturnValue(true),
    });
    await expect(copiarTexto("cópia")).resolves.toBe(true);
  });
});
