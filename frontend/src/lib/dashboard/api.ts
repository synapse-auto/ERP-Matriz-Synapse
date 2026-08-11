import { apiFetch } from "@/lib/api/http-client";

import type { FiltroDashboard, VisaoGeralDashboard } from "./types";

export function obterVisaoGeral(filtro: FiltroDashboard): Promise<VisaoGeralDashboard> {
  const parametros = new URLSearchParams({
    ano: String(filtro.ano),
    meses: filtro.meses.join(","),
  });
  if (filtro.origemInicio && filtro.origemFim) {
    parametros.set("origemInicio", filtro.origemInicio);
    parametros.set("origemFim", filtro.origemFim);
  }
  return apiFetch<VisaoGeralDashboard>(`/api/v1/dashboard/visao-geral?${parametros}`);
}
