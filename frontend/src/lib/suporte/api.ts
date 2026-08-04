import { apiFetch } from "@/lib/api/http-client";

import type { DadosLembrete, Lembrete, PaginaLembretes, StatusLembrete } from "./types";

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
