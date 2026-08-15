import type { InfiniteData, QueryClient } from "@tanstack/react-query";

import type { MensagemResposta, PaginaMensagens } from "./types";

export type DadosDoHistorico = InfiniteData<PaginaMensagens, string | null>;
export type ChaveDoHistorico = readonly ["mensagens", string | null];

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
