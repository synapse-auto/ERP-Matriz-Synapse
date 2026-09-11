"use client";

import { useCallback, useSyncExternalStore } from "react";

import { useAuthStore } from "@/lib/auth/auth-store";

const CHAVE_BASE = "synapse:preferencias-notificacoes:som";
const EVENTO_PREFERENCIA_ALTERADA = "synapse:preferencia-notificacao-alterada";

function chaveDaPreferencia(usuarioId: string | null): string | null {
  return usuarioId ? `${CHAVE_BASE}:${usuarioId}` : null;
}

function lerPreferencia(usuarioId: string | null): boolean {
  const chave = chaveDaPreferencia(usuarioId);
  if (!chave || typeof window === "undefined") return true;
  return window.localStorage.getItem(chave) !== "false";
}

export function usePreferenciaSomDeNotificacao(): {
  somHabilitado: boolean;
  definirSomHabilitado: (habilitado: boolean) => void;
} {
  const usuarioId = useAuthStore((estado) => estado.usuarioId);
  const somHabilitado = useSyncExternalStore(
    assinarMudancasDePreferencia,
    () => lerPreferencia(usuarioId),
    () => true,
  );

  const definirSomHabilitado = useCallback((habilitado: boolean) => {
    const chave = chaveDaPreferencia(usuarioId);
    if (!chave || typeof window === "undefined") return;
    window.localStorage.setItem(chave, String(habilitado));
    window.dispatchEvent(new Event(EVENTO_PREFERENCIA_ALTERADA));
  }, [usuarioId]);

  return { somHabilitado, definirSomHabilitado };
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
