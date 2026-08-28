"use client";

import { useSyncExternalStore } from "react";

/** Mesmo recorte da sidebar recolhida: abaixo disto o shell vira a versão enxuta. */
export const CONSULTA_TELA_ESTREITA = "(max-width: 639px)";

function assinarMudancaDeViewport(notificar: () => void) {
  if (typeof window.matchMedia !== "function") return () => undefined;
  const consulta = window.matchMedia(CONSULTA_TELA_ESTREITA);
  consulta.addEventListener("change", notificar);
  return () => consulta.removeEventListener("change", notificar);
}

function telaEstreitaNoCliente() {
  return typeof window.matchMedia === "function"
    ? window.matchMedia(CONSULTA_TELA_ESTREITA).matches
    : false;
}

function telaEstreitaNoServidor() {
  return false;
}

export function useTelaEstreita(): boolean {
  return useSyncExternalStore(
    assinarMudancaDeViewport,
    telaEstreitaNoCliente,
    telaEstreitaNoServidor,
  );
}
