"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { atualizarParametroAutomacao, listarConfiguracaoAutomacao } from "./api";

const CHAVE_CONFIGURACAO_AUTOMACAO = ["automacao", "config"] as const;

export function useConfiguracaoAutomacao() {
  return useQuery({
    queryKey: CHAVE_CONFIGURACAO_AUTOMACAO,
    queryFn: listarConfiguracaoAutomacao,
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
