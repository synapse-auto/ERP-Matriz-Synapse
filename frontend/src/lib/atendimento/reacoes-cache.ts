import type { QueryClient } from "@tanstack/react-query";

import type { ResumoReacao } from "@/lib/atendimento/types";
import type { ChatMensagem, PaginaChatMensagens } from "@/lib/chat-interno/types";

import type { ChaveDoHistorico, DadosDoHistorico } from "./cache-mensagens";

export interface ResumoReacaoPublico {
  emoji: string;
  quantidade: number;
}

/** Minimo no evento WS: quem alterou e o emoji atual (nulo = removeu). Sem nomes nem lista. */
export interface AlteracaoDeReacao {
  atorId: string;
  emojiDoAtor: string | null;
}

/**
 * Substitui quantidades do evento; nunca incrementa.
 * Se o ator e o usuario desta aba, `reagi` segue `emojiDoAtor`; senao preserva a marca local.
 */
export function aplicarResumoPublico(
  atuais: ResumoReacao[] | undefined,
  publicos: ResumoReacaoPublico[],
  alteracao: AlteracaoDeReacao,
  usuarioAtualId: string | null,
): ResumoReacao[] {
  const meuEmoji =
    alteracao.atorId === usuarioAtualId
      ? alteracao.emojiDoAtor
      : (atuais?.find((item) => item.reagi)?.emoji ?? null);
  return publicos.map((item) => ({
    emoji: item.emoji,
    quantidade: item.quantidade,
    reagi: meuEmoji != null && item.emoji === meuEmoji,
  }));
}

export function atualizarReacoesDoHistorico(
  queryClient: QueryClient,
  queryKey: ChaveDoHistorico,
  mensagemId: string,
  publicos: ResumoReacaoPublico[],
  alteracao: AlteracaoDeReacao,
  usuarioAtualId: string | null,
): void {
  queryClient.setQueryData<DadosDoHistorico>(queryKey, (atual) => {
    if (!atual) return atual;
    return {
      ...atual,
      pages: atual.pages.map((pagina) => ({
        ...pagina,
        mensagens: pagina.mensagens.map((mensagem) =>
          mensagem.id === mensagemId
            ? { ...mensagem, reacoes: aplicarResumoPublico(mensagem.reacoes, publicos, alteracao, usuarioAtualId) }
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
  alteracao: AlteracaoDeReacao,
  usuarioAtualId: string | null,
): void {
  queryClient.setQueryData<PaginaChatMensagens>(["chat-interno", "mensagens", conversaId], (atual) => {
    if (!atual) return atual;
    return {
      ...atual,
      mensagens: atual.mensagens.map((mensagem: ChatMensagem) =>
        mensagem.id === mensagemId
          ? { ...mensagem, reacoes: aplicarResumoPublico(mensagem.reacoes, publicos, alteracao, usuarioAtualId) }
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
