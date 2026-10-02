"use client";

import { useAuthStore } from "@/lib/auth/auth-store";

import { SemAcessoACampanhas } from "./estados";

const PAPEIS_DE_GESTAO = ["GESTOR", "SUBGESTOR", "ADMINISTRADOR"];

/**
 * Guarda de rota: campanhas são da gestão (gestor, subgestor, administrador). Cobre a URL digitada à
 * mão; o backend recusa os demais com 403, então esta tela só evita mostrar o que não funcionaria.
 */
export function ExigeAcessoACampanhas({ children }: { children: React.ReactNode }) {
  const papel = useAuthStore((estado) => estado.papel);
  const status = useAuthStore((estado) => estado.status);
  if (status !== "autenticado") return null;
  if (!papel || !PAPEIS_DE_GESTAO.includes(papel)) return <SemAcessoACampanhas />;
  return children;
}
