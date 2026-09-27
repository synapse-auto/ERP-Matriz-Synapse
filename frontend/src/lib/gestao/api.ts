import { apiFetch } from "@/lib/api/http-client";

import type {
  Catalogo,
  Gravacao,
  MinhasPermissoes,
  Papel,
  Perfil,
  PermissoesDeUsuario,
  PreviaDeCopia,
  Rascunho,
  ResumoDeUsuario,
} from "./types";

const BASE = "/api/v1/gestao/permissoes";

export const obterMinhasPermissoes = () => apiFetch<MinhasPermissoes>(`${BASE}/minhas`);
export const obterCatalogo = () => apiFetch<Catalogo>(`${BASE}/catalogo`);
export const listarPerfis = () => apiFetch<Perfil[]>(`${BASE}/perfis`);
export const listarPermissoesDaEquipe = () => apiFetch<ResumoDeUsuario[]>(`${BASE}/usuarios`);
export const obterPermissoesDeUsuario = (id: string) => apiFetch<PermissoesDeUsuario>(`${BASE}/usuarios/${id}`);

export function salvarPerfil(papel: Papel, revisaoEsperada: number, rascunho: Rascunho, copiadoDe?: Papel) {
  return apiFetch<Gravacao>(`${BASE}/perfis/${papel}`, {
    method: "PUT",
    body: JSON.stringify({ revisaoEsperada, ...rascunho, copiadoDe: copiadoDe ?? null }),
  });
}

export function salvarExcecoes(id: string, revisaoEsperada: number, rascunho: Rascunho, copiadoDe?: string) {
  return apiFetch<Gravacao>(`${BASE}/usuarios/${id}/excecoes`, {
    method: "PUT",
    body: JSON.stringify({ revisaoEsperada, ...rascunho, copiadoDe: copiadoDe ?? null }),
  });
}

export function restaurarPadrao(id: string, revisaoEsperada: number) {
  return apiFetch<Gravacao>(`${BASE}/usuarios/${id}/excecoes?revisaoEsperada=${revisaoEsperada}`, { method: "DELETE" });
}

export function preverCopiaDePerfil(destino: Papel, origem: Papel) {
  return apiFetch<PreviaDeCopia>(`${BASE}/perfis/${destino}/copia/previa`, {
    method: "POST",
    body: JSON.stringify({ origem }),
  });
}

export function preverCopiaDeUsuario(destino: string, origemUsuarioId: string) {
  return apiFetch<PreviaDeCopia>(`${BASE}/usuarios/${destino}/copia/previa`, {
    method: "POST",
    body: JSON.stringify({ origemUsuarioId }),
  });
}
