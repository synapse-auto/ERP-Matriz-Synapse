"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import {
  atualizarParametroAutomacao,
  listarConfiguracaoAutomacao,
  obterTelemetriaAutomacao,
  obterRecursosIa, atualizarResumoIa,
  listarRegrasFollowUp, criarRegraFollowUp, atualizarRegraFollowUp, alternarRegraFollowUp, excluirRegraFollowUp,
  listarRegrasFidelizacao, criarRegraFidelizacao, atualizarRegraFidelizacao, alternarRegraFidelizacao, excluirRegraFidelizacao,
  listarConfiguracaoFidelizacao, atualizarConfiguracaoFidelizacao,
  listarDatasFestivas, criarDataFestiva, atualizarDataFestiva, alternarDataFestiva, excluirDataFestiva,
} from "./api";
import type { ConfiguracaoResumoIa, FidelizacaoPayload, FollowUpPayload } from "./types";

const CHAVE_CONFIGURACAO_AUTOMACAO = ["automacao", "config"] as const;
const CHAVE_TELEMETRIA_AUTOMACAO = ["automacao", "telemetria"] as const;
const CHAVE_RECURSOS_IA = ["automacao", "recursos-ia"] as const;
export function useRecursosIa() { return useQuery({ queryKey: CHAVE_RECURSOS_IA, queryFn: obterRecursosIa }); }
export function useAtualizarResumoIa() { const cache = useQueryClient(); return useMutation({ mutationFn: (body: ConfiguracaoResumoIa) => atualizarResumoIa(body), onSuccess: () => cache.invalidateQueries({ queryKey: CHAVE_RECURSOS_IA }) }); }

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
const CONFIGURACAO_FIDELIZACAO = ["automacao", "fidelizacao", "configuracao"] as const;
export function useConfiguracaoFidelizacao(enabled = true) {
  return useQuery({ queryKey: CONFIGURACAO_FIDELIZACAO, queryFn: listarConfiguracaoFidelizacao, enabled });
}
export function useAtualizarConfiguracaoFidelizacao() {
  const cache = useQueryClient();
  return useMutation({
    mutationFn: ({ chave, valor }: { chave: string; valor: string }) => atualizarConfiguracaoFidelizacao(chave, valor),
    onSuccess: () => cache.invalidateQueries({ queryKey: CONFIGURACAO_FIDELIZACAO }),
  });
}
const DATAS_FESTIVAS = ["automacao", "fidelizacao", "datas-festivas"] as const;
export function useDatasFestivas(enabled = true) { return useQuery({ queryKey: DATAS_FESTIVAS, queryFn: listarDatasFestivas, enabled }); }
export function useMutacaoDataFestiva() {
  const cache = useQueryClient();
  return useMutation({
    mutationFn: (x: { id?: string; dados: Omit<import("./types").MensagemFestiva, "id"> }) => x.id ? atualizarDataFestiva(x.id, x.dados) : criarDataFestiva(x.dados),
    onSuccess: () => cache.invalidateQueries({ queryKey: DATAS_FESTIVAS }),
  });
}
export function useAlternarDataFestiva() { const cache = useQueryClient(); return useMutation({ mutationFn: ({ id, ativo }: { id: string; ativo: boolean }) => alternarDataFestiva(id, ativo), onSuccess: () => cache.invalidateQueries({ queryKey: DATAS_FESTIVAS }) }); }
export function useExcluirDataFestiva() { const cache = useQueryClient(); return useMutation({ mutationFn: excluirDataFestiva, onSuccess: () => cache.invalidateQueries({ queryKey: DATAS_FESTIVAS }) }); }
