import type { QueryClient } from "@tanstack/react-query";

import type { ResumoReacao } from "@/lib/atendimento/types";
import type { ChatMensagem, PaginaChatMensagens } from "@/lib/chat-interno/types";

import type { ChaveDoHistorico, DadosDoHistorico } from "./cache-mensagens";

export interface ResumoReacaoPublico {
  emoji: string;
  quantidade: number;
}

/** Substitui quantidades do evento; preserva `reagi` local. Nunca incrementa. */
export function aplicarResumoPublico(
  atuais: ResumoReacao[] | undefined,
  publicos: ResumoReacaoPublico[],
): ResumoReacao[] {
  const meu = atuais?.find((item) => item.reagi)?.emoji;
  return publicos.map((item) => ({
    emoji: item.emoji,
    quantidade: item.quantidade,
    reagi: item.emoji === meu,
  }));
}

export function atualizarReacoesDoHistorico(
  queryClient: QueryClient,
  queryKey: ChaveDoHistorico,
  mensagemId: string,
  publicos: ResumoReacaoPublico[],
): void {
  queryClient.setQueryData<DadosDoHistorico>(queryKey, (atual) => {
    if (!atual) return atual;
    return {
      ...atual,
      pages: atual.pages.map((pagina) => ({
        ...pagina,
        mensagens: pagina.mensagens.map((mensagem) =>
          mensagem.id === mensagemId
            ? { ...mensagem, reacoes: aplicarResumoPublico(mensagem.reacoes, publicos) }
            : mensagem,
        ),
      })),
    };
  });
}

export function atualizarReacoesDoChatInterno(
  queryClient: QueryClient,
  conversaId: string,
  mensagemId: string,
  publicos: ResumoReacaoPublico[],
): void {
  queryClient.setQueryData<PaginaChatMensagens>(["chat-interno", "mensagens", conversaId], (atual) => {
    if (!atual) return atual;
    return {
      ...atual,
      mensagens: atual.mensagens.map((mensagem: ChatMensagem) =>
        mensagem.id === mensagemId
          ? { ...mensagem, reacoes: aplicarResumoPublico(mensagem.reacoes, publicos) }
          : mensagem,
      ),
    };
  });
}

export function substituirReacoesDoHistorico(
  queryClient: QueryClient,
  queryKey: ChaveDoHistorico,
  mensagemId: string,
  reacoes: ResumoReacao[],
): void {
  queryClient.setQueryData<DadosDoHistorico>(queryKey, (atual) => {
    if (!atual) return atual;
    return {
      ...atual,
      pages: atual.pages.map((pagina) => ({
        ...pagina,
        mensagens: pagina.mensagens.map((mensagem) =>
          mensagem.id === mensagemId ? { ...mensagem, reacoes } : mensagem,
        ),
      })),
    };
  });
}

export function substituirReacoesDoChatInterno(
  queryClient: QueryClient,
  conversaId: string,
  mensagemId: string,
  reacoes: ResumoReacao[] | undefined,
): void {
  const resumo = reacoes ?? [];
  queryClient.setQueryData<PaginaChatMensagens>(["chat-interno", "mensagens", conversaId], (atual) => {
    if (!atual) return atual;
    return {
      ...atual,
      mensagens: atual.mensagens.map((mensagem: ChatMensagem) =>
        mensagem.id === mensagemId ? { ...mensagem, reacoes: resumo } : mensagem,
      ),
    };
  });
}
