"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import {
  listarPerfis,
  listarPermissoesDaEquipe,
  obterCatalogo,
  obterMinhasPermissoes,
  obterPermissoesDeUsuario,
  restaurarPadrao,
  salvarExcecoes,
  salvarPerfil,
} from "./api";
import type { Papel, Rascunho } from "./types";

export const CHAVE_GESTAO = ["gestao"] as const;
export const CHAVE_MINHAS_PERMISSOES = ["permissoes", "minhas"] as const;

/**
 * Efetivas do autenticado, uma consulta para a tela inteira. Revalida no foco da janela e quando o
 * backend avisa ACESSO_ALTERADO (ver NotificacoesTempoReal) — sem polling.
 */
export function useMinhasPermissoes(habilitado = true) {
  return useQuery({
    queryKey: CHAVE_MINHAS_PERMISSOES,
    queryFn: obterMinhasPermissoes,
    enabled: habilitado,
    staleTime: 30_000,
  });
}

export function useCatalogo() {
  return useQuery({ queryKey: [...CHAVE_GESTAO, "catalogo"], queryFn: obterCatalogo });
}

export function usePerfis() {
  return useQuery({ queryKey: [...CHAVE_GESTAO, "perfis"], queryFn: listarPerfis });
}

export function usePermissoesDaEquipe() {
  return useQuery({ queryKey: [...CHAVE_GESTAO, "usuarios"], queryFn: listarPermissoesDaEquipe });
}

export function usePermissoesDeUsuario(id: string | null) {
  return useQuery({
    queryKey: [...CHAVE_GESTAO, "usuario", id],
    queryFn: () => obterPermissoesDeUsuario(id as string),
    enabled: id != null,
  });
}

/** Nunca publica sucesso antes da resposta: o cache só é invalidado em onSuccess. */
export function useSalvarPerfil() {
  const cache = useQueryClient();
  return useMutation({
    mutationFn: (v: { papel: Papel; revisao: number; rascunho: Rascunho; copiadoDe?: Papel }) =>
      salvarPerfil(v.papel, v.revisao, v.rascunho, v.copiadoDe),
    onSuccess: () => {
      void cache.invalidateQueries({ queryKey: CHAVE_GESTAO });
      void cache.invalidateQueries({ queryKey: CHAVE_MINHAS_PERMISSOES });
    },
  });
}

export function useSalvarExcecoes() {
  const cache = useQueryClient();
  return useMutation({
    mutationFn: (v: { id: string; revisao: number; rascunho: Rascunho; copiadoDe?: string }) =>
      salvarExcecoes(v.id, v.revisao, v.rascunho, v.copiadoDe),
    onSuccess: () => {
      void cache.invalidateQueries({ queryKey: CHAVE_GESTAO });
      void cache.invalidateQueries({ queryKey: CHAVE_MINHAS_PERMISSOES });
    },
  });
}

export function useRestaurarPadrao() {
  const cache = useQueryClient();
  return useMutation({
    mutationFn: (v: { id: string; revisao: number }) => restaurarPadrao(v.id, v.revisao),
    onSuccess: () => {
      void cache.invalidateQueries({ queryKey: CHAVE_GESTAO });
      void cache.invalidateQueries({ queryKey: CHAVE_MINHAS_PERMISSOES });
    },
  });
}
