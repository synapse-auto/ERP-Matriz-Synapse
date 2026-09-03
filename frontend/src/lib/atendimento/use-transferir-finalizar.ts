"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import {
  contarAtendimentosFinalizaveis,
  finalizarAtendimento,
  finalizarAtendimentosVisiveis,
  transferirAtendimento,
} from "./api";
import type { AtendimentoResumo } from "./types";

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

export function useFinalizarAtendimentosVisiveis() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (atendenteId?: string | null) => finalizarAtendimentosVisiveis(atendenteId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["atendimentos"] });
    },
  });
}

export function useQuantidadeAtendimentosFinalizaveis() {
  return useQuery({
    queryKey: ["atendimentos", "finalizar-lote"],
    queryFn: contarAtendimentosFinalizaveis,
  });
}
