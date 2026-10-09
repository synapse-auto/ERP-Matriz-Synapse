"use client";

import { useEffect } from "react";

import { useQuery, useQueryClient } from "@tanstack/react-query";

import { useFuncionalidadesHabilitadas } from "@/lib/config/use-funcionalidades";

import { listarInformacoesDoChatbot } from "./api";
import type { EstadoConexao } from "./tempo-real";
import type { CartaoInformacoesChatbot } from "./types";

/** Chave da feature flag por instância: o recurso é capacidade, nunca nome de cliente. */
export const FUNCIONALIDADE_INFORMACOES_CHATBOT = "informacoes_chatbot_historico";

const SEM_CARTOES: ReadonlyArray<CartaoInformacoesChatbot> = [];

export function chaveInformacoesChatbot(atendimentoId: string | null) {
  return ["informacoes-chatbot", atendimentoId] as const;
}

/**
 * Cards do chatbot do atendimento aberto. Uma única consulta limitada por conversa, fora da
 * paginação de mensagens: o card não é mensagem e não pode mexer no cursor do histórico.
 *
 * Sem polling. Cada card novo chega pelo evento `INFORMACOES_CHATBOT` (que invalida esta chave) e a
 * consulta é revalidada uma vez a cada conexão do WebSocket (a primeira e as reconexões) para
 * recuperar o que passou antes de o canal estar assinado. Com a flag desligada nenhuma requisição
 * é feita.
 */
export function useInformacoesDoChatbot(
  atendimentoId: string | null,
  estadoConexao: EstadoConexao,
): ReadonlyArray<CartaoInformacoesChatbot> {
  const queryClient = useQueryClient();
  const { data: funcionalidades } = useFuncionalidadesHabilitadas();
  const ativa =
    atendimentoId != null && (funcionalidades?.includes(FUNCIONALIDADE_INFORMACOES_CHATBOT) ?? false);

  const consulta = useQuery({
    queryKey: chaveInformacoesChatbot(atendimentoId),
    queryFn: async () => (await listarInformacoesDoChatbot(atendimentoId as string)).itens,
    enabled: ativa,
  });

  useEffect(() => {
    if (estadoConexao !== "conectado" || !ativa) return;
    // Backfill de toda conexão, a primeira inclusive: um card criado entre a carga da conversa e a
    // assinatura do canal não gera evento para esta aba, e nada mais o buscaria.
    void queryClient.invalidateQueries({ queryKey: chaveInformacoesChatbot(atendimentoId) });
  }, [estadoConexao, ativa, atendimentoId, queryClient]);

  return ativa ? (consulta.data ?? SEM_CARTOES) : SEM_CARTOES;
}
