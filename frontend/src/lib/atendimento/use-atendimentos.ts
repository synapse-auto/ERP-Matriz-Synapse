"use client";

import { useQuery } from "@tanstack/react-query";

import { contarAtendimentosPorVisao, listarAtendimentos } from "./api";
import type { VisaoAtendimento } from "./types";

export function useAtendimentos(visao: VisaoAtendimento) {
  return useQuery({
    queryKey: ["atendimentos", visao],
    queryFn: () => listarAtendimentos(visao),
  });
}

/** Os badges das abas (E17b §Bloco 6) — uma contagem por visão, independente de qual aba está ativa. */
export function useContagemDeAtendimentos() {
  return useQuery({
    queryKey: ["atendimentos", "contagem"],
    queryFn: () => contarAtendimentosPorVisao(),
  });
}
