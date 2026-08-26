import type { InfiniteData, QueryClient } from "@tanstack/react-query";

import { useAuthStore } from "@/lib/auth/auth-store";

import type { MensagemResposta, PaginaMensagens } from "./types";

export type DadosDoHistorico = InfiniteData<PaginaMensagens, string | null>;
export type ChaveDoHistorico = readonly ["mensagens", string | null];

export interface IdentidadeAutenticada {
  id: string | null;
  nome: string | null;
}

/** A resposta do envio não traz autoria; usa a identidade autenticada já carregada pela sessão. */
export function identidadeAutenticada(queryClient: QueryClient): IdentidadeAutenticada {
  const id = useAuthStore.getState().usuarioId;
  const meuUsuario = queryClient.getQueryData<{ id?: string; nome?: string }>(["me"]);
  return {
    id: id ?? meuUsuario?.id ?? null,
    nome: meuUsuario?.nome ?? null,
  };
}

/**
 * Atualiza a página mais recente do histórico paginado por cursor.
 *
 * `useInfiniteQuery` mantém o cache como `InfiniteData`, nunca como um array plano. Centralizar essa
 * operação impede que atualizações otimistas, WebSocket e reconciliação discordem sobre a forma do
 * mesmo cache.
 */
export function atualizarPaginaRecente(
  queryClient: QueryClient,
  queryKey: ChaveDoHistorico,
  atualizador: (mensagensAtuais: MensagemResposta[]) => MensagemResposta[],
): void {
  queryClient.setQueryData<DadosDoHistorico>(queryKey, (atual) => {
    if (!atual || atual.pages.length === 0) return atual;
    const pages = [...atual.pages];
    pages[0] = { ...pages[0], mensagens: atualizador(pages[0].mensagens) };
    return { ...atual, pages };
  });
}
