import { apiFetch } from "@/lib/api/http-client";

import type { ParametroAutomacao, RegraFidelizacao, RegraFollowUp, StatusAutomacaoTelemetria } from "./types";

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

export function listarRegrasFollowUp(): Promise<RegraFollowUp[]> { return apiFetch("/api/v1/automacao/follow-ups"); }
export function criarRegraFollowUp(body: Omit<RegraFollowUp, "id" | "nome">): Promise<RegraFollowUp> { return apiFetch("/api/v1/automacao/follow-ups", { method: "POST", body: JSON.stringify(body) }); }
export function atualizarRegraFollowUp(id: string, body: Omit<RegraFollowUp, "id" | "nome">): Promise<RegraFollowUp> { return apiFetch(`/api/v1/automacao/follow-ups/${id}`, { method: "PUT", body: JSON.stringify(body) }); }
export function alternarRegraFollowUp(id: string, ativo: boolean): Promise<RegraFollowUp> { return apiFetch(`/api/v1/automacao/follow-ups/${id}/ativo`, { method: "PATCH", body: JSON.stringify({ ativo }) }); }
export function excluirRegraFollowUp(id: string): Promise<void> { return apiFetch(`/api/v1/automacao/follow-ups/${id}`, { method: "DELETE" }); }
export function listarRegrasFidelizacao(): Promise<RegraFidelizacao[]> { return apiFetch("/api/v1/automacao/fidelizacao"); }
export function criarRegraFidelizacao(body: Omit<RegraFidelizacao, "id">): Promise<RegraFidelizacao> { return apiFetch("/api/v1/automacao/fidelizacao", { method: "POST", body: JSON.stringify(body) }); }
export function atualizarRegraFidelizacao(id: string, body: Omit<RegraFidelizacao, "id">): Promise<RegraFidelizacao> { return apiFetch(`/api/v1/automacao/fidelizacao/${id}`, { method: "PUT", body: JSON.stringify(body) }); }
export function alternarRegraFidelizacao(id: string, ativo: boolean): Promise<RegraFidelizacao> { return apiFetch(`/api/v1/automacao/fidelizacao/${id}/ativo`, { method: "PATCH", body: JSON.stringify({ ativo }) }); }
export function excluirRegraFidelizacao(id: string): Promise<void> { return apiFetch(`/api/v1/automacao/fidelizacao/${id}`, { method: "DELETE" }); }
