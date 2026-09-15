"use client";

import { useAuthStore } from "@/lib/auth/auth-store";

/** FASE 2: trava temporária de teste — esconde os controles de preenchimento para não-ADM. */
export function SomenteAdministrador({ children }: { children: React.ReactNode }) {
  const papel = useAuthStore((estado) => estado.papel);
  if (papel !== "ADMINISTRADOR") return null;
  return <>{children}</>;
}
