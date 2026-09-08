"use client";

import { useInfiniteQuery, useQuery } from "@tanstack/react-query";

import { emitirUrlAssinadaDaMidiaChat, listarMidiasDoGrupoChat } from "./api";

const TAMANHO_PAGINA = 20;

export function useMidiasDoGrupo(conversaId: string) {
  return useInfiniteQuery({
    queryKey: ["chat-interno", "midias", conversaId],
    queryFn: ({ pageParam }) => listarMidiasDoGrupoChat(conversaId, pageParam, TAMANHO_PAGINA),
    initialPageParam: 0,
    getNextPageParam: (ultima, _paginas, paginaAtual) =>
      ultima.length === TAMANHO_PAGINA ? paginaAtual + 1 : undefined,
  });
}

export function useUrlAssinadaDaMidiaGrupo(conversaId: string, mensagemId: string, habilitada: boolean) {
  return useQuery({
    queryKey: ["chat-interno", "midia-url", conversaId, mensagemId],
    queryFn: () => emitirUrlAssinadaDaMidiaChat(conversaId, mensagemId),
    enabled: habilitada,
    staleTime: 2 * 60 * 1000,
  });
}
