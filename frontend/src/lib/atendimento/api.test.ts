import { describe, expect, it, vi } from "vitest";

vi.mock("@/lib/api/http-client", () => ({ apiFetch: vi.fn() }));

import { apiFetch } from "@/lib/api/http-client";

import { listarInboxUnificada } from "./api";

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
