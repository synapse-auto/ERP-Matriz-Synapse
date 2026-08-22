"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import {
  avaliacoesEquipe,
  criarUsuario,
  desempenhoEquipe,
  desativarUsuario,
  editarUsuario,
  gerarSenhaProvisoria,
  listarEquipe,
  atualizarDisponibilidadeParaIa,
  obterMeuUsuario,
} from "./api";
import type { PapelGerenciavel } from "./types";

const CHAVE_EQUIPE = ["equipe"] as const;
const CHAVE_AVALIACOES = ["equipe", "avaliacoes"] as const;
const CHAVE_DESEMPENHO = ["equipe", "desempenho"] as const;

/**
 * Extraída como hook próprio (em vez de `useQuery` inline no componente) para ficar mockável nos
 * testes de tela no mesmo padrão do resto do projeto — cada fonte de dado tem um hook, nunca
 * `useQuery` cru espalhado pelos componentes.
 */
export function useEquipe() {
  return useQuery({ queryKey: CHAVE_EQUIPE, queryFn: listarEquipe });
}

/** Mini-dashboard e ranking por avaliação (E17b §Bloco 4). */
export function useAvaliacoesEquipe() {
  return useQuery({ queryKey: CHAVE_AVALIACOES, queryFn: avaliacoesEquipe });
}

/** Atendimentos e vendas usam read model gerencial restrito a gestao. */
export function useDesempenhoEquipe() {
  return useQuery({ queryKey: CHAVE_DESEMPENHO, queryFn: desempenhoEquipe });
}

/** GET /api/v1/me (E17) — nome, papel e presença de quem está autenticado. */
export function useMeuUsuario() {
  return useQuery({ queryKey: ["me"], queryFn: obterMeuUsuario });
}

export function useCriarUsuario() {
  const cache = useQueryClient();
  return useMutation({
    mutationFn: (dados: { nome: string; email: string; senha: string; papel: PapelGerenciavel }) =>
      criarUsuario(dados),
    onSuccess: () => cache.invalidateQueries({ queryKey: CHAVE_EQUIPE }),
  });
}

export function useEditarUsuario() {
  const cache = useQueryClient();
  return useMutation({
    mutationFn: ({
      id,
      dados,
    }: {
      id: string;
      dados: { nome: string; email: string; papel: PapelGerenciavel };
    }) => editarUsuario(id, dados),
    onSuccess: () => cache.invalidateQueries({ queryKey: CHAVE_EQUIPE }),
  });
}

export function useDesativarUsuario() {
  const cache = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => desativarUsuario(id),
    onSuccess: () => cache.invalidateQueries({ queryKey: CHAVE_EQUIPE }),
  });
}

/** E29: não invalida CHAVE_EQUIPE — gerar a senha não muda nenhum campo listado na grade. */
export function useGerarSenhaProvisoria() {
  return useMutation({
    mutationFn: (id: string) => gerarSenhaProvisoria(id),
  });
}

export function useAtualizarDisponibilidadeParaIa() {
  const cache = useQueryClient();
  return useMutation({
    mutationFn: ({ id, disponivelParaIa }: { id: string; disponivelParaIa: boolean }) =>
      atualizarDisponibilidadeParaIa(id, disponivelParaIa),
    onSuccess: () => cache.invalidateQueries({ queryKey: CHAVE_EQUIPE }),
  });
}
