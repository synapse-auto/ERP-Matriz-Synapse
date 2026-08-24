import { apiFetch } from "@/lib/api/http-client";

import type { CatalogosDeFiltro, CampoFiltravel, CriterioRequisicao, LeadParaEntrada, PaginaDeLeads } from "./types";

/** Único critério sempre-verdadeiro: `criadoEm` nunca é nulo, então "sem filtro" vira isto. */
export const CRITERIO_SEM_FILTRO: CriterioRequisicao = {
  tipo: "SIMPLES",
  campo: "criadoEm",
  operador: "PREENCHIDO",
};

export function listarCamposFiltraveis(): Promise<CampoFiltravel[]> {
  return apiFetch<CampoFiltravel[]>("/api/v1/leads/filtrar/campos");
}

export function listarCatalogosDeFiltro(): Promise<CatalogosDeFiltro> {
  return apiFetch<CatalogosDeFiltro>("/api/v1/leads/filtrar/catalogos");
}

export function filtrarLeads(
  criterio: CriterioRequisicao,
  pagina: number,
  tamanho: number,
): Promise<PaginaDeLeads> {
  return apiFetch<PaginaDeLeads>("/api/v1/leads/filtrar", {
    method: "POST",
    body: JSON.stringify({ criterio, pagina, tamanho }),
  });
}

export async function contarLeads(criterio: CriterioRequisicao): Promise<number> {
  const resposta = await apiFetch<{ total: number }>("/api/v1/leads/filtrar/contagem", {
    method: "POST",
    body: JSON.stringify({ criterio }),
  });
  return resposta.total;
}

export function buscarLeadsParaEntrada(termo: string): Promise<LeadParaEntrada[]> {
  return apiFetch<LeadParaEntrada[]>(`/api/v1/leads/busca-entrada?termo=${encodeURIComponent(termo)}`);
}
