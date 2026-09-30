"use client";

import { useEffect, useState } from "react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";

import { useAuthStore } from "@/lib/auth/auth-store";

/**
 * O cache pertence a quem estava logado quando ele foi preenchido. Trocar de usuário (ou sair)
 * sem recarregar a página — sessão expirada que volta ao /login pelo roteador e outra pessoa
 * entra — não pode herdar respostas autorizadas para a sessão anterior. A renovação do token do
 * mesmo usuário não limpa nada.
 */
export function deveLimparCacheAoTrocarSessao(anterior: string | null, atual: string | null) {
  return anterior !== null && anterior !== atual;
}

function useLimparCacheAoTrocarSessao(client: QueryClient) {
  useEffect(
    () =>
      useAuthStore.subscribe((estado, estadoAnterior) => {
        if (deveLimparCacheAoTrocarSessao(estadoAnterior.usuarioId, estado.usuarioId)) {
          client.clear();
        }
      }),
    [client],
  );
}

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
  useLimparCacheAoTrocarSessao(client);

  return <QueryClientProvider client={client}>{children}</QueryClientProvider>;
}
