"use client";

import { useQuery } from "@tanstack/react-query";

import { emitirUrlAssinadaDaMidia } from "./api";

/** TTL da URL assinada é 5 minutos; recusa um pouco antes para não servir link morto. */
const STALE_MS = 2 * 60 * 1000;

export function useUrlAssinadaDaMidia(leadId: string, mensagemId: string, habilitado = true) {
  return useQuery({
    queryKey: ["midia-url-assinada", leadId, mensagemId],
    queryFn: () => emitirUrlAssinadaDaMidia(leadId, mensagemId),
    enabled: habilitado,
    staleTime: STALE_MS,
    gcTime: 5 * 60 * 1000,
  });
}
