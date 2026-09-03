import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

import type { MensagemResposta } from "@/lib/atendimento/types";

let opcoesDoVirtualizador: Record<string, unknown> | null = null;

vi.mock("@tanstack/react-virtual", () => ({
  useVirtualizer: (opcoes: Record<string, unknown>) => {
    opcoesDoVirtualizador = opcoes;
    return {
      getTotalSize: () => 120,
      getVirtualItems: () => [
        { index: 0, start: 16 },
        { index: 1, start: 72 },
      ],
      measureElement: vi.fn(),
      scrollToIndex: vi.fn(),
    };
  },
}));

vi.mock("@/lib/config/textos-provider", () => ({
  useTextos: () => ({
    estados: { vazio: "Sem mensagens" },
    atendimentos: {
      filtros: { busca: "Buscar" },
      mensagem: {
        carregarAnteriores: "Carregar anteriores",
        carregandoAnteriores: "Carregando",
        ia: "IA",
        hoje: "Hoje",
        ontem: "Ontem",
        trocaAtendimento: "Atendimento transferido",
        atendimentoRecebido: "Atendimento recebido",
        responsavelAtual: "Responsável: {nome}",
      },
      canais: { whatsapp: "WhatsApp" },
    },
  }),
}));

vi.mock("./bolha-mensagem", () => ({
  BolhaMensagem: ({ mensagem }: { mensagem: MensagemResposta }) => (
    <div data-slot="bolha-mensagem">{mensagem.conteudo}</div>
  ),
}));

import {
  ESPACAMENTO_DE_SEGURANCA_DO_HISTORICO,
  ListaMensagens,
} from "./lista-mensagens";

function mensagem(id: string, conteudo: string): MensagemResposta {
  return {
    id,
    remetenteTipo: "LEAD",
    remetenteId: null,
    remetenteNome: null,
    tipo: "TEXTO",
    conteudo,
    midiaUrl: null,
    midiaMetadados: null,
    opcoes: null,
    statusEntrega: "ENTREGUE",
    enviadoEm: "2026-08-28T12:00:00Z",
  };
}

describe("ListaMensagens: área útil do histórico", () => {
  it("reserva espaço no virtualizador antes da primeira e depois da última mensagem", () => {
    render(
      <ListaMensagens
        mensagens={[mensagem("curta", "Oi"), mensagem("longa", "Mensagem longa para testar a última posição no histórico.")]}
        carregando={false}
        onReenviar={vi.fn()}
        onDefinirReacao={vi.fn().mockResolvedValue(undefined)}
        onRemoverReacao={vi.fn().mockResolvedValue(undefined)}
        temMais={false}
        carregandoMais={false}
        onCarregarMais={vi.fn()}
        buscaAberta={false}
        canalTipo="WHATSAPP"
        atendenteId={null}
        atendenteNome={null}
      />,
    );

    expect(opcoesDoVirtualizador).toMatchObject({
      paddingStart: ESPACAMENTO_DE_SEGURANCA_DO_HISTORICO,
      paddingEnd: ESPACAMENTO_DE_SEGURANCA_DO_HISTORICO,
      scrollPaddingStart: ESPACAMENTO_DE_SEGURANCA_DO_HISTORICO,
      scrollPaddingEnd: ESPACAMENTO_DE_SEGURANCA_DO_HISTORICO,
    });
    expect(screen.getByText("Oi").closest("[data-index='0']")).toHaveStyle({
      transform: `translateY(${ESPACAMENTO_DE_SEGURANCA_DO_HISTORICO}px)`,
    });
    expect(document.querySelector('[data-slot="historico-mensagens"]')).toHaveClass(
      "min-h-0",
      "flex-1",
      "overscroll-contain",
      "scroll-py-4",
    );
    expect(document.querySelector('[data-slot="historico-mensagens"]')).toHaveStyle({
      paddingBottom: "calc(0.5rem + var(--altura-composer, 0px))",
      scrollPaddingBottom: "calc(1rem + var(--altura-composer, 0px))",
    });
  });
});
