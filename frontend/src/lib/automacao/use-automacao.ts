"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import {
  atualizarParametroAutomacao,
  listarConfiguracaoAutomacao,
  obterTelemetriaAutomacao,
  listarRegrasFollowUp, criarRegraFollowUp, atualizarRegraFollowUp, alternarRegraFollowUp, excluirRegraFollowUp,
  listarRegrasFidelizacao, criarRegraFidelizacao, atualizarRegraFidelizacao, alternarRegraFidelizacao, excluirRegraFidelizacao,
} from "./api";
import type { FidelizacaoPayload, FollowUpPayload } from "./types";

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

const FOLLOW_UP = ["automacao", "follow-ups"] as const;
const FIDELIZACAO = ["automacao", "fidelizacao"] as const;
export function useRegrasFollowUp() { return useQuery({ queryKey: FOLLOW_UP, queryFn: listarRegrasFollowUp }); }
export function useRegrasFidelizacao() { return useQuery({ queryKey: FIDELIZACAO, queryFn: listarRegrasFidelizacao }); }
export function useMutacaoRegraFollowUp() { const c = useQueryClient(); return useMutation({ mutationFn: (x: { id?: string; dados: FollowUpPayload }) => x.id ? atualizarRegraFollowUp(x.id, x.dados) : criarRegraFollowUp(x.dados), onSuccess: () => c.invalidateQueries({ queryKey: FOLLOW_UP }) }); }
export function useAlternarRegraFollowUp() { const c = useQueryClient(); return useMutation({ mutationFn: ({ id, ativo }: { id: string; ativo: boolean }) => alternarRegraFollowUp(id, ativo), onSuccess: () => c.invalidateQueries({ queryKey: FOLLOW_UP }) }); }
export function useExcluirRegraFollowUp() { const c = useQueryClient(); return useMutation({ mutationFn: excluirRegraFollowUp, onSuccess: () => c.invalidateQueries({ queryKey: FOLLOW_UP }) }); }
export function useMutacaoRegraFidelizacao() { const c = useQueryClient(); return useMutation({ mutationFn: (x: { id?: string; dados: FidelizacaoPayload }) => x.id ? atualizarRegraFidelizacao(x.id, x.dados) : criarRegraFidelizacao(x.dados), onSuccess: () => c.invalidateQueries({ queryKey: FIDELIZACAO }) }); }
export function useAlternarRegraFidelizacao() { const c = useQueryClient(); return useMutation({ mutationFn: ({ id, ativo }: { id: string; ativo: boolean }) => alternarRegraFidelizacao(id, ativo), onSuccess: () => c.invalidateQueries({ queryKey: FIDELIZACAO }) }); }
export function useExcluirRegraFidelizacao() { const c = useQueryClient(); return useMutation({ mutationFn: excluirRegraFidelizacao, onSuccess: () => c.invalidateQueries({ queryKey: FIDELIZACAO }) }); }
