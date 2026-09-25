import { describe, expect, it, vi } from "vitest";

vi.mock("@/lib/api/http-client", () => ({ apiFetch: vi.fn() }));

import { apiFetch } from "@/lib/api/http-client";

import { ErroDeApi } from "@/lib/api/errors";

import { buscarAtendimentoPorTelefone, enviarTemplate, listarInboxUnificada } from "./api";

describe("listarInboxUnificada — contrato da primeira versão da E63", () => {
  it("normaliza nome genérico do cliente sem derrubar a tela durante atualização gradual", async () => {
    vi.mocked(apiFetch).mockResolvedValue({
      itens: [
        {
          tipo: "CLIENTE",
          atendimentoId: "atendimento-1",
          conversaId: null,
          nome: "Cliente da API",
          avatarUrl: null,
          identificadorVisual: "atendimento-1",
          ultimaMensagemPreview: "Olá",
          ultimaMensagemEm: "2026-08-26T21:00:00Z",
          naoLidas: 1,
          leadId: "lead-1",
          leadEmpresa: null,
          canalTipo: "WHATSAPP",
          status: "EM_ATENDIMENTO",
          etapaId: null,
          etapaNome: null,
          etapaCor: null,
          atendenteId: null,
          atendenteNome: null,
        },
      ],
      proximoCursor: null,
    });

    const pagina = await listarInboxUnificada("TODOS");
    const cliente = pagina.itens[0];

    expect(cliente?.tipo).toBe("CLIENTE");
    if (!cliente || cliente.tipo === "EQUIPE_INTERNA") throw new Error("cliente ausente");
    expect(cliente.leadNome).toBe("Cliente da API");
    expect(cliente.leadFotoUrl).toBeNull();
    expect(cliente.atendimentoAtivoId).toBe("atendimento-1");
  });

  it("envia atendenteId somente ao consultar finalizados", async () => {
    vi.mocked(apiFetch).mockResolvedValue({ itens: [], proximoCursor: null });

    await listarInboxUnificada("FINALIZADOS", null, 50, "atendente-1");

    expect(apiFetch).toHaveBeenLastCalledWith(
      "/api/v1/atendimentos/inbox?visao=FINALIZADOS&limite=50&atendenteId=atendente-1",
    );
  });
});

describe("enviarTemplate — corpo renderizado opcional", () => {
  it("inclui o corpo renderizado no JSON sem alterar a chave de idempotência", async () => {
    vi.mocked(apiFetch).mockResolvedValue({});

    await enviarTemplate(
      "atendimento-1",
      "lead-1",
      "boas_vindas",
      "pt_BR",
      ["Maria"],
      "chave-1",
      "Olá Maria",
    );

    expect(apiFetch).toHaveBeenLastCalledWith(
      "/api/v1/atendimentos/mensagens/template",
      {
        method: "POST",
        headers: { "Idempotency-Key": "chave-1" },
        body: JSON.stringify({
          atendimentoId: "atendimento-1",
          leadId: "lead-1",
          nome: "boas_vindas",
          idioma: "pt_BR",
          parametros: ["Maria"],
          corpoRenderizado: "Olá Maria",
        }),
      },
    );
  });
});

describe("buscarAtendimentoPorTelefone (E211)", () => {
  it("usa a busca pontual com o telefone codificado e devolve o cartão autorizado", async () => {
    vi.mocked(apiFetch).mockResolvedValue({ atendimentoId: "a-1", leadId: "l-1" });

    await expect(buscarAtendimentoPorTelefone("+55 61 98888-0000")).resolves.toEqual({ atendimentoId: "a-1", leadId: "l-1" });
    expect(apiFetch).toHaveBeenLastCalledWith("/api/v1/atendimentos/busca?telefone=%2B55%2061%2098888-0000");
  });

  it("404 (inexistente ou fora do alcance da sessão) vira ausência, sem distinguir os dois", async () => {
    vi.mocked(apiFetch).mockRejectedValue(new ErroDeApi(404, null, "não encontrado"));

    await expect(buscarAtendimentoPorTelefone("5561988880000")).resolves.toBeNull();
  });

  it("outras falhas propagam para a tela mostrar erro", async () => {
    vi.mocked(apiFetch).mockRejectedValue(new ErroDeApi(500, null, "erro"));
    await expect(buscarAtendimentoPorTelefone("5561988880000")).rejects.toBeInstanceOf(ErroDeApi);

    vi.mocked(apiFetch).mockRejectedValue(new TypeError("Failed to fetch"));
    await expect(buscarAtendimentoPorTelefone("5561988880000")).rejects.toBeInstanceOf(TypeError);
  });
});
