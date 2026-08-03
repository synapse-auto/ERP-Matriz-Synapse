"use client";

import { useEffect, useRef } from "react";

import { useQuery, useQueryClient } from "@tanstack/react-query";

import { mensagensDesde } from "./api";
import { type ConexaoTempoReal, type EstadoConexao, mesclarMensagens } from "./tempo-real";
import type { MensagemResposta } from "./types";

/**
 * Histórico de uma conversa: carga inicial via `useQuery`, atualizado depois de forma incremental
 * (nunca refetch da lista inteira) por dois caminhos — eventos WS em tempo real e o backfill que
 * roda toda vez que a conexão volta de uma queda (reconciliação, RNF-CRM-01).
 */
export function useMensagens(
  atendimentoId: string | null,
  conexao: ConexaoTempoReal,
  estadoConexao: EstadoConexao,
) {
  const queryClient = useQueryClient();
  const queryKey = ["mensagens", atendimentoId] as const;
  const ultimoInstanteRef = useRef<string | null>(null);

  const query = useQuery({
    queryKey,
    queryFn: () => mensagensDesde(atendimentoId as string),
    enabled: atendimentoId != null,
  });

  // Assina a conversa aberta; desassina a anterior automaticamente (ConexaoTempoReal.abrirConversa).
  useEffect(() => {
    ultimoInstanteRef.current = null;
    if (!atendimentoId) {
      conexao.fecharConversa();
      return;
    }
    conexao.abrirConversa(atendimentoId, (evento) => {
      if (evento.tipo === "MENSAGEM") {
        const nova: MensagemResposta = {
          id: evento.dados.mensagemId,
          remetenteTipo: evento.dados.remetenteTipo,
          remetenteId: evento.dados.remetenteId,
          tipo: evento.dados.tipo,
          conteudo: evento.dados.conteudo,
          midiaUrl: evento.dados.midiaUrl,
          midiaMetadados: evento.dados.midiaMetadados,
          statusEntrega: evento.dados.statusEntrega,
          enviadoEm: evento.dados.enviadoEm,
        };
        queryClient.setQueryData<MensagemResposta[]>(queryKey, (atual) =>
          mesclarMensagens(atual ?? [], [nova]),
        );
        ultimoInstanteRef.current = evento.dados.enviadoEm;
      } else if (evento.tipo === "STATUS") {
        queryClient.setQueryData<MensagemResposta[]>(queryKey, (atual) =>
          (atual ?? []).map((mensagem) =>
            mensagem.id === evento.dados.mensagemId
              ? { ...mensagem, statusEntrega: evento.dados.statusEntrega }
              : mensagem,
          ),
        );
      } else {
        // TRANSFERENCIA/FINALIZACAO: a conversa em si não muda de conteúdo, mas o card na lista
        // (dono, status) sim.
        queryClient.invalidateQueries({ queryKey: ["atendimentos"] });
      }
    });
    return () => conexao.fecharConversa();
    // eslint-disable-next-line react-hooks/exhaustive-deps -- reage só a troca de conversa
  }, [atendimentoId]);

  // Acompanha o instante mais recente já visto, para o backfill de reconexão.
  useEffect(() => {
    const mensagens = query.data;
    if (!mensagens || mensagens.length === 0) {
      return;
    }
    const maisRecente = mensagens.reduce((a, b) => (a.enviadoEm > b.enviadoEm ? a : b));
    if (!ultimoInstanteRef.current || maisRecente.enviadoEm > ultimoInstanteRef.current) {
      ultimoInstanteRef.current = maisRecente.enviadoEm;
    }
  }, [query.data]);

  // Backfill de reconexão: só dispara quando a conexão volta a "conectado" DEPOIS de já termos
  // uma carga inicial (ultimoInstanteRef setado) — a primeira carga já é coberta pelo useQuery
  // acima, então não duplicamos a busca aqui. Deliberadamente fora do array de deps: atendimentoId
  // e queryKey precisam ser lidos com o valor corrente no momento em que a conexão reconecta, não
  // disparar o efeito de novo a cada troca de conversa (isso já é tratado pelo efeito acima).
  useEffect(() => {
    if (estadoConexao !== "conectado" || !atendimentoId || !ultimoInstanteRef.current) {
      return;
    }
    mensagensDesde(atendimentoId, ultimoInstanteRef.current).then((novas) => {
      if (novas.length === 0) {
        return;
      }
      queryClient.setQueryData<MensagemResposta[]>(queryKey, (atual) =>
        mesclarMensagens(atual ?? [], novas),
      );
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps -- só reage a transição de estado da conexão
  }, [estadoConexao]);

  return query;
}
