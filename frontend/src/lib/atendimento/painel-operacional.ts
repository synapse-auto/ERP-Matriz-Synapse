"use client";

import { useEffect, useMemo } from "react";
import { useInfiniteQuery, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiFetch } from "@/lib/api/http-client";
import { useAuthStore } from "@/lib/auth/auth-store";
import { useMinhasPermissoes } from "@/lib/gestao/use-gestao";

export type FilaOperacional = "EM_ABERTO" | "NOVAS" | "ATIVOS" | "PENDENTES" | "SEM_RESPOSTA" | "FINALIZADOS" | "POTENCIAIS";
export type SinalOperacional = "TODOS" | "NOVAS" | "SEM_RESPOSTA";
export interface FiltroOperacional {
  recorte?: FilaOperacional;
  atendenteId?: string;
  entrada?: boolean;
  busca?: string;
  etapaId?: string;
  sinal?: SinalOperacional;
}
export interface ContagensOperacionais {
  novasMensagens: number; novasConversas: number; ativos: number; pendentes: number;
  semResposta: number; finalizados: number; emAberto: number; potenciais: number; esperaDesde: string | null;
}
export interface LinhaOperacional {
  atendenteId: string | null; nome: string | null; fotoUrl: string | null; papel: string | null;
  presenca: string | null; entrada: boolean; elegivel: boolean; contagens: ContagensOperacionais;
}
export interface SnapshotOperacional {
  atualizadoEm: string; finalizadosInicio: string; finalizadosFim: string; fuso: string;
  limiteMinutos: number; proximaTransicaoEm: string; equipe: LinhaOperacional[];
}
export interface ConversaOperacional {
  atendimentoId: string; leadId: string; leadNome: string; leadFotoUrl: string | null;
  leadEmpresa: string | null; status: string; atendenteId: string | null; atendenteNome: string | null;
  naoLidas: number; atividadeEm: string; esperaDesde: string | null;
  etapaNome?: string | null; etapaCor?: string | null; situacao?: string;
  ultimaMensagemPreview?: string; ultimaMensagemTipo?: string | null;
  ultimaMensagemEm?: string | null; respondido?: boolean;
  canalTipo?: string | null;
  /** Campo aditivo; ausente em respostas antigas equivale a não mostrar a marca. */
  convitePendente?: boolean;
}
interface PaginaOperacional { itens: ConversaOperacional[]; proximoCursor: string | null }
/** Estado mínimo consumido pela lista; não inventa variantes do resultado discriminado do Query. */
export type ConsultaFilaOperacional = Pick<ReturnType<typeof useFilaOperacional>,
  "data" | "isLoading" | "isError" | "refetch" | "hasNextPage" | "isFetchingNextPage" | "fetchNextPage">;
const BASE = "/api/v1/atendimentos/painel-operacional";

export function parametrosOperacionais(filtro: FiltroOperacional): URLSearchParams {
  const params = new URLSearchParams();
  for (const [chave, valor] of Object.entries(filtro)) {
    if (valor !== undefined && valor !== false && valor !== "") params.set(chave, String(valor));
  }
  return params;
}

/** Não usa papel como fallback; erro de revalidação também fecha este recurso privilegiado. */
export function useAcessoPainelOperacional() {
  const autenticado = useAuthStore(s => s.status === "autenticado" && !s.precisaTrocarSenha);
  const permissoes = useMinhasPermissoes(autenticado);
  return {
    permitido: autenticado && !permissoes.isError
      && permissoes.data?.capacidades["atendimentos.painel_operacional"]?.permitido === true,
    usuarioId: permissoes.data?.usuarioId,
    revisao: permissoes.data?.revisao,
  };
}

export function useSnapshotOperacional(habilitado: boolean, filtro: FiltroOperacional = {}) {
  const acesso = useAcessoPainelOperacional();
  const cache = useQueryClient();
  const parametros = parametrosOperacionais(filtro);
  parametros.delete("recorte"); // A contagem cobre todas as abas do mesmo recorte de dono/sinal/busca.
  const criterio = parametros.toString();
  const chave = useMemo(() => ["atendimentos", "contagem", "operacional", acesso.usuarioId, acesso.revisao, criterio] as const,
    [acesso.usuarioId, acesso.revisao, criterio]);
  const consulta = useQuery({
    queryKey: chave,
    queryFn: ({ signal }) => apiFetch<SnapshotOperacional>(`${BASE}?${criterio}`, { signal }),
    enabled: habilitado && acesso.permitido,
    // Não reutilizar snapshot de outro usuário, período ou filtros.
    placeholderData: undefined,
  });
  const { data, dataUpdatedAt, isError } = consulta;
  useEffect(() => {
    if (!acesso.permitido) {
      void cache.cancelQueries({ queryKey: ["atendimentos", "contagem", "operacional"] });
      void cache.cancelQueries({ queryKey: ["atendimentos", "operacional-fila"] });
    }
  }, [acesso.permitido, cache]);
  useEffect(() => {
    if (!habilitado || !acesso.permitido || !data || isError) return;
    const intervalo = new Date(data.proximaTransicaoEm).getTime()
      - new Date(data.atualizadoEm).getTime() - (Date.now() - dataUpdatedAt);
    // Um disparo por próxima transição, não um relógio/polling por card ou por linha.
    const timer = window.setTimeout(() => {
      // Outro observador já pode ter concluído a mesma transição entre dois callbacks.
      if (cache.getQueryState(chave)?.dataUpdatedAt !== dataUpdatedAt) return;
      const filas = ["atendimentos", "operacional-fila", acesso.usuarioId, acesso.revisao];
      if (document.visibilityState === "hidden") {
        void cache.invalidateQueries({ queryKey: chave, exact: true, refetchType: "none" });
        void cache.invalidateQueries({ queryKey: filas, refetchType: "none" });
      } else if (cache.getQueryState(chave)?.fetchStatus !== "fetching") {
        // Lista e painel podem observar o mesmo snapshot: não cancelar/reiniciar a mesma leitura.
        void cache.refetchQueries({ queryKey: chave, exact: true }, { cancelRefetch: false });
        void cache.invalidateQueries({ queryKey: filas }, { cancelRefetch: false });
      }
    }, Math.max(100, Math.min(intervalo, 2_147_483_647)));
    return () => window.clearTimeout(timer);
  }, [habilitado, acesso.permitido, acesso.usuarioId, acesso.revisao, data, dataUpdatedAt, isError, cache, chave]);
  return { ...consulta, data: acesso.permitido ? consulta.data : undefined };
}

export function useFilaOperacional(filtro: FiltroOperacional, habilitado: boolean) {
  const acesso = useAcessoPainelOperacional();
  const consulta = useInfiniteQuery({
    queryKey: ["atendimentos", "operacional-fila", acesso.usuarioId, acesso.revisao, filtro],
    initialPageParam: null as string | null,
    queryFn: ({ pageParam, signal }) => {
      const params = parametrosOperacionais(filtro);
      params.set("tamanho", "50");
      if (pageParam) params.set("cursor", pageParam);
      return apiFetch<PaginaOperacional>(`${BASE}/fila?${params}`, { signal });
    },
    getNextPageParam: ultima => ultima.proximoCursor ?? undefined,
    enabled: habilitado && acesso.permitido,
    placeholderData: undefined,
  });
  return { ...consulta, data: acesso.permitido ? consulta.data : undefined };
}

export function totalizarOperacional(equipe: LinhaOperacional[]): ContagensOperacionais {
  const total: ContagensOperacionais = { novasMensagens: 0, novasConversas: 0, ativos: 0, pendentes: 0,
    semResposta: 0, finalizados: 0, emAberto: 0, potenciais: 0, esperaDesde: null };
  for (const linha of equipe) {
    for (const chave of ["novasMensagens", "novasConversas", "ativos", "pendentes", "semResposta", "finalizados", "emAberto", "potenciais"] as const) {
      total[chave] += linha.contagens[chave];
    }
    const espera = linha.contagens.esperaDesde;
    if (espera && (!total.esperaDesde || espera < total.esperaDesde)) total.esperaDesde = espera;
  }
  return total;
}
