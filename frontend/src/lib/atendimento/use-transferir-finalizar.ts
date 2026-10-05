"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import {
  contarAtendimentosFinalizaveis,
  finalizarAtendimento,
  finalizarAtendimentosVisiveis,
  registrarResultadoVenda,
  transferirAtendimento,
} from "./api";
import type { AtendimentoResumo, ResultadoVenda } from "./types";

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
    mutationFn: (entrada: string | { atendimentoId: string; resultadoVenda?: ResultadoVenda }) =>
      typeof entrada === "string"
        ? finalizarAtendimento(entrada)
        : finalizarAtendimento(entrada.atendimentoId, entrada.resultadoVenda),
    onSuccess: (resumo) => {
      queryClient.invalidateQueries({ queryKey: ["atendimentos"] });
      queryClient.invalidateQueries({ queryKey: ["dashboard"] });
      onAtendimentoFinalizado?.(resumo);
    },
  });
}

export function useRegistrarResultadoVenda() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ atendimentoId, resultado }: { atendimentoId: string; resultado: ResultadoVenda }) =>
      registrarResultadoVenda(atendimentoId, resultado),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["atendimentos"] });
      queryClient.invalidateQueries({ queryKey: ["dashboard"] });
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

export function useQuantidadeAtendimentosFinalizaveis(habilitado = true) {
  return useQuery({
    queryKey: ["atendimentos", "finalizar-lote"],
    queryFn: contarAtendimentosFinalizaveis,
    enabled: habilitado,
  });
}
