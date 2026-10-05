"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import {
  contarAtendimentosFinalizaveis,
  finalizarAtendimento,
  transferirAtendimento,
} from "./api";
import type { AtendimentoResumo } from "./types";

/** Transferência que o próprio usuário acabou de fazer; a perda de acesso que vem dela é esperada. */
export interface TransferenciaPropria {
  paraAtendenteId: string | null;
  destinoNome: string | null;
}

export function chaveDaTransferenciaPropria(atendimentoId: string) {
  return ["transferencia-propria", atendimentoId] as const;
}

export function useTransferirAtendimento() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({
      atendimentoId,
      paraAtendenteId,
    }: {
      atendimentoId: string;
      paraAtendenteId: string | null;
      destinoNome?: string | null;
    }) => transferirAtendimento(atendimentoId, paraAtendenteId),
    onSuccess: (_resumo, { atendimentoId, paraAtendenteId, destinoNome }) => {
      queryClient.setQueryData<TransferenciaPropria>(chaveDaTransferenciaPropria(atendimentoId), {
        paraAtendenteId,
        destinoNome: destinoNome ?? null,
      });
      queryClient.invalidateQueries({ queryKey: ["atendimentos"] });
    },
  });
}

export function useFinalizarAtendimento(
  onAtendimentoFinalizado?: (resumo: AtendimentoResumo) => void,
) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (atendimentoId: string) => finalizarAtendimento(atendimentoId),
    onSuccess: (resumo) => {
      queryClient.invalidateQueries({ queryKey: ["atendimentos"] });
      onAtendimentoFinalizado?.(resumo);
    },
  });
}

export function useQuantidadeAtendimentosFinalizaveis(habilitado = true) {
  return useQuery({
    queryKey: ["atendimentos", "finalizar-lote"],
    queryFn: contarAtendimentosFinalizaveis,
    enabled: habilitado,
  });
}
