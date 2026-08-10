import { apiFetch } from "@/lib/api/http-client";

import type { AgregacaoDeTags, DadosDeTag, Tag } from "./types";

export function listarTags(): Promise<Tag[]> {
  return apiFetch<Tag[]>("/api/v1/tags");
}

/** Mini-dashboard de Tags (E17b §Bloco 5): tag mais usada, % de leads tagueados, contagem por tag. */
export function obterAgregacaoDeTags(): Promise<AgregacaoDeTags> {
  return apiFetch<AgregacaoDeTags>("/api/v1/tags/agregacao");
}

export function criarTag(dados: DadosDeTag): Promise<Tag> {
  return apiFetch<Tag>("/api/v1/tags", {
    method: "POST",
    body: JSON.stringify(dados),
  });
}

export function atualizarTag(id: string, dados: DadosDeTag): Promise<Tag> {
  return apiFetch<Tag>(`/api/v1/tags/${id}`, {
    method: "PUT",
    body: JSON.stringify(dados),
  });
}

export function removerTag(id: string): Promise<void> {
  return apiFetch<void>(`/api/v1/tags/${id}`, { method: "DELETE" });
}
