"use client";

import { useEffect } from "react";

import { useAuthStore } from "@/lib/auth/auth-store";

/**
 * O próximo frame acontece depois de o shell montar e seus hooks de consulta terem sido
 * disparados. Assim a transição cobre o intervalo verdadeiro sem esperar uma resposta de dados
 * auxiliares para liberar a navegação.
 */
export function SinalizadorShellPronto() {
  const concluirAberturaDoPainel = useAuthStore((estado) => estado.concluirAberturaDoPainel);

  useEffect(() => {
    const quadro = window.requestAnimationFrame(concluirAberturaDoPainel);
    return () => window.cancelAnimationFrame(quadro);
  }, [concluirAberturaDoPainel]);

  return null;
}
