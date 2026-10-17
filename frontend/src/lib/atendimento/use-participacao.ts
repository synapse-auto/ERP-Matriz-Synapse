"use client";

import { useCallback, useEffect, useRef, useSyncExternalStore } from "react";
import { useAuthStore } from "@/lib/auth/auth-store";

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
const pendentes = new Map<string, symbol>();
const observadoresAtivos = new Map<string, Set<symbol>>();

// Logout/troca de usuário não pode reaproveitar um pedido pessoal da sessão anterior.
useAuthStore.subscribe((atual, anterior) => {
  if (atual.usuarioId === anterior.usuarioId && atual.papel === anterior.papel) return;
  cache.clear();
  pendentes.clear();
  buscadores.clear();
  observadoresAtivos.clear();
  ouvintes.forEach(lista => lista.forEach(ouvinte => ouvinte()));
});
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
  const requisicao = Symbol(chave);
  pendentes.set(chave, requisicao);
  if (!cache.has(chave)) cache.set(chave, fonte.inicial);
  fonte.buscar()
    .then((valor) => {
      if (pendentes.get(chave) === requisicao) notificar(chave, valor);
    })
    .catch(() => {
      if (pendentes.get(chave) === requisicao) notificar(chave, fonte.inicial);
    })
    .finally(() => {
      if (pendentes.get(chave) === requisicao) pendentes.delete(chave);
    });
}

/**
 * Uma busca por chave. O cabeçalho re-renderiza a cada evento de tempo real e a cada tecla; buscar
 * em todo render virava um laço de requisições enquanto a conversa estava aberta. Nova busca só
 * acontece via `invalidarParticipacao`.
 */
function useRemoteParticipation<T extends Valor>(chave: string, buscar: () => Promise<T>, inicial: T, habilitado = true) {
  const usuarioId = useAuthStore(estado => estado.usuarioId);
  const papel = useAuthStore(estado => estado.papel);
  const chavePessoal = `${usuarioId}:${papel}:${chave}`;
  const ativo = habilitado && usuarioId !== null;
  const buscarAtual = useRef(buscar);
  useEffect(() => { buscarAtual.current = buscar; });
  useEffect(() => {
    if (!ativo) return;
    const observador = Symbol(chavePessoal);
    const ativos = observadoresAtivos.get(chavePessoal) ?? new Set<symbol>();
    ativos.add(observador);
    observadoresAtivos.set(chavePessoal, ativos);
    buscadores.set(chavePessoal, { buscar: () => buscarAtual.current(), inicial });
    if (!cache.has(chavePessoal)) carregar(chavePessoal);
    return () => {
      ativos.delete(observador);
      if (!ativos.size) {
        observadoresAtivos.delete(chavePessoal);
        buscadores.delete(chavePessoal);
        pendentes.delete(chavePessoal);
      }
    };
  }, [chavePessoal, ativo, inicial]);
  const assinar = useCallback((ouvinte: () => void) => {
    const lista = ouvintes.get(chavePessoal) ?? new Set<() => void>();
    lista.add(ouvinte); ouvintes.set(chavePessoal, lista);
    return () => {
      lista.delete(ouvinte);
      if (!lista.size) {
        ouvintes.delete(chavePessoal);
      }
    };
  }, [chavePessoal]);
  const ler = useCallback(
    () => ((ativo ? cache.get(chavePessoal) : undefined) ?? inicial) as T,
    [chavePessoal, inicial, ativo],
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
  const { usuarioId, papel } = useAuthStore.getState();
  [`meu-pedido:${atendimentoId}`, `pedidos-pendentes:${atendimentoId}`].forEach((tipo) => {
    const chave = `${usuarioId}:${papel}:${tipo}`;
    cache.delete(chave);
    pendentes.delete(chave);
    if (ouvintes.get(chave)?.size) carregar(chave);
    ouvintes.get(chave)?.forEach((ouvinte) => ouvinte());
  });
}

export { pedirEntrada, entrarAtendimento, sairAtendimento, aprovarPedido, recusarPedido };
