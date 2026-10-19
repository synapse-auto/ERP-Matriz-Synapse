"use client";

import { useSyncExternalStore } from "react";

/** Recorte lg do layout: seis colunas nao cabem legivelmente em tablets. Nao muda a navegacao. */
const CONSULTA_CARTOES = "(max-width: 1023px)";

function assinar(notificar: () => void) {
  if (typeof window.matchMedia !== "function") return () => undefined;
  const consulta = window.matchMedia(CONSULTA_CARTOES);
  consulta.addEventListener("change", notificar);
  return () => consulta.removeEventListener("change", notificar);
}

function ler() {
  return typeof window.matchMedia === "function" && window.matchMedia(CONSULTA_CARTOES).matches;
}

export function useListaEmCartoes() {
  return useSyncExternalStore(assinar, ler, () => false);
}
