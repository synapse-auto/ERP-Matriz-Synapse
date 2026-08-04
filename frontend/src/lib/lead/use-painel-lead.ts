"use client";

import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import {
  atualizarLead,
  desvincularTagDoLead,
  listarCanais,
  listarCamposCustomizados,
  listarEtapas,
  listarTagsDoLead,
  listarTimeline,
  listarTodasAsTags,
  obterLead,
  vincularTagAoLead,
} from "./api";
import type { AtualizacaoLead, LeadFicha, TagDoLead } from "./types";

export function useLead(leadId: string | null) {
  return useQuery({
    queryKey: ["lead", leadId],
    queryFn: () => obterLead(leadId!),
    enabled: Boolean(leadId),
  });
}

export function useEtapas() {
  return useQuery({ queryKey: ["etapas"], queryFn: listarEtapas });
}

export function useCamposCustomizados() {
  return useQuery({ queryKey: ["campos-customizados"], queryFn: listarCamposCustomizados });
}

export function useCanais() {
  return useQuery({ queryKey: ["canais"], queryFn: listarCanais });
}

export function useTodasAsTags() {
  return useQuery({ queryKey: ["tags"], queryFn: listarTodasAsTags });
}

export function useTagsDoLead(leadId: string | null) {
  return useQuery({
    queryKey: ["lead", leadId, "tags"],
    queryFn: () => listarTagsDoLead(leadId!),
    enabled: Boolean(leadId),
  });
}

export function useTimelineDoLead(leadId: string | null) {
  return useInfiniteQuery({
    queryKey: ["lead", leadId, "timeline"],
    queryFn: ({ pageParam }) => listarTimeline(leadId!, pageParam),
    initialPageParam: 0,
    getNextPageParam: (ultima) => (ultima.temMais ? ultima.pagina + 1 : undefined),
    enabled: Boolean(leadId),
  });
}

export function useSalvarFicha(leadId: string) {
  const cache = useQueryClient();
  return useMutation({
    mutationFn: (dados: AtualizacaoLead) => atualizarLead(leadId, dados),
    onMutate: async (dados) => {
      await cache.cancelQueries({ queryKey: ["lead", leadId] });
      const anterior = cache.getQueryData<LeadFicha>(["lead", leadId]);
      if (anterior) {
        cache.setQueryData<LeadFicha>(["lead", leadId], {
          ...anterior,
          notas: dados.notas,
          dadosCustomizados: dados.dadosCustomizados,
        });
      }
      return { anterior };
    },
    onError: (_erro, _dados, contexto) => {
      if (contexto?.anterior) {
        cache.setQueryData(["lead", leadId], contexto.anterior);
      }
    },
    onSuccess: (salvo) => cache.setQueryData(["lead", leadId], salvo),
  });
}

interface ContextoTag {
  anterior?: TagDoLead[];
}

function useAlterarTag(
  leadId: string,
  operacao: (leadId: string, tagId: string) => Promise<TagDoLead[]>,
  modo: "vincular" | "desvincular",
) {
  const cache = useQueryClient();
  const chave = ["lead", leadId, "tags"] as const;
  return useMutation<TagDoLead[], Error, { tag: TagDoLead }, ContextoTag>({
    mutationFn: ({ tag }) => operacao(leadId, tag.id),
    onMutate: async ({ tag }) => {
      await cache.cancelQueries({ queryKey: chave });
      const anterior = cache.getQueryData<TagDoLead[]>(chave);
      cache.setQueryData<TagDoLead[]>(
        chave,
        modo === "desvincular"
          ? (anterior ?? []).filter((item) => item.id !== tag.id)
          : [...(anterior ?? []), tag].sort((a, b) => a.nome.localeCompare(b.nome)),
      );
      return { anterior };
    },
    onError: (_erro, _variaveis, contexto) => cache.setQueryData(chave, contexto?.anterior ?? []),
    onSuccess: (tags) => cache.setQueryData(chave, tags),
  });
}

export function useVincularTag(leadId: string) {
  return useAlterarTag(leadId, vincularTagAoLead, "vincular");
}

export function useDesvincularTag(leadId: string) {
  return useAlterarTag(leadId, desvincularTagDoLead, "desvincular");
}
