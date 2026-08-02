"use client";

import { useState } from "react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";

/**
 * QueryClient criado dentro do componente (não em module scope): em SSR, um client no escopo do
 * módulo seria compartilhado entre requisições concorrentes de usuários diferentes — cache de um
 * vazando para a tela de outro.
 */
export function QueryProvider({ children }: { children: React.ReactNode }) {
  const [client] = useState(
    () =>
      new QueryClient({
        defaultOptions: {
          queries: {
            // ErroDeApi já chega com mensagem pronta pro usuário — uma tentativa extra antes de
            // desistir cobre falha de rede transitória sem mascarar erro de verdade.
            retry: 1,
            staleTime: 30_000,
          },
        },
      }),
  );

  return <QueryClientProvider client={client}>{children}</QueryClientProvider>;
}
