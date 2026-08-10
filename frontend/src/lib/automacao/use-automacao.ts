"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import {
  atualizarParametroAutomacao,
  listarConfiguracaoAutomacao,
  obterTelemetriaAutomacao,
} from "./api";

const CHAVE_CONFIGURACAO_AUTOMACAO = ["automacao", "config"] as const;
const CHAVE_TELEMETRIA_AUTOMACAO = ["automacao", "telemetria"] as const;

export function useConfiguracaoAutomacao() {
  return useQuery({
    queryKey: CHAVE_CONFIGURACAO_AUTOMACAO,
    queryFn: listarConfiguracaoAutomacao,
  });
}

/** Os quatro cards do topo da tela de Automação (E17b §Bloco 5). */
export function useTelemetriaAutomacao() {
  return useQuery({
    queryKey: CHAVE_TELEMETRIA_AUTOMACAO,
    queryFn: obterTelemetriaAutomacao,
  });
}

export function useAtualizarParametroAutomacao() {
  const cache = useQueryClient();
  return useMutation({
    mutationFn: ({ chave, valor }: { chave: string; valor: string }) =>
      atualizarParametroAutomacao(chave, valor),
    onSuccess: () => cache.invalidateQueries({ queryKey: CHAVE_CONFIGURACAO_AUTOMACAO }),
  });
}
