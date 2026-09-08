import { beforeEach, describe, expect, it, vi } from "vitest";

const { buscarTextosMock, buscarTemaMock } = vi.hoisted(() => ({
  buscarTextosMock: vi.fn(),
  buscarTemaMock: vi.fn(),
}));

vi.mock("@/lib/config/fetch-config", () => ({
  buscarTextos: buscarTextosMock,
  buscarTema: buscarTemaMock,
}));

import { GET } from "./route";

describe("GET /health/readiness", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    buscarTextosMock.mockResolvedValue({});
    buscarTemaMock.mockResolvedValue({});
  });

  it("responde 200 somente quando textos e tema estao disponiveis", async () => {
    const resposta = await GET();

    expect(resposta.status).toBe(200);
    await expect(resposta.json()).resolves.toEqual({ status: "UP" });
    expect(buscarTextosMock).toHaveBeenCalledOnce();
    expect(buscarTemaMock).toHaveBeenCalledOnce();
  });

  it.each(["textos", "tema"])("responde 503 sem expor a falha de %s", async (configuracao) => {
    const erroOriginal = new Error(`segredo interno de ${configuracao}`);
    if (configuracao === "textos") {
      buscarTextosMock.mockRejectedValueOnce(erroOriginal);
    } else {
      buscarTemaMock.mockRejectedValueOnce(erroOriginal);
    }

    const resposta = await GET();

    expect(resposta.status).toBe(503);
    await expect(resposta.text()).resolves.toBe('{"status":"DOWN"}');
  });
});
