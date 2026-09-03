"use client";

import { useQuery } from "@tanstack/react-query";

import type { TagDoLead } from "@/lib/lead/types";

import {
  CRITERIO_SEM_FILTRO,
  contarLeads,
  filtrarLeads,
  listarCamposFiltraveis,
  listarCatalogosDeFiltro,
} from "./api";
import type {
  CriterioRequisicao,
  CriterioSimplesRequisicao,
  FiltroAtivo,
  FiltrosRapidosAgenda,
} from "./types";

const TAMANHO_PAGINA = 50;
const SEM_RESPONSAVEL = "__sem_responsavel__";

function criterioEm(campo: string, valores: string[]): CriterioSimplesRequisicao | null {
  return valores.length > 0 ? { tipo: "SIMPLES", campo, operador: "EM", valores } : null;
}

function criterioDosAtendentes(valores: string[]): CriterioRequisicao | null {
  const semResponsavel = valores.includes(SEM_RESPONSAVEL);
  const ids = valores.filter((valor) => valor !== SEM_RESPONSAVEL);
  const partes: CriterioRequisicao[] = [];
  const porIds = criterioEm("atendenteResponsavel", ids);
  if (porIds) partes.push(porIds);
  if (semResponsavel) {
    partes.push({ tipo: "SIMPLES", campo: "atendenteResponsavel", operador: "VAZIO" });
  }
  if (partes.length === 0) return null;
  return partes.length === 1 ? partes[0] : { tipo: "COMPOSTO", conector: "OU", criterios: partes };
}

function criterioDaBusca(busca: string, tags: TagDoLead[]): CriterioRequisicao | null {
  const valor = busca.trim();
  if (!valor) return null;

  const criterios: CriterioRequisicao[] = ["nome", "telefone", "cpf"].map((campo) => ({
    tipo: "SIMPLES",
    campo,
    operador: "CONTEM",
    valor,
  }));
  const buscaNormalizada = valor.toLocaleLowerCase("pt-BR");
  const tagsCorrespondentes = tags
    .filter((tag) => tag.nome.toLocaleLowerCase("pt-BR").includes(buscaNormalizada))
    .map((tag) => tag.id);
  const porTag = criterioEm("tag", tagsCorrespondentes);
  if (porTag) criterios.push(porTag);

  return { tipo: "COMPOSTO", conector: "OU", criterios };
}

/** Compõe busca em OU e todos os grupos rápidos/avançados em E, no contrato já existente. */
export function criterioDosFiltrosAtivos(
  rapidos: FiltrosRapidosAgenda,
  avancados: FiltroAtivo[],
  tags: TagDoLead[],
): CriterioRequisicao {
  const criterios: CriterioRequisicao[] = [];
  const busca = criterioDaBusca(rapidos.busca, tags);
  if (busca) criterios.push(busca);

  const grupos = [
    criterioEm("etapa", rapidos.etapas),
    criterioDosAtendentes(rapidos.atendentes),
    criterioEm("localizacao", rapidos.cidades),
    criterioEm("tag", rapidos.tags),
  ];
  criterios.push(...grupos.filter((criterio): criterio is CriterioRequisicao => criterio !== null));

  criterios.push(
    ...avancados.map(
      (filtro): CriterioRequisicao => ({
        tipo: "SIMPLES",
        campo: filtro.campo.apelido,
        operador: filtro.operador,
        ...(filtro.valores ? { valores: filtro.valores } : { valor: filtro.valor }),
      }),
    ),
  );

  if (criterios.length === 0) return CRITERIO_SEM_FILTRO;
  if (criterios.length === 1) return criterios[0];
  return { tipo: "COMPOSTO", conector: "E", criterios };
}

export function useCamposFiltraveis() {
  return useQuery({ queryKey: ["agenda", "campos"], queryFn: listarCamposFiltraveis });
}

export function useCatalogosDeFiltro() {
  return useQuery({ queryKey: ["agenda", "catalogos"], queryFn: listarCatalogosDeFiltro });
}

export function useLeadsDaAgenda(
  rapidos: FiltrosRapidosAgenda,
  avancados: FiltroAtivo[],
  tags: TagDoLead[],
  pagina: number,
) {
  const criterio = criterioDosFiltrosAtivos(rapidos, avancados, tags);
  return useQuery({
    queryKey: ["agenda", "leads", criterio, pagina],
    queryFn: () => filtrarLeads(criterio, pagina, TAMANHO_PAGINA),
  });
}

export function useContagemDeLeads(
  rapidos: FiltrosRapidosAgenda,
  avancados: FiltroAtivo[],
  tags: TagDoLead[],
) {
  const criterio = criterioDosFiltrosAtivos(rapidos, avancados, tags);
  return useQuery({
    queryKey: ["agenda", "contagem", criterio],
    queryFn: () => contarLeads(criterio),
  });
}

export { SEM_RESPONSAVEL, TAMANHO_PAGINA };
