"use client";

import { useMutation, useQueryClient } from "@tanstack/react-query";

import { finalizarAtendimento, transferirAtendimento } from "./api";

export function useTransferirAtendimento() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({
      atendimentoId,
      paraAtendenteId,
    }: {
      atendimentoId: string;
      paraAtendenteId: string | null;
    }) => transferirAtendimento(atendimentoId, paraAtendenteId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["atendimentos"] });
    },
  });
}

export function useFinalizarAtendimento() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (atendimentoId: string) => finalizarAtendimento(atendimentoId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["atendimentos"] });
    },
  });
}
