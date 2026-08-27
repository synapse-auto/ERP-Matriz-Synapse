"use client";

import { useInfiniteQuery, useMutation, useQueryClient } from "@tanstack/react-query";

import { enviarFeedback, listarFeedbacks } from "./api";
import type { CursorDeFeedbacks, TipoFeedback } from "./types";

export function useEnviarFeedback() {
  const cache = useQueryClient();
  return useMutation({
    mutationFn: enviarFeedback,
    onSuccess: () => cache.invalidateQueries({ queryKey: ["feedbacks", "administracao"] }),
  });
}

export function useFeedbacksAdministrativos(tipo: TipoFeedback | null) {
  return useInfiniteQuery({
    queryKey: ["feedbacks", "administracao", tipo],
    initialPageParam: null as CursorDeFeedbacks | null,
    queryFn: ({ pageParam }) => listarFeedbacks({ tipo, cursor: pageParam }),
    getNextPageParam: (pagina) =>
      pagina.proximoCriadoEm && pagina.proximoId
        ? { antesDe: pagina.proximoCriadoEm, antesDoId: pagina.proximoId }
        : undefined,
  });
}
