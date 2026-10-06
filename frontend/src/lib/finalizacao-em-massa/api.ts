import { apiFetch } from "@/lib/api/http-client";

import type {
  FiltroDeFinalizacao,
  OperacaoDeFinalizacao,
  PaginaDeItens,
  PreviaDeFinalizacao,
  StatusDoItemDeFinalizacao,
} from "./types";

const BASE = "/api/v1/atendimentos/finalizacoes-em-massa";

function enviando(corpo: unknown, cabecalhos?: Record<string, string>): RequestInit {
  return { method: "POST", body: JSON.stringify(corpo), headers: cabecalhos };
}

export function preverFinalizacao(filtro: FiltroDeFinalizacao): Promise<PreviaDeFinalizacao> {
  return apiFetch<PreviaDeFinalizacao>(`${BASE}/previa`, enviando(filtro));
}

/**
 * A chave identifica esta confirmacao: repetir a chamada (duplo clique, retry de rede) devolve a mesma operacao em
 * vez de abrir outra.
 */
export function iniciarFinalizacao(
  filtro: FiltroDeFinalizacao,
  chaveDeIdempotencia: string,
): Promise<OperacaoDeFinalizacao> {
  return apiFetch<OperacaoDeFinalizacao>(BASE, enviando(filtro, { "Idempotency-Key": chaveDeIdempotencia }));
}

export function obterOperacaoDeFinalizacao(id: string): Promise<OperacaoDeFinalizacao> {
  return apiFetch<OperacaoDeFinalizacao>(`${BASE}/${id}`);
}

export function listarOperacoesDeFinalizacao(): Promise<OperacaoDeFinalizacao[]> {
  return apiFetch<OperacaoDeFinalizacao[]>(BASE);
}

export function listarItensDaFinalizacao(
  id: string,
  status: StatusDoItemDeFinalizacao | null,
  pagina: number,
  tamanho: number,
): Promise<PaginaDeItens> {
  const parametros = new URLSearchParams({ pagina: String(pagina), tamanho: String(tamanho) });
  if (status) parametros.set("status", status);
  return apiFetch<PaginaDeItens>(`${BASE}/${id}/itens?${parametros.toString()}`);
}
