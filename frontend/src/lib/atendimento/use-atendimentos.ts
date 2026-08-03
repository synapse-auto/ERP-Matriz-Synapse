"use client";

import { useQuery } from "@tanstack/react-query";

import { listarAtendimentos } from "./api";
import type { VisaoAtendimento } from "./types";

export function useAtendimentos(visao: VisaoAtendimento) {
  return useQuery({
    queryKey: ["atendimentos", visao],
    queryFn: () => listarAtendimentos(visao),
  });
}
