import { apiFetch } from "@/lib/api/http-client";

import type { DadosLembrete, DadosMensagemProgramada, Lembrete, MensagemProgramada,
  PaginaLembretes, PaginaMensagensProgramadas, StatusLembrete, StatusMensagemProgramada } from "./types";

export function listarLembretes(filtros: {
  inicio?: string;
  fim?: string;
  status?: StatusLembrete;
  pagina: number;
}): Promise<PaginaLembretes> {
  const query = new URLSearchParams({ pagina: String(filtros.pagina) });
  if (filtros.inicio) query.set("inicio", filtros.inicio);
  if (filtros.fim) query.set("fim", filtros.fim);
  if (filtros.status) query.set("status", filtros.status);
  return apiFetch<PaginaLembretes>(`/api/v1/lembretes?${query}`);
}

export function criarLembrete(dados: DadosLembrete): Promise<Lembrete> {
  return apiFetch<Lembrete>("/api/v1/lembretes", {
    method: "POST",
    body: JSON.stringify(dados),
  });
}

export function atualizarLembrete(
  lembrete: Lembrete,
  status: StatusLembrete,
): Promise<Lembrete> {
  return apiFetch<Lembrete>(`/api/v1/lembretes/${lembrete.id}`, {
    method: "PUT",
    body: JSON.stringify({ texto: lembrete.texto, dataHora: lembrete.dataHora, status }),
  });
}

export function removerLembrete(id: string): Promise<void> {
  return apiFetch<void>(`/api/v1/lembretes/${id}`, { method: "DELETE" });
}

export function listarMensagensProgramadas(f: { inicio?: string; fim?: string; status?: StatusMensagemProgramada; pagina: number }): Promise<PaginaMensagensProgramadas> {
  const q = new URLSearchParams({ pagina: String(f.pagina) });
  if (f.inicio) q.set("inicio", f.inicio); if (f.fim) q.set("fim", f.fim); if (f.status) q.set("status", f.status);
  return apiFetch<PaginaMensagensProgramadas>(`/api/v1/mensagens-programadas?${q}`);
}
export function criarMensagemProgramada(d: DadosMensagemProgramada): Promise<MensagemProgramada> {
  return apiFetch<MensagemProgramada>("/api/v1/mensagens-programadas", { method: "POST", body: JSON.stringify(d) });
}
export function editarMensagemProgramada(id: string, d: Omit<DadosMensagemProgramada, "leadId">): Promise<MensagemProgramada> {
  return apiFetch<MensagemProgramada>(`/api/v1/mensagens-programadas/${id}`, { method: "PUT", body: JSON.stringify(d) });
}
export function cancelarMensagemProgramada(id: string): Promise<MensagemProgramada> {
  return apiFetch<MensagemProgramada>(`/api/v1/mensagens-programadas/${id}/cancelar`, { method: "POST" });
}
