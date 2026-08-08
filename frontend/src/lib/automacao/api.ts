import { apiFetch } from "@/lib/api/http-client";

import type { ParametroAutomacao } from "./types";

export function listarConfiguracaoAutomacao(): Promise<ParametroAutomacao[]> {
  return apiFetch<ParametroAutomacao[]>("/api/v1/automacao/config");
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
