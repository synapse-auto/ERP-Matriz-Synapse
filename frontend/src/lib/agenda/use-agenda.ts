"use client";

import { useQuery } from "@tanstack/react-query";

import { CRITERIO_SEM_FILTRO, contarLeads, filtrarLeads, listarCamposFiltraveis } from "./api";
import type { CriterioRequisicao, FiltroAtivo } from "./types";

const TAMANHO_PAGINA = 50;

/** Combina os filtros ativos com E — nunca OR: cada chip só reduz o que a tela mostra. */
export function criterioDosFiltrosAtivos(filtros: FiltroAtivo[]): CriterioRequisicao {
  if (filtros.length === 0) {
    return CRITERIO_SEM_FILTRO;
  }
  const folhas: CriterioRequisicao[] = filtros.map((filtro) => ({
    tipo: "SIMPLES",
    campo: filtro.campo.apelido,
    operador: filtro.operador,
    ...(filtro.valores ? { valores: filtro.valores } : { valor: filtro.valor }),
  }));
  if (folhas.length === 1) {
    return folhas[0];
  }
  return { tipo: "COMPOSTO", conector: "AND", criterios: folhas };
}

export function useCamposFiltraveis() {
  return useQuery({ queryKey: ["agenda", "campos"], queryFn: listarCamposFiltraveis });
}

export function useLeadsDaAgenda(filtros: FiltroAtivo[], pagina: number) {
  const criterio = criterioDosFiltrosAtivos(filtros);
  return useQuery({
    queryKey: ["agenda", "leads", criterio, pagina],
    queryFn: () => filtrarLeads(criterio, pagina, TAMANHO_PAGINA),
  });
}

export function useContagemDeLeads(filtros: FiltroAtivo[]) {
  const criterio = criterioDosFiltrosAtivos(filtros);
  return useQuery({
    queryKey: ["agenda", "contagem", criterio],
    queryFn: () => contarLeads(criterio),
  });
}

export { TAMANHO_PAGINA };
