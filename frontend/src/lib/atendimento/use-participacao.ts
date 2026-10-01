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
const UUID =
  /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

function idDeAtendimentoValido(id: string | null | undefined): id is string {
  return typeof id === "string" && UUID.test(id);
}

function notificar(chave: string, valor: Valor) {
  cache.set(chave, valor);
  ouvintes.get(chave)?.forEach((ouvinte) => ouvinte());
}

const buscadores = new Map<string, { buscar: () => Promise<Valor>; inicial: Valor }>();

function carregar(chave: string) {
  const fonte = buscadores.get(chave);
  if (!fonte || pendentes.has(chave)) return;
  pendentes.add(chave);
  if (!cache.has(chave)) cache.set(chave, fonte.inicial);
  fonte.buscar()
    .then((valor) => notificar(chave, valor))
    .catch(() => notificar(chave, fonte.inicial))
    .finally(() => pendentes.delete(chave));
}

/**
 * Uma busca por chave. O cabeçalho re-renderiza a cada evento de tempo real e a cada tecla; buscar
 * em todo render virava um laço de requisições enquanto a conversa estava aberta. Nova busca só
 * acontece via `invalidarParticipacao`.
 */
function useRemoteParticipation<T extends Valor>(chave: string, buscar: () => Promise<T>, inicial: T, habilitado = true) {
  if (habilitado) {
    buscadores.set(chave, { buscar, inicial });
    if (!cache.has(chave)) carregar(chave);
  }
  const assinar = useCallback((ouvinte: () => void) => {
    const lista = ouvintes.get(chave) ?? new Set<() => void>();
    lista.add(ouvinte); ouvintes.set(chave, lista);
    return () => lista.delete(ouvinte);
  }, [chave]);
  const ler = useCallback(
    () => ((habilitado ? cache.get(chave) : undefined) ?? inicial) as T,
    [chave, inicial, habilitado],
  );
  return useSyncExternalStore(assinar, ler, () => inicial);
}

export function useMeuPedido(atendimentoId: string) {
  return useRemoteParticipation(
    `meu-pedido:${atendimentoId}`,
    () =>
      idDeAtendimentoValido(atendimentoId)
        ? obterMeuPedido(atendimentoId)
        : Promise.resolve(null),
    null,
  ) as PedidoEntradaAtendimento | null;
}

const SEM_PEDIDOS: PedidoEntradaAtendimento[] = [];

/** Só o responsável avalia pedidos; para os demais o endpoint responde 404 e não deve ser chamado. */
export function usePedidosPendentes(atendimentoId: string, habilitado = true) {
  return useRemoteParticipation(
    `pedidos-pendentes:${atendimentoId}`,
    () =>
      idDeAtendimentoValido(atendimentoId)
        ? listarPedidosPendentes(atendimentoId)
        : Promise.resolve(SEM_PEDIDOS),
    SEM_PEDIDOS,
    habilitado,
  ) as PedidoEntradaAtendimento[];
}

export function invalidarParticipacao(atendimentoId: string) {
  [`meu-pedido:${atendimentoId}`, `pedidos-pendentes:${atendimentoId}`].forEach((chave) => {
    cache.delete(chave);
    pendentes.delete(chave);
    if (ouvintes.get(chave)?.size) carregar(chave);
    ouvintes.get(chave)?.forEach((ouvinte) => ouvinte());
  });
}

export { pedirEntrada, entrarAtendimento, sairAtendimento, aprovarPedido, recusarPedido };
