"use client";

import { useCallback, useSyncExternalStore } from "react";

import { useAuthStore } from "@/lib/auth/auth-store";

const CHAVE_BASE_SOM = "synapse:preferencias-notificacoes:som";
const CHAVE_BASE_VISUAL = "synapse:preferencias-notificacoes:visual";
const CHAVE_BASE_CHAT_INTERNO = "synapse:preferencias-notificacoes:chat-interno";
const CHAVE_BASE_DURACAO = "synapse:preferencias-notificacoes:duracao-segundos";
const CHAVE_BASE_POSICAO = "synapse:preferencias-notificacoes:posicao";
const EVENTO_PREFERENCIA_ALTERADA = "synapse:preferencia-notificacao-alterada";

export type PosicaoDaNotificacao = "TOPO" | "BAIXO";

function chaveDaPreferencia(base: string, usuarioId: string | null): string | null {
  return usuarioId ? `${base}:${usuarioId}` : null;
}

function lerBooleano(base: string, usuarioId: string | null, padrao: boolean): boolean {
  const chave = chaveDaPreferencia(base, usuarioId);
  if (!chave || typeof window === "undefined") return padrao;
  const valor = window.localStorage.getItem(chave);
  return valor == null ? padrao : valor === "true";
}

function lerPreferenciaSom(usuarioId: string | null): boolean {
  const chave = chaveDaPreferencia(CHAVE_BASE_SOM, usuarioId);
  if (!chave || typeof window === "undefined") return true;
  // Compatibilidade com a preferência existente: somente "false" desativa.
  return window.localStorage.getItem(chave) !== "false";
}

function definirPreferencia(base: string, usuarioId: string | null, valor: string): void {
  const chave = chaveDaPreferencia(base, usuarioId);
  if (!chave || typeof window === "undefined") return;
  window.localStorage.setItem(chave, valor);
  window.dispatchEvent(new Event(EVENTO_PREFERENCIA_ALTERADA));
}

export function usePreferenciaSomDeNotificacao(): {
  somHabilitado: boolean;
  definirSomHabilitado: (habilitado: boolean) => void;
} {
  const usuarioId = useAuthStore((estado) => estado.usuarioId);
  const somHabilitado = useSyncExternalStore(
    assinarMudancasDePreferencia,
    () => lerPreferenciaSom(usuarioId),
    () => true,
  );

  const definirSomHabilitado = useCallback((habilitado: boolean) => {
    definirPreferencia(CHAVE_BASE_SOM, usuarioId, String(habilitado));
  }, [usuarioId]);

  return { somHabilitado, definirSomHabilitado };
}

export function usePreferenciaVisualDeNotificacao(): {
  visualHabilitado: boolean;
  definirVisualHabilitado: (habilitado: boolean) => void;
} {
  const usuarioId = useAuthStore((estado) => estado.usuarioId);
  const visualHabilitado = useSyncExternalStore(
    assinarMudancasDePreferencia,
    () => lerBooleano(CHAVE_BASE_VISUAL, usuarioId, true),
    () => true,
  );
  const definirVisualHabilitado = useCallback((habilitado: boolean) => {
    definirPreferencia(CHAVE_BASE_VISUAL, usuarioId, String(habilitado));
  }, [usuarioId]);
  return { visualHabilitado, definirVisualHabilitado };
}

export function usePreferenciaChatInternoDeNotificacao(): {
  chatInternoHabilitado: boolean;
  definirChatInternoHabilitado: (habilitado: boolean) => void;
} {
  const usuarioId = useAuthStore((estado) => estado.usuarioId);
  const chatInternoHabilitado = useSyncExternalStore(
    assinarMudancasDePreferencia,
    () => lerBooleano(CHAVE_BASE_CHAT_INTERNO, usuarioId, true),
    () => true,
  );
  const definirChatInternoHabilitado = useCallback((habilitado: boolean) => {
    definirPreferencia(CHAVE_BASE_CHAT_INTERNO, usuarioId, String(habilitado));
  }, [usuarioId]);
  return { chatInternoHabilitado, definirChatInternoHabilitado };
}

const DURACAO_PADRAO_SEGUNDOS = 3;
const DURACAO_MINIMA_SEGUNDOS = 1;
const DURACAO_MAXIMA_SEGUNDOS = 30;

function lerDuracao(usuarioId: string | null): number {
  const chave = chaveDaPreferencia(CHAVE_BASE_DURACAO, usuarioId);
  if (!chave || typeof window === "undefined") return DURACAO_PADRAO_SEGUNDOS;
  const valor = Number(window.localStorage.getItem(chave));
  return Number.isInteger(valor) && valor >= DURACAO_MINIMA_SEGUNDOS && valor <= DURACAO_MAXIMA_SEGUNDOS
    ? valor
    : DURACAO_PADRAO_SEGUNDOS;
}

export function usePreferenciaDuracaoDeNotificacao(): {
  duracaoSegundos: number;
  definirDuracaoSegundos: (segundos: number) => void;
} {
  const usuarioId = useAuthStore((estado) => estado.usuarioId);
  const duracaoSegundos = useSyncExternalStore(
    assinarMudancasDePreferencia,
    () => lerDuracao(usuarioId),
    () => DURACAO_PADRAO_SEGUNDOS,
  );
  const definirDuracaoSegundos = useCallback((segundos: number) => {
    if (!Number.isInteger(segundos) || segundos < DURACAO_MINIMA_SEGUNDOS || segundos > DURACAO_MAXIMA_SEGUNDOS) return;
    definirPreferencia(CHAVE_BASE_DURACAO, usuarioId, String(segundos));
  }, [usuarioId]);
  return { duracaoSegundos, definirDuracaoSegundos };
}

function lerPosicao(usuarioId: string | null): PosicaoDaNotificacao {
  const chave = chaveDaPreferencia(CHAVE_BASE_POSICAO, usuarioId);
  if (!chave || typeof window === "undefined") return "TOPO";
  return window.localStorage.getItem(chave) === "BAIXO" ? "BAIXO" : "TOPO";
}

export function usePreferenciaPosicaoDeNotificacao(): {
  posicao: PosicaoDaNotificacao;
  definirPosicao: (posicao: PosicaoDaNotificacao) => void;
} {
  const usuarioId = useAuthStore((estado) => estado.usuarioId);
  const posicao = useSyncExternalStore(
    assinarMudancasDePreferencia,
    () => lerPosicao(usuarioId),
    () => "TOPO" as PosicaoDaNotificacao,
  );
  const definirPosicao = useCallback((novaPosicao: PosicaoDaNotificacao) => {
    definirPreferencia(CHAVE_BASE_POSICAO, usuarioId, novaPosicao);
  }, [usuarioId]);
  return { posicao, definirPosicao };
}

function assinarMudancasDePreferencia(ouvinte: () => void): () => void {
  if (typeof window === "undefined") return () => undefined;
  window.addEventListener("storage", ouvinte);
  window.addEventListener(EVENTO_PREFERENCIA_ALTERADA, ouvinte);
  return () => {
    window.removeEventListener("storage", ouvinte);
    window.removeEventListener(EVENTO_PREFERENCIA_ALTERADA, ouvinte);
  };
}
