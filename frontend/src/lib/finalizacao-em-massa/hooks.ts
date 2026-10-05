"use client";

import { useEffect, useRef } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import {
  iniciarFinalizacao,
  listarItensDaFinalizacao,
  listarOperacoesDeFinalizacao,
  obterOperacaoDeFinalizacao,
  preverFinalizacao,
} from "./api";
import type { FiltroDeFinalizacao, OperacaoDeFinalizacao, StatusDoItemDeFinalizacao } from "./types";
import { filtroNormalizado, problemaDoFiltro } from "./validacao";

const CHAVE = ["finalizacao-em-massa"] as const;

/** Frequencia com que o progresso e relido enquanto a operacao esta aberta (o worker anda em lotes curtos). */
export const INTERVALO_DE_ACOMPANHAMENTO_MS = 1500;
export const ITENS_POR_PAGINA = 50;

/** So consulta com filtro valido; o servidor continua revalidando e decidindo quem pode ver o que. */
export function usePreviaDeFinalizacao(filtro: FiltroDeFinalizacao, habilitado: boolean) {
  const normalizado = filtroNormalizado(filtro);
  return useQuery({
    queryKey: [...CHAVE, "previa", normalizado],
    queryFn: () => preverFinalizacao(normalizado),
    enabled: habilitado && problemaDoFiltro(normalizado) === null,
    staleTime: 0,
    gcTime: 0,
    retry: false,
  });
}

export function useIniciarFinalizacao() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ filtro, chave }: { filtro: FiltroDeFinalizacao; chave: string }) =>
      iniciarFinalizacao(filtroNormalizado(filtro), chave),
    onSuccess: (operacao) => {
      queryClient.setQueryData(operacaoKey(operacao.id), operacao);
      void queryClient.invalidateQueries({ queryKey: [...CHAVE, "recentes"] });
    },
  });
}

function operacaoKey(id: string) {
  return [...CHAVE, "operacao", id] as const;
}

/**
 * Acompanha a operacao ate concluir. A lista de atendimentos e atualizada a cada avanco (e uma vez ao concluir), para
 * quem deixou a tela aberta ver os atendimentos saindo da fila sem F5.
 */
export function useOperacaoDeFinalizacao(id: string | null) {
  const queryClient = useQueryClient();
  const consulta = useQuery({
    queryKey: operacaoKey(id ?? ""),
    queryFn: () => obterOperacaoDeFinalizacao(id as string),
    enabled: id !== null,
    refetchInterval: (query) => {
      const operacao = query.state.data as OperacaoDeFinalizacao | undefined;
      return operacao && operacao.status === "CONCLUIDA" ? false : INTERVALO_DE_ACOMPANHAMENTO_MS;
    },
  });

  const ultimoProcessado = useRef<number | null>(null);
  const operacao = consulta.data;
  useEffect(() => {
    if (!operacao) return;
    const mudou = ultimoProcessado.current !== operacao.processados;
    ultimoProcessado.current = operacao.processados;
    if (!mudou) return;
    void queryClient.invalidateQueries({ queryKey: ["atendimentos"] });
    if (operacao.status === "CONCLUIDA") {
      void queryClient.invalidateQueries({ queryKey: [...CHAVE, "recentes"] });
    }
  }, [operacao, queryClient]);

  return consulta;
}

export function useItensDaFinalizacao(
  id: string | null,
  status: StatusDoItemDeFinalizacao | null,
  habilitado: boolean,
) {
  return useQuery({
    queryKey: [...CHAVE, "itens", id, status],
    queryFn: () => listarItensDaFinalizacao(id as string, status, 0, ITENS_POR_PAGINA),
    enabled: habilitado && id !== null,
  });
}

/** Ultimas operacoes do usuario: e por aqui que se reabre uma operacao depois de fechar a janela. */
export function useOperacoesDeFinalizacao(habilitado: boolean) {
  return useQuery({
    queryKey: [...CHAVE, "recentes"],
    queryFn: listarOperacoesDeFinalizacao,
    enabled: habilitado,
  });
}
