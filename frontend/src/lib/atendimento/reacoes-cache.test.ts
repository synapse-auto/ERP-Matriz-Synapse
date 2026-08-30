import { QueryClient } from "@tanstack/react-query";
import { describe, expect, it } from "vitest";

import type { PaginaChatMensagens } from "@/lib/chat-interno/types";

import type { DadosDoHistorico } from "./cache-mensagens";
import {
  aplicarResumoPublico,
  atualizarReacoesDoChatInterno,
  atualizarReacoesDoHistorico,
  type AlteracaoDeReacao,
  type ResumoReacaoPublico,
} from "./reacoes-cache";
import type { MensagemResposta, ResumoReacao } from "./types";

const USUARIO = "user-mesmo";
const OUTRO = "user-outro";
const CHAVE_HISTORICO = ["mensagens", "a1"] as const;

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

function historico(reacoes: ResumoReacao[]): DadosDoHistorico {
  return {
    pageParams: [null, "c1"],
    pages: [
      { mensagens: [mensagem(reacoes)], proximoCursor: "c1" },
      { mensagens: [], proximoCursor: null },
    ],
  };
}

function paginaChat(reacoes: ResumoReacao[]): PaginaChatMensagens {
  return {
    mensagens: [
      {
        id: "m1",
        conversaId: "c1",
        remetenteId: "u1",
        remetenteNome: "Ana",
        conteudo: "oi",
        enviadoEm: "2026-08-28T15:00:00Z",
        reacoes,
      },
    ],
    proximoCursor: null,
  };
}

type Adaptador = {
  nome: string;
  semear: (cache: QueryClient, reacoes: ResumoReacao[]) => void;
  aplicar: (
    cache: QueryClient,
    publicos: ResumoReacaoPublico[],
    alteracao: AlteracaoDeReacao,
    usuarioAtualId: string,
  ) => void;
  ler: (cache: QueryClient) => ResumoReacao[] | undefined;
};

const adaptadores: Adaptador[] = [
  {
    nome: "atendimento",
    semear(cache, reacoes) {
      cache.setQueryData(CHAVE_HISTORICO, historico(reacoes));
    },
    aplicar(cache, publicos, alteracao, usuarioAtualId) {
      atualizarReacoesDoHistorico(cache, CHAVE_HISTORICO, "m1", publicos, alteracao, usuarioAtualId);
    },
    ler(cache) {
      return cache.getQueryData<DadosDoHistorico>(CHAVE_HISTORICO)?.pages[0].mensagens[0].reacoes;
    },
  },
  {
    nome: "chat interno",
    semear(cache, reacoes) {
      cache.setQueryData(["chat-interno", "mensagens", "c1"], paginaChat(reacoes));
    },
    aplicar(cache, publicos, alteracao, usuarioAtualId) {
      atualizarReacoesDoChatInterno(cache, "c1", "m1", publicos, alteracao, usuarioAtualId);
    },
    ler(cache) {
      return cache.getQueryData<PaginaChatMensagens>(["chat-interno", "mensagens", "c1"])?.mensagens[0]
        .reacoes;
    },
  },
];

describe("aplicarResumoPublico", () => {
  it("substitui quantidades e, se o ator e outro usuario, preserva a reacao propria", () => {
    const atualizado = aplicarResumoPublico(
      [{ emoji: "👍", quantidade: 1, reagi: true }],
      [{ emoji: "👍", quantidade: 2 }],
      { atorId: OUTRO, emojiDoAtor: "👍" },
      USUARIO,
    );
    expect(atualizado).toEqual([{ emoji: "👍", quantidade: 2, reagi: true }]);
  });

  it("evento duplicado nao incrementa de novo", () => {
    const primeiro = aplicarResumoPublico(
      [{ emoji: "👍", quantidade: 1, reagi: false }],
      [{ emoji: "👍", quantidade: 2 }],
      { atorId: OUTRO, emojiDoAtor: "👍" },
      USUARIO,
    );
    const segundo = aplicarResumoPublico(primeiro, [{ emoji: "👍", quantidade: 2 }], {
      atorId: OUTRO,
      emojiDoAtor: "👍",
    }, USUARIO);
    expect(segundo).toEqual([{ emoji: "👍", quantidade: 2, reagi: false }]);
  });
});

describe.each(adaptadores)("$nome — duas abas do mesmo usuario", (adaptador) => {
  it("troca 👍 por ❤️ na outra aba e depois remove a reacao propria", () => {
    const abaQueAgia = new QueryClient();
    const abaQueRecebe = new QueryClient();
    const inicial: ResumoReacao[] = [{ emoji: "👍", quantidade: 1, reagi: true }];
    adaptador.semear(abaQueAgia, inicial);
    adaptador.semear(abaQueRecebe, inicial);

    const troca: AlteracaoDeReacao = { atorId: USUARIO, emojiDoAtor: "❤️" };
    const publicosTroca: ResumoReacaoPublico[] = [{ emoji: "❤️", quantidade: 1 }];
    adaptador.aplicar(abaQueAgia, publicosTroca, troca, USUARIO);
    adaptador.aplicar(abaQueRecebe, publicosTroca, troca, USUARIO);

    for (const aba of [abaQueAgia, abaQueRecebe]) {
      expect(adaptador.ler(aba)).toEqual([{ emoji: "❤️", quantidade: 1, reagi: true }]);
      expect(adaptador.ler(aba)?.filter((item) => item.reagi)).toHaveLength(1);
    }

    const remocao: AlteracaoDeReacao = { atorId: USUARIO, emojiDoAtor: null };
    adaptador.aplicar(abaQueAgia, [], remocao, USUARIO);
    adaptador.aplicar(abaQueRecebe, [], remocao, USUARIO);
    for (const aba of [abaQueAgia, abaQueRecebe]) {
      expect(adaptador.ler(aba)).toEqual([]);
      expect(adaptador.ler(aba)?.some((item) => item.reagi)).toBeFalsy();
    }
  });

  it("evento duplicado nao dobra quantidade", () => {
    const cache = new QueryClient();
    adaptador.semear(cache, [{ emoji: "👍", quantidade: 1, reagi: true }]);
    const evento: AlteracaoDeReacao = { atorId: OUTRO, emojiDoAtor: "👍" };
    const publicos: ResumoReacaoPublico[] = [{ emoji: "👍", quantidade: 2 }];
    adaptador.aplicar(cache, publicos, evento, USUARIO);
    adaptador.aplicar(cache, publicos, evento, USUARIO);
    expect(adaptador.ler(cache)).toEqual([{ emoji: "👍", quantidade: 2, reagi: true }]);
  });

  it("evento de outro usuario preserva a reacao propria do destinatario", () => {
    const cache = new QueryClient();
    adaptador.semear(cache, [{ emoji: "👍", quantidade: 1, reagi: true }]);
    adaptador.aplicar(
      cache,
      [
        { emoji: "👍", quantidade: 1 },
        { emoji: "😂", quantidade: 1 },
      ],
      { atorId: OUTRO, emojiDoAtor: "😂" },
      USUARIO,
    );
    expect(adaptador.ler(cache)).toEqual([
      { emoji: "👍", quantidade: 1, reagi: true },
      { emoji: "😂", quantidade: 1, reagi: false },
    ]);
  });
});
