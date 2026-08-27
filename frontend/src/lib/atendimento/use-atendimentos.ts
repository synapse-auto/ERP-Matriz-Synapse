"use client";

import { useInfiniteQuery, useQuery } from "@tanstack/react-query";
import { useMemo } from "react";

import { contarAtendimentosPorVisao, listarAtendimentos, listarInboxUnificada } from "./api";
import type { ItemInbox, VisaoAtendimento } from "./types";

const INTERVALO_DE_REVALIDACAO_MS = Number(
  process.env.NEXT_PUBLIC_ATENDIMENTOS_POLLING_MS ?? "10000",
);

export function useAtendimentos(visao: VisaoAtendimento) {
  const inbox = useInfiniteQuery({
    queryKey: ["atendimentos", visao],
    enabled: visao === "TODOS",
    initialPageParam: null as string | null,
    queryFn: ({ pageParam }) => listarInboxUnificada(visao, pageParam),
    getNextPageParam: (ultima) => ultima.proximoCursor ?? undefined,
    // Também detecta a abertura de um novo atendimento pela IA quando a conversa estava sem socket.
    refetchInterval: INTERVALO_DE_REVALIDACAO_MS,
  });
  const paginas = inbox.data?.pages;
  const itensInbox = useMemo(() => paginas?.flatMap((pagina) => pagina.itens), [paginas]);
  const legado = useQuery({
    queryKey: ["atendimentos", visao],
    enabled: visao !== "TODOS",
    queryFn: () => listarAtendimentos(visao),
    refetchInterval: INTERVALO_DE_REVALIDACAO_MS,
  });
  if (visao === "TODOS") {
    return {
      ...inbox,
      data: itensInbox,
      hasNextPage: inbox.hasNextPage,
      isFetchingNextPage: inbox.isFetchingNextPage,
      fetchNextPage: inbox.fetchNextPage,
    };
  }
  return {
    ...legado,
    data: legado.data as ItemInbox[] | undefined,
    hasNextPage: false,
    isFetchingNextPage: false,
    fetchNextPage: async () => undefined,
  };
}

/** Os badges das abas (E17b §Bloco 6) — uma contagem por visão, independente de qual aba está ativa. */
export function useContagemDeAtendimentos() {
  return useQuery({
    queryKey: ["atendimentos", "contagem"],
    queryFn: () => contarAtendimentosPorVisao(),
  });
}
