"use client";

import { useQuery } from "@tanstack/react-query";

import { listarEquipe, obterMeuUsuario } from "./api";

/**
 * Extraída como hook próprio (em vez de `useQuery` inline no componente) para ficar mockável nos
 * testes de tela no mesmo padrão do resto do projeto — cada fonte de dado tem um hook, nunca
 * `useQuery` cru espalhado pelos componentes.
 */
export function useEquipe() {
  return useQuery({ queryKey: ["equipe"], queryFn: listarEquipe });
}

/** GET /api/v1/me (E17) — nome, papel e presença de quem está autenticado. */
export function useMeuUsuario() {
  return useQuery({ queryKey: ["me"], queryFn: obterMeuUsuario });
}
