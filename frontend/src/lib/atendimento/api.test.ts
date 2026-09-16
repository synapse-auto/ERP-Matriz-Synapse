import { describe, expect, it, vi } from "vitest";

vi.mock("@/lib/api/http-client", () => ({ apiFetch: vi.fn() }));

import { apiFetch } from "@/lib/api/http-client";

import { enviarTemplate, listarInboxUnificada } from "./api";

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
