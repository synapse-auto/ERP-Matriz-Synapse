import { QueryClient } from "@tanstack/react-query";
import { describe, expect, it } from "vitest";

import { aplicarResumoPublico, atualizarReacoesDoHistorico } from "./reacoes-cache";
import type { DadosDoHistorico } from "./cache-mensagens";
import type { MensagemResposta } from "./types";

function mensagem(reacoes: MensagemResposta["reacoes"]): MensagemResposta {
  return {
    id: "m1",
    remetenteTipo: "ATENDENTE",
    remetenteId: "u1",
    remetenteNome: "Ana",
    tipo: "TEXTO",
    conteudo: "oi",
    midiaUrl: null,
    midiaMetadados: null,
    opcoes: null,
    statusEntrega: "ENTREGUE",
    enviadoEm: "2026-08-28T15:00:00Z",
    reacoes,
  };
}

describe("aplicarResumoPublico", () => {
  it("substitui quantidades e preserva a reacao propria", () => {
    const atualizado = aplicarResumoPublico(
      [{ emoji: "👍", quantidade: 1, reagi: true }],
      [{ emoji: "👍", quantidade: 2 }],
    );
    expect(atualizado).toEqual([{ emoji: "👍", quantidade: 2, reagi: true }]);
  });

  it("evento duplicado nao incrementa de novo", () => {
    const primeiro = aplicarResumoPublico(
      [{ emoji: "👍", quantidade: 1, reagi: false }],
      [{ emoji: "👍", quantidade: 2 }],
    );
    const segundo = aplicarResumoPublico(primeiro, [{ emoji: "👍", quantidade: 2 }]);
    expect(segundo).toEqual([{ emoji: "👍", quantidade: 2, reagi: false }]);
  });
});

describe("atualizarReacoesDoHistorico", () => {
  it("atualiza a bolha em qualquer pagina sem somar duas vezes", () => {
    const cache = new QueryClient();
    const chave = ["mensagens", "a1"] as const;
    const historico: DadosDoHistorico = {
      pageParams: [null, "c1"],
      pages: [
        { mensagens: [mensagem([{ emoji: "👍", quantidade: 1, reagi: true }])], proximoCursor: "c1" },
        { mensagens: [], proximoCursor: null },
      ],
    };
    cache.setQueryData(chave, historico);
    atualizarReacoesDoHistorico(cache, chave, "m1", [{ emoji: "👍", quantidade: 2 }]);
    atualizarReacoesDoHistorico(cache, chave, "m1", [{ emoji: "👍", quantidade: 2 }]);
    const depois = cache.getQueryData<DadosDoHistorico>(chave);
    expect(depois?.pages[0].mensagens[0].reacoes).toEqual([{ emoji: "👍", quantidade: 2, reagi: true }]);
  });
});
