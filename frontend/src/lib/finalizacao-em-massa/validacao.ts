import type { FiltroDeFinalizacao } from "./types";

export type ProblemaDoFiltro = "SEM_ATENDENTE" | "SEM_PERIODO" | "PERIODO_INVERTIDO";

/**
 * Conferencia so para nao disparar uma previa que o servidor recusaria de qualquer jeito. O servidor revalida tudo
 * (existencia e acesso dos atendentes, limites, janela) e e a unica autoridade.
 */
export function problemaDoFiltro(filtro: FiltroDeFinalizacao): ProblemaDoFiltro | null {
  if (filtro.atendenteIds.length === 0) return "SEM_ATENDENTE";
  if (!filtro.de || !filtro.ate) return "SEM_PERIODO";
  if (filtro.de > filtro.ate) return "PERIODO_INVERTIDO";
  if (filtro.de === filtro.ate && filtro.horaInicio && filtro.horaFim && filtro.horaFim < filtro.horaInicio) {
    return "PERIODO_INVERTIDO";
  }
  return null;
}

/** Hora vazia vira `null`: "sem horario" e diferente de "00:00" para quem le o relatorio depois. */
export function filtroNormalizado(filtro: FiltroDeFinalizacao): FiltroDeFinalizacao {
  return {
    ...filtro,
    atendenteIds: [...filtro.atendenteIds].sort(),
    horaInicio: filtro.horaInicio || null,
    horaFim: filtro.horaFim || null,
  };
}

export function novaChaveDeIdempotencia(): string {
  return `fm-${globalThis.crypto.randomUUID()}`;
}
