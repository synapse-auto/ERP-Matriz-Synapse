import { apiFetch } from "@/lib/api/http-client";

import type { DadosDeTag, Tag } from "./types";

export function listarTags(): Promise<Tag[]> {
  return apiFetch<Tag[]>("/api/v1/tags");
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
