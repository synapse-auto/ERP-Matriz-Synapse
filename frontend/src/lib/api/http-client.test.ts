import { afterEach, describe, expect, it, vi } from "vitest";

vi.mock("@/lib/auth/auth-store", () => ({
  useAuthStore: { getState: () => ({ accessToken: "token", limparSessao: vi.fn() }) },
}));

import { apiFetch } from "./http-client";

describe("apiFetch", () => {
  afterEach(() => vi.unstubAllGlobals());

  it("aceita 200 sem corpo como sucesso sem conteúdo (aprovar pedido, Optional vazio)", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response(null, { status: 200 })));

    await expect(apiFetch("/api/v1/atendimentos/pedidos-entrada/p-1/aprovar", { method: "POST" }))
      .resolves.toBeNull();
  });

  it("continua devolvendo o JSON quando há corpo", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response(JSON.stringify({ ok: 1 }), { status: 200 })));

    await expect(apiFetch<{ ok: number }>("/api/v1/qualquer")).resolves.toEqual({ ok: 1 });
  });
});
