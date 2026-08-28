"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { obterAvaliacao, registrarAvaliacao } from "./api";

export function useAvaliacaoDoAtendimento(atendimentoId: string, habilitado: boolean) {
  return useQuery({
    queryKey: ["atendimentos", atendimentoId, "avaliacao"],
    queryFn: () => obterAvaliacao(atendimentoId),
    enabled: habilitado,
  });
}

export function useRegistrarAvaliacao(atendimentoId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (nota: number) => registrarAvaliacao(atendimentoId, nota),
    onSuccess: (avaliacao) => {
      queryClient.setQueryData(["atendimentos", atendimentoId, "avaliacao"], avaliacao);
    },
  });
}
