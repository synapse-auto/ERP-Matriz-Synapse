"use client";

import { useQuery } from "@tanstack/react-query";

import { contarAtendimentosPorVisao, listarAtendimentos } from "./api";
import type { VisaoAtendimento } from "./types";

const INTERVALO_DE_REVALIDACAO_MS = Number(
  process.env.NEXT_PUBLIC_ATENDIMENTOS_POLLING_MS ?? "10000",
);

export function useAtendimentos(visao: VisaoAtendimento) {
  return useQuery({
    queryKey: ["atendimentos", visao],
    queryFn: () => listarAtendimentos(visao),
    // Também detecta a abertura de um novo atendimento pela IA quando a conversa estava sem socket.
    refetchInterval: INTERVALO_DE_REVALIDACAO_MS,
  });
}

/** Os badges das abas (E17b §Bloco 6) — uma contagem por visão, independente de qual aba está ativa. */
export function useContagemDeAtendimentos() {
  return useQuery({
    queryKey: ["atendimentos", "contagem"],
    queryFn: () => contarAtendimentosPorVisao(),
  });
}
