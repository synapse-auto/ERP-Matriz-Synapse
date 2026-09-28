"use client";

import { useQuery } from "@tanstack/react-query";

import { apiFetch } from "@/lib/api/http-client";

/**
 * Feature flags LIGADAS nesta instância (`GET /api/v1/config/features`, FeatureService.habilitadas).
 * Flag desligada — ou ausente da tabela — nunca aparece na resposta, então "a chave está na lista"
 * já é a checagem completa: não existe um `{flag: false}` para filtrar.
 */
export function useFuncionalidadesHabilitadas() {
  return useQuery({
    queryKey: ["config", "features"],
    queryFn: () => apiFetch<string[]>("/api/v1/config/features"),
  });
}
