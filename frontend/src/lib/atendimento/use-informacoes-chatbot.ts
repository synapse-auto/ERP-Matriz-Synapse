"use client";

import { useEffect, useMemo } from "react";

import { useInfiniteQuery, useQueryClient } from "@tanstack/react-query";

import { useFuncionalidadesHabilitadas } from "@/lib/config/use-funcionalidades";

import { paginaInformacoesDoChatbot } from "./api";
import type { EstadoConexao } from "./tempo-real";
import type { CartaoInformacoesChatbot } from "./types";

/** Chave da feature flag por instância: o recurso é capacidade, nunca nome de cliente. */
export const FUNCIONALIDADE_INFORMACOES_CHATBOT = "informacoes_chatbot_historico";

const SEM_CARTOES: ReadonlyArray<CartaoInformacoesChatbot> = [];

/** Prefixo comum a todas as janelas da conversa: é o que o evento de tempo real invalida. */
export function chaveInformacoesChatbot(atendimentoId: string | null) {
  return ["informacoes-chatbot", atendimentoId] as const;
}

/**
 * Trecho do histórico de mensagens já carregado. `desde` é o instante da mensagem mais antiga que a
 * tela tem — os cards só interessam a partir dali — e `null` quando todo o histórico já foi
 * carregado. A tela ainda sem mensagens carregadas passa `null` no lugar da janela (não pronta).
 */
export interface JanelaDoHistorico {
  desde: string | null;
}

/**
 * Cards do chatbot do atendimento aberto, integrados à paginação do histórico: acompanham a janela de
 * mensagens já carregada (`desde`) e vêm em páginas por cursor, da mais recente para a mais antiga,
 * então nenhum card some e nenhuma consulta traz a conversa inteira. Ao carregar mensagens mais
 * antigas a janela cresce e os cards desse trecho entram.
 *
 * Sem polling. Cada card novo chega pelo evento `INFORMACOES_CHATBOT` (que invalida o prefixo da
 * chave) e a consulta é revalidada uma vez a cada conexão do WebSocket (a primeira e as reconexões)
 * para recuperar o que passou antes de o canal estar assinado. Com a flag desligada nenhuma
 * requisição é feita.
 */
export function useInformacoesDoChatbot(
  atendimentoId: string | null,
  estadoConexao: EstadoConexao,
  janela: JanelaDoHistorico | null,
): ReadonlyArray<CartaoInformacoesChatbot> {
  const queryClient = useQueryClient();
  const { data: funcionalidades } = useFuncionalidadesHabilitadas();
  const ativa =
    atendimentoId != null
    && janela != null
    && (funcionalidades?.includes(FUNCIONALIDADE_INFORMACOES_CHATBOT) ?? false);
  const desde = janela?.desde ?? null;

  const consulta = useInfiniteQuery({
    queryKey: [...chaveInformacoesChatbot(atendimentoId), desde ?? "tudo"],
    queryFn: ({ pageParam }) => paginaInformacoesDoChatbot(atendimentoId as string, pageParam, desde),
    initialPageParam: null as string | null,
    getNextPageParam: (ultima) => ultima.proximoCursor ?? undefined,
    enabled: ativa,
    // Ao crescer a janela a chave muda; manter os cards já vistos (da MESMA conversa) evita piscar.
    placeholderData: (anterior, consultaAnterior) =>
      consultaAnterior?.queryKey[1] === atendimentoId ? anterior : undefined,
  });

  // Páginas dentro da janela, uma por vez: cada requisição é limitada pelo tamanho de página do servidor.
  // `paginasCarregadas` entra nas dependências de propósito: com resposta rápida o React agrupa
  // "buscando → ocioso" numa renderização só e, sem ele, o efeito não reexecutaria e a janela
  // ficaria pela metade.
  const { hasNextPage, isFetchingNextPage, fetchNextPage } = consulta;
  const paginasCarregadas = consulta.data?.pages.length ?? 0;
  useEffect(() => {
    if (ativa && hasNextPage && !isFetchingNextPage) void fetchNextPage();
  }, [ativa, hasNextPage, isFetchingNextPage, paginasCarregadas, fetchNextPage]);

  useEffect(() => {
    if (estadoConexao !== "conectado" || !ativa) return;
    // Backfill de toda conexão, a primeira inclusive: um card criado entre a carga da conversa e a
    // assinatura do canal não gera evento para esta aba, e nada mais o buscaria.
    void queryClient.invalidateQueries({ queryKey: chaveInformacoesChatbot(atendimentoId) });
  }, [estadoConexao, ativa, atendimentoId, queryClient]);

  // Cada página sai em ordem cronológica e a mais antiga vem por último: inverte a lista de páginas.
  const cartoes = useMemo(
    () => (consulta.data ? [...consulta.data.pages].reverse().flatMap((pagina) => pagina.itens) : SEM_CARTOES),
    [consulta.data],
  );
  return ativa ? cartoes : SEM_CARTOES;
}
