"use client";

import { useQuery } from "@tanstack/react-query";

import { obterConfiguracaoComposer } from "./api";

export function useConfiguracaoComposer() {
  return useQuery({
    queryKey: ["configuracao-composer"],
    queryFn: obterConfiguracaoComposer,
    staleTime: 5 * 60 * 1000,
  });
}
