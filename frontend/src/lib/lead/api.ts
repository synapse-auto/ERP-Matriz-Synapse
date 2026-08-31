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
} from "./types";

export function obterLead(id: string): Promise<LeadFicha> {
  return apiFetch<LeadFicha>(`/api/v1/leads/${id}`);
}

export function listarMidiasDoLead(id: string, pagina = 0, tamanho = 20): Promise<MidiaDoLead[]> {
  return apiFetch<MidiaDoLead[]>(`/api/v1/leads/${id}/midias?pagina=${pagina}&tamanho=${tamanho}`);
}

export function emitirUrlAssinadaDaMidia(leadId: string, mensagemId: string): Promise<UrlAssinadaDaMidia> {
  return apiFetch<UrlAssinadaDaMidia>(`/api/v1/leads/${leadId}/midias/${mensagemId}/url`);
}

export function atualizarLead(id: string, dados: AtualizacaoLead): Promise<LeadFicha> {
  return apiFetch<LeadFicha>(`/api/v1/leads/${id}`, {
    method: "PUT",
    body: JSON.stringify(dados),
  });
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
