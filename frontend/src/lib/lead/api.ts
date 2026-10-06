import { apiFetch } from "@/lib/api/http-client";

import type {
  AtualizacaoLead,
  CampoCustomizado,
  CanalResumo,
  EtapaAtendimento,
  LeadFicha,
  PaginaTimeline,
  TagDoLead,
  MidiaDoLead,
  UrlAssinadaDaMidia,
  SolicitacaoResumoIa,
} from "./types";

export function obterLead(id: string): Promise<LeadFicha> {
  return apiFetch<LeadFicha>(`/api/v1/leads/${id}`);
}

/** Ficha aberta a partir da Agenda colaborativa, sob o contexto de autorização da Agenda. */
export function obterLeadNaAgenda(id: string): Promise<LeadFicha> {
  return apiFetch<LeadFicha>(`/api/v1/leads/${id}/agenda`);
}

export function listarMidiasDoLead(
  id: string,
  pagina = 0,
  tamanho = 20,
  tipos?: readonly MidiaDoLead["tipo"][],
): Promise<MidiaDoLead[]> {
  const parametros = new URLSearchParams({ pagina: String(pagina), tamanho: String(tamanho) });
  if (tipos) parametros.set("tipos", tipos.join(","));
  return apiFetch<MidiaDoLead[]>(`/api/v1/leads/${id}/midias?${parametros}`);
}

export function emitirUrlAssinadaDaMidia(leadId: string, mensagemId: string): Promise<UrlAssinadaDaMidia> {
  return apiFetch<UrlAssinadaDaMidia>(`/api/v1/leads/${leadId}/midias/${mensagemId}/url`);
}

export function atualizarLead(id: string, dados: AtualizacaoLead): Promise<LeadFicha> {
  const rota = `/api/v1/leads/${id}`;
  return apiFetch<LeadFicha>(rota, {
    method: "PUT",
    body: JSON.stringify(dados),
  });
}

export function atualizarTelefoneLead(
  id: string,
  telefone: string,
  contexto: "padrao" | "agenda" = "padrao",
): Promise<LeadFicha> {
  const rota = contexto === "agenda" ? `/api/v1/leads/${id}/agenda/telefone` : `/api/v1/leads/${id}/telefone`;
  return apiFetch<LeadFicha>(rota, {
    method: "PUT",
    body: JSON.stringify({ telefone }),
  });
}

export function solicitarResumoIa(atendimentoId: string, solicitacaoId: string): Promise<SolicitacaoResumoIa> {
  return apiFetch<SolicitacaoResumoIa>(`/api/v1/atendimentos/${atendimentoId}/resumo-ia`, {
    method: "POST",
    headers: { "Idempotency-Key": solicitacaoId },
  });
}

export function obterEstadoResumoIa(atendimentoId: string): Promise<SolicitacaoResumoIa | null> {
  return apiFetch<SolicitacaoResumoIa | null>(`/api/v1/atendimentos/${atendimentoId}/resumo-ia`);
}

export function listarTagsDoLead(id: string): Promise<TagDoLead[]> {
  return apiFetch<TagDoLead[]>(`/api/v1/leads/${id}/tags`);
}

export function vincularTagAoLead(leadId: string, tagId: string): Promise<TagDoLead[]> {
  return apiFetch<TagDoLead[]>(`/api/v1/leads/${leadId}/tags/${tagId}`, { method: "PUT" });
}

export function desvincularTagDoLead(leadId: string, tagId: string): Promise<TagDoLead[]> {
  return apiFetch<TagDoLead[]>(`/api/v1/leads/${leadId}/tags/${tagId}`, { method: "DELETE" });
}

export function listarTimeline(leadId: string, pagina: number): Promise<PaginaTimeline> {
  return apiFetch<PaginaTimeline>(`/api/v1/leads/${leadId}/timeline?pagina=${pagina}`);
}

export function listarEtapas(): Promise<EtapaAtendimento[]> {
  return apiFetch<EtapaAtendimento[]>("/api/v1/etapas");
}

export function listarCamposCustomizados(): Promise<CampoCustomizado[]> {
  return apiFetch<CampoCustomizado[]>("/api/v1/campos-customizados");
}

export function listarCanais(): Promise<CanalResumo[]> {
  return apiFetch<CanalResumo[]>("/api/v1/canais");
}

export function listarTodasAsTags(): Promise<TagDoLead[]> {
  return apiFetch<TagDoLead[]>("/api/v1/tags");
}
