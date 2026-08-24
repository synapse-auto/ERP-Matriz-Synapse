"use client";

import { useCallback, useSyncExternalStore } from "react";

import {
  obterMeuPedido,
  listarPedidosPendentes,
  pedirEntrada,
  entrarAtendimento,
  sairAtendimento,
  aprovarPedido,
  recusarPedido,
} from "./api";
import type { PedidoEntradaAtendimento } from "./types";

type Valor = PedidoEntradaAtendimento | null | PedidoEntradaAtendimento[];
const cache = new Map<string, Valor>();
const ouvintes = new Map<string, Set<() => void>>();
const pendentes = new Set<string>();

function notificar(chave: string, valor: Valor) {
  cache.set(chave, valor);
  ouvintes.get(chave)?.forEach((ouvinte) => ouvinte());
}

function carregar<T extends Valor>(chave: string, buscar: () => Promise<T>, inicial: T) {
  if (pendentes.has(chave)) return;
  pendentes.add(chave);
  if (!cache.has(chave)) cache.set(chave, inicial);
  buscar().then((valor) => notificar(chave, valor)).catch(() => notificar(chave, inicial)).finally(() => pendentes.delete(chave));
}

function useRemoteParticipation<T extends Valor>(chave: string, buscar: () => Promise<T>, inicial: T) {
  carregar(chave, buscar, inicial);
  const assinar = useCallback((ouvinte: () => void) => {
    const lista = ouvintes.get(chave) ?? new Set<() => void>();
    lista.add(ouvinte); ouvintes.set(chave, lista);
    return () => lista.delete(ouvinte);
  }, [chave]);
  const ler = useCallback(() => (cache.get(chave) ?? inicial) as T, [chave, inicial]);
  return useSyncExternalStore(assinar, ler, () => inicial);
}

export function useMeuPedido(atendimentoId: string) {
  return useRemoteParticipation(`meu-pedido:${atendimentoId}`, () => obterMeuPedido(atendimentoId), null) as PedidoEntradaAtendimento | null;
}

export function usePedidosPendentes(atendimentoId: string) {
  return useRemoteParticipation(`pedidos-pendentes:${atendimentoId}`, () => listarPedidosPendentes(atendimentoId), []) as PedidoEntradaAtendimento[];
}

export function invalidarParticipacao(atendimentoId: string) {
  [`meu-pedido:${atendimentoId}`, `pedidos-pendentes:${atendimentoId}`].forEach((chave) => {
    cache.delete(chave); pendentes.delete(chave); ouvintes.get(chave)?.forEach((ouvinte) => ouvinte());
  });
}

export { pedirEntrada, entrarAtendimento, sairAtendimento, aprovarPedido, recusarPedido };
