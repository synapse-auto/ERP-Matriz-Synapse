import { beforeEach, describe, expect, it, vi } from "vitest";

const { apiFetch } = vi.hoisted(() => ({ apiFetch: vi.fn() }));

vi.mock("@/lib/api/http-client", () => ({ apiFetch }));

import { listarMidiasDoLead } from "./api";

describe("contrato da listagem de mídias do lead", () => {
  beforeEach(() => apiFetch.mockReset().mockResolvedValue([]));

  it("preserva a rota sem filtro para clientes existentes", async () => {
    await listarMidiasDoLead("lead-1", 1, 20);
    expect(apiFetch).toHaveBeenCalledWith("/api/v1/leads/lead-1/midias?pagina=1&tamanho=20");
  });

  it("envia filtro explícito antes da paginação da ficha", async () => {
    await listarMidiasDoLead("lead-1", 0, 20, ["IMAGEM", "VIDEO", "DOCUMENTO"]);
    expect(apiFetch).toHaveBeenCalledWith(
      "/api/v1/leads/lead-1/midias?pagina=0&tamanho=20&tipos=IMAGEM%2CVIDEO%2CDOCUMENTO",
    );
  });
});
