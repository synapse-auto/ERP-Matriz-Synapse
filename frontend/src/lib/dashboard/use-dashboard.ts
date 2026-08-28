"use client";

import { useQuery } from "@tanstack/react-query";

import { obterVisaoGeral } from "./api";
import type { FiltroDashboard } from "./types";

export function useVisaoGeralDashboard(filtro: FiltroDashboard) {
  return useQuery({
    queryKey: ["dashboard", "visao-geral", filtro],
    queryFn: () => obterVisaoGeral(filtro),
    enabled:
      (filtro.meses.length > 0 || Boolean(filtro.inicio && filtro.fim))
      && ((!filtro.origemInicio && !filtro.origemFim)
        || Boolean(filtro.origemInicio && filtro.origemFim)),
  });
}
