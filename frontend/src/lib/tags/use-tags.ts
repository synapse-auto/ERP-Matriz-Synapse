"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { atualizarTag, criarTag, listarTags, removerTag } from "./api";
import type { DadosDeTag } from "./types";

/**
 * Mesma chave usada por `useTodasAsTags` (lib/lead/use-painel-lead.ts): criar, editar ou remover
 * uma tag aqui invalida o catálogo usado no atalho de tags do atendimento também.
 */
const CHAVE_TAGS = ["tags"] as const;

export function useTags() {
  return useQuery({ queryKey: CHAVE_TAGS, queryFn: listarTags });
}

export function useCriarTag() {
  const cache = useQueryClient();
  return useMutation({
    mutationFn: (dados: DadosDeTag) => criarTag(dados),
    onSuccess: () => cache.invalidateQueries({ queryKey: CHAVE_TAGS }),
  });
}

export function useAtualizarTag() {
  const cache = useQueryClient();
  return useMutation({
    mutationFn: ({ id, dados }: { id: string; dados: DadosDeTag }) => atualizarTag(id, dados),
    onSuccess: () => cache.invalidateQueries({ queryKey: CHAVE_TAGS }),
  });
}

export function useRemoverTag() {
  const cache = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => removerTag(id),
    onSuccess: () => cache.invalidateQueries({ queryKey: CHAVE_TAGS }),
  });
}
