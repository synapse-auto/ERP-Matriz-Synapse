"use client";

import { useEffect } from "react";
import { useInfiniteQuery, useMutation, useQuery, useQueryClient, type QueryClient } from "@tanstack/react-query";

import type { ItemInbox } from "@/lib/atendimento/types";
import { INTERVALO_REVALIDACAO_CACHE_MS } from "@/lib/query/tempos";

import {
  atualizarLead,
  desvincularTagDoLead,
  listarCanais,
  listarCamposCustomizados,
  listarEtapas,
  listarTagsDoLead,
  listarTimeline,
  listarTodasAsTags,
  listarMidiasDoLead,
  obterLead,
  obterEstadoResumoIa,
  solicitarResumoIa,
  obterLeadNaAgenda,
  vincularTagAoLead,
} from "./api";
import type { AtualizacaoLead, LeadFicha, MidiaDoLead, TagDoLead } from "./types";

export const TIPOS_MIDIAS_DA_FICHA = ["IMAGEM", "VIDEO", "DOCUMENTO"] as const satisfies readonly MidiaDoLead["tipo"][];

export function useLead(leadId: string | null, contexto: "padrao" | "agenda" = "padrao") {
  return useQuery({
    queryKey: contexto === "agenda" ? ["lead", "agenda", leadId] : ["lead", leadId],
    queryFn: () => (contexto === "agenda" ? obterLeadNaAgenda(leadId!) : obterLead(leadId!)),
    enabled: Boolean(leadId),
  });
}

export function useEtapas() {
  return useQuery({ queryKey: ["etapas"], queryFn: listarEtapas });
}

export function useEstadoResumoIa(atendimentoId: string | null) {
  const cache = useQueryClient();
  const estado = useQuery({
    queryKey: ["resumo-ia", atendimentoId],
    queryFn: () => obterEstadoResumoIa(atendimentoId!),
    enabled: Boolean(atendimentoId),
    // O WebSocket é o caminho principal. Se o evento se perder, consulte apenas o ciclo
    // aberto enquanto esta ficha estiver montada; estado terminal interrompe o intervalo.
    refetchInterval: (query) =>
      query.state.data?.status === "PENDENTE" || query.state.data?.status === "PROCESSANDO"
        ? INTERVALO_REVALIDACAO_CACHE_MS
        : false,
    refetchOnReconnect: "always",
  });
  const solicitacaoId = estado.data?.solicitacaoId;
  const leadId = estado.data?.leadId;
  const status = estado.data?.status;

  useEffect(() => {
    if (solicitacaoId && leadId && (status === "CONCLUIDO" || status === "FALHOU")) {
      void cache.invalidateQueries({ queryKey: ["lead", leadId] });
    }
  }, [cache, solicitacaoId, leadId, status]);

  return estado;
}

export function useSolicitarResumoIa(atendimentoId: string) {
  const cache = useQueryClient();
  return useMutation({
    mutationFn: ({ solicitacaoId }: { solicitacaoId: string }) => solicitarResumoIa(atendimentoId, solicitacaoId),
    onSuccess: (estado) => {
      cache.setQueryData(["resumo-ia", atendimentoId], estado);
      void cache.invalidateQueries({ queryKey: ["lead", estado.leadId] });
    },
  });
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

export function useMidiasDoLead(leadId: string | null, tipos?: readonly MidiaDoLead["tipo"][]) {
  return useInfiniteQuery({
    queryKey: ["lead", leadId, "midias", tipos?.join(",") ?? "todos"],
    queryFn: ({ pageParam }) => listarMidiasDoLead(leadId!, pageParam, 20, tipos),
    initialPageParam: 0,
    getNextPageParam: (ultima, _paginas, pagina) => ultima.length === 20 ? pagina + 1 : undefined,
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
          notas: dados.notas ?? anterior.notas,
          dadosCustomizados: dados.dadosCustomizados ?? anterior.dadosCustomizados,
          nome: dados.nome ?? anterior.nome,
          codigo:
            dados.codigo !== undefined
              ? dados.codigo === ""
                ? null
                : dados.codigo
              : anterior.codigo,
        });
      }
      return { anterior };
    },
    onError: (_erro, _dados, contexto) => {
      if (contexto?.anterior) {
        cache.setQueryData(["lead", leadId], contexto.anterior);
      }
    },
    onSuccess: (salvo, dados) => {
      cache.setQueryData(["lead", leadId], salvo);
      atualizarCartaoNaInbox(cache, leadId, {
        ...(dados.nome !== undefined ? { leadNome: salvo.nome } : {}),
        ...(dados.codigo !== undefined ? { leadCodigo: salvo.codigo } : {}),
      });
      if (dados.nome !== undefined) {
        cache.invalidateQueries({ queryKey: ["agenda"] });
      }
    },
  });
}

function atualizarCartaoNaInbox(
  cache: QueryClient,
  leadId: string,
  patch: { leadNome?: string; leadCodigo?: string | null },
) {
  cache.setQueriesData({ queryKey: ["atendimentos"] }, (atual: unknown) => {
    if (!atual || typeof atual !== "object") return atual;
    if (Array.isArray(atual)) {
      return atual.map((item) =>
        item && typeof item === "object" && "leadId" in item && item.leadId === leadId
          ? { ...item, ...patch }
          : item,
      );
    }
    if ("pages" in atual && Array.isArray((atual as { pages: unknown }).pages)) {
      const inf = atual as { pages: { itens?: ItemInbox[] }[] };
      return {
        ...inf,
        pages: inf.pages.map((pagina) => ({
          ...pagina,
          itens: (pagina.itens ?? []).map((item) =>
            item.tipo !== "EQUIPE_INTERNA" && item.leadId === leadId
              ? { ...item, ...patch }
              : item,
          ),
        })),
      };
    }
    return atual;
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
