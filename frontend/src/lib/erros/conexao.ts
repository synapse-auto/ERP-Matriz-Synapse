"use client";

import { useSyncExternalStore } from "react";
import { ErroDeApi } from "@/lib/api/errors";

function acompanharConexao(avisar: () => void) {
  window.addEventListener("online", avisar);
  window.addEventListener("offline", avisar);
  return () => {
    window.removeEventListener("online", avisar);
    window.removeEventListener("offline", avisar);
  };
}

export function useOnline() {
  return useSyncExternalStore(acompanharConexao, () => navigator.onLine, () => true);
}

/** Não infere queda global a partir de uma funcionalidade ou de 401/403. */
export function classificarFalhaDeLeitura(erro: unknown, online: boolean) {
  if (erro instanceof ErroDeApi && [401, 403].includes(erro.status)) return "acesso";
  if (!online) return "offline";
  if (erro instanceof ErroDeApi && [502, 503, 504].includes(erro.status)) return "backend";
  if (erro instanceof TypeError) return "conexao";
  return "renderizacao";
}

export function registrarErroDeTela(escopo: string, erro: Error & { digest?: string }) {
  // Não registra mensagem, stack, URL, payload, usuário nem tokens, mesmo em erros do cliente.
  console.error("[tela] Falha de renderização", {
    escopo,
    digest: erro.digest?.match(/^[a-zA-Z0-9_-]{1,100}$/)?.[0] ?? null,
  });
}
