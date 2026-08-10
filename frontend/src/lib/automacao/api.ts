import { apiFetch } from "@/lib/api/http-client";

import type { ParametroAutomacao, StatusAutomacaoTelemetria } from "./types";

export function listarConfiguracaoAutomacao(): Promise<ParametroAutomacao[]> {
  return apiFetch<ParametroAutomacao[]>("/api/v1/automacao/config");
}

/** Os quatro cards do topo da tela de Automação (E17b §Bloco 5). */
export function obterTelemetriaAutomacao(): Promise<StatusAutomacaoTelemetria> {
  return apiFetch<StatusAutomacaoTelemetria>("/api/v1/automacao/telemetria");
}

export function atualizarParametroAutomacao(
  chave: string,
  valor: string,
): Promise<ParametroAutomacao> {
  return apiFetch<ParametroAutomacao>(`/api/v1/automacao/config/${chave}`, {
    method: "PUT",
    body: JSON.stringify({ valor }),
  });
}
