"use client";

import { useEffect, useMemo, useRef } from "react";

import { useInfiniteQuery, useQueryClient } from "@tanstack/react-query";

import { mensagensDesde, paginaMensagens } from "./api";
import { atualizarPaginaRecente, type DadosDoHistorico } from "./cache-mensagens";
import { atualizarReacoesDoHistorico } from "./reacoes-cache";
import { type ConexaoTempoReal, type EstadoConexao, mesclarMensagens } from "./tempo-real";
import type { EventoTempoReal, MensagemResposta } from "./types";
import { useAuthStore } from "@/lib/auth/auth-store";

/** Historico por cursor, somado ao fluxo incremental do WebSocket e a reconciliacao de reconexao. */
export function useMensagens(
  atendimentoId: string | null,
  conexao: ConexaoTempoReal,
  estadoConexao: EstadoConexao,
  onMensagemRecebida?: () => void,
  atendimentoParaAssinar: string | null = atendimentoId,
  onEventoEstado?: (evento: EventoTempoReal) => void,
) {
  const queryClient = useQueryClient();
  const queryKey = ["mensagens", atendimentoId] as const;
  const ultimoInstanteRef = useRef<string | null>(null);
  const onMensagemRecebidaRef = useRef(onMensagemRecebida);
  const onEventoEstadoRef = useRef(onEventoEstado);

  useEffect(() => {
    onMensagemRecebidaRef.current = onMensagemRecebida;
  }, [onMensagemRecebida]);

  useEffect(() => {
    onEventoEstadoRef.current = onEventoEstado;
  }, [onEventoEstado]);

  const query = useInfiniteQuery({
    queryKey,
    queryFn: ({ pageParam }) => paginaMensagens(atendimentoId as string, pageParam),
    initialPageParam: null as string | null,
    getNextPageParam: (ultima) => ultima.proximoCursor ?? undefined,
    enabled: atendimentoId != null,
  });

  const mensagens = useMemo(
    () =>
      query.data
        ? [...query.data.pages].reverse().flatMap((pagina) => pagina.mensagens)
        : [],
    [query.data],
  );

  useEffect(() => {
    ultimoInstanteRef.current = null;
    if (!atendimentoParaAssinar) {
      conexao.fecharConversa();
      return;
    }
    conexao.abrirConversa(atendimentoParaAssinar, (evento) => {
      if (evento.tipo === "MENSAGEM") {
        const nova: MensagemResposta = {
          id: evento.dados.mensagemId,
          atendimentoId: evento.dados.atendimentoId,
          remetenteTipo: evento.dados.remetenteTipo,
          remetenteId: evento.dados.remetenteId,
          remetenteNome: null,
          tipo: evento.dados.tipo,
          conteudo: evento.dados.conteudo,
          midiaUrl: evento.dados.midiaUrl,
          midiaMetadados: evento.dados.midiaMetadados,
          opcoes: evento.dados.opcoes,
          statusEntrega: evento.dados.statusEntrega,
          erroEntrega: null,
          enviadoEm: evento.dados.enviadoEm,
          citacao: evento.dados.citacao ?? null,
        };
        atualizarPaginaRecente(queryClient, queryKey, (atuais) => mesclarMensagens(atuais, [nova]));
        ultimoInstanteRef.current = evento.dados.enviadoEm;
        onMensagemRecebidaRef.current?.();
      } else if (evento.tipo === "STATUS") {
        if (evento.dados.statusEntrega === "FALHOU") {
          void queryClient.invalidateQueries({ queryKey });
          return;
        }
        queryClient.setQueryData<DadosDoHistorico>(queryKey, (atual) =>
          atual
            ? {
                ...atual,
                pages: atual.pages.map((pagina) => ({
                  ...pagina,
                  mensagens: pagina.mensagens.map((mensagem) =>
                    mensagem.id === evento.dados.mensagemId
                      ? { ...mensagem, statusEntrega: evento.dados.statusEntrega }
                      : mensagem,
                  ),
                })),
              }
            : atual,
        );
      } else if (evento.tipo === "REACAO") {
        atualizarReacoesDoHistorico(
          queryClient,
          queryKey,
          evento.dados.mensagemId,
          evento.dados.reacoes,
          { atorId: evento.dados.atorId, emojiDoAtor: evento.dados.emojiDoAtor },
          useAuthStore.getState().usuarioId,
        );
      } else {
        // TRANSFERENCIA / FINALIZACAO: o chamador aplica o estado local com guarda de
        // ocorridoEm; aqui só dispara o callback e reconcilia a inbox via HTTP.
        onEventoEstadoRef.current?.(evento);
        void queryClient.invalidateQueries({ queryKey: ["atendimentos"] });
      }
    });
    return () => conexao.fecharConversa();
    // eslint-disable-next-line react-hooks/exhaustive-deps -- a assinatura muda somente com a conversa
  }, [atendimentoParaAssinar]);

  useEffect(() => {
    if (mensagens.length === 0) return;
    const maisRecente = mensagens.reduce((a, b) => (a.enviadoEm > b.enviadoEm ? a : b));
    if (!ultimoInstanteRef.current || maisRecente.enviadoEm > ultimoInstanteRef.current) {
      ultimoInstanteRef.current = maisRecente.enviadoEm;
    }
  }, [mensagens]);

  useEffect(() => {
    if (estadoConexao !== "conectado" || !atendimentoParaAssinar || !ultimoInstanteRef.current) return;
    mensagensDesde(atendimentoParaAssinar, ultimoInstanteRef.current).then((novas) => {
      if (novas.length > 0) {
        atualizarPaginaRecente(queryClient, queryKey, (atuais) => mesclarMensagens(atuais, novas));
      }
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps -- reage somente a transicao da conexao
  }, [estadoConexao, atendimentoParaAssinar]);

  return { ...query, data: mensagens };
}
