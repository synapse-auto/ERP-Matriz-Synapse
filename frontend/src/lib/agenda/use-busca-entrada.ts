"use client";

import { useCallback, useSyncExternalStore } from "react";

import { buscarLeadsParaEntrada } from "./api";
import type { LeadParaEntrada } from "./types";

type Estado = { dados: LeadParaEntrada[]; carregando: boolean };
const estados = new Map<string, Estado>();
const ouvintes = new Map<string, Set<() => void>>();
const vazio: Estado = { dados: [], carregando: false };

function atualizar(termo: string, estado: Estado) {
  estados.set(termo, estado);
  ouvintes.get(termo)?.forEach((ouvinte) => ouvinte());
}

function iniciar(termo: string) {
  if (termo.trim().length < 2 || estados.has(termo)) return;
  estados.set(termo, { dados: [], carregando: true });
  buscarLeadsParaEntrada(termo)
    .then((dados) => atualizar(termo, { dados, carregando: false }))
    .catch(() => atualizar(termo, { dados: [], carregando: false }));
}

export function useBuscaLeadsParaEntrada(termo: string) {
  iniciar(termo);
  const assinar = useCallback(
    (ouvinte: () => void) => {
      const lista = ouvintes.get(termo) ?? new Set<() => void>();
      lista.add(ouvinte);
      ouvintes.set(termo, lista);
      return () => lista.delete(ouvinte);
    },
    [termo],
  );
  const ler = useCallback(() => estados.get(termo) ?? vazio, [termo]);
  const estado = useSyncExternalStore(assinar, ler, () => vazio);
  return { data: estado.dados, isLoading: estado.carregando };
}
