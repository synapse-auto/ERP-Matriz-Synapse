"use client";

import { useState } from "react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";

import { INTERVALO_REVALIDACAO_CACHE_MS } from "./tempos";
import { ErroDeApi } from "@/lib/api/errors";
import { useAuthStore } from "@/lib/auth/auth-store";

/**
 * QueryClient criado dentro do componente (não em module scope): em SSR, um client no escopo do
 * módulo seria compartilhado entre requisições concorrentes de usuários diferentes — cache de um
 * vazando para a tela de outro.
 */
export function QueryProvider({ children }: { children: React.ReactNode }) {
  const usuario=useAuthStore(estado=>estado.usuarioId);
  const papel=useAuthStore(estado=>estado.papel);
  return <CacheDaSessao key={`${usuario??"anonimo"}:${papel??"anonimo"}`}>{children}</CacheDaSessao>;
}

/** Outro ator/papel recebe outra instancia; respostas/mutations antigas continuam no cache antigo. */
function CacheDaSessao({ children }: { children: React.ReactNode }) {
  const [client] = useState(
    () =>
      new QueryClient({
        defaultOptions: {
          queries: {
            // ErroDeApi já chega com mensagem pronta pro usuário — uma tentativa extra antes de
            // desistir cobre falha de rede transitória sem mascarar erro de verdade.
            retry: (tentativas, erro) => tentativas < 1 && !(erro instanceof ErroDeApi && [401, 403].includes(erro.status)),
            staleTime: INTERVALO_REVALIDACAO_CACHE_MS,
          },
          // Comandos offline falham na tentativa explícita; não ficam na fila para reenviar
          // silenciosamente quando o navegador reconectar. Leituras mantêm o backoff do Query.
          mutations: { retry: false, networkMode: "always" },
        },
      }),
  );

  return <QueryClientProvider client={client}>{children}</QueryClientProvider>;
}
