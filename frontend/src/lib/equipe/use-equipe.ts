"use client";

import { useQuery } from "@tanstack/react-query";

import { listarEquipe } from "./api";

/**
 * Extraída como hook próprio (em vez de `useQuery` inline no componente) para ficar mockável nos
 * testes de tela no mesmo padrão do resto do projeto — cada fonte de dado tem um hook, nunca
 * `useQuery` cru espalhado pelos componentes.
 */
export function useEquipe() {
  return useQuery({ queryKey: ["equipe"], queryFn: listarEquipe });
}
