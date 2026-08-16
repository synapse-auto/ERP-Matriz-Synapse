"use client";

import { useEffect, useMemo, useRef } from "react";

import { useInfiniteQuery, useQueryClient } from "@tanstack/react-query";

import { mensagensDesde, paginaMensagens } from "./api";
import { atualizarPaginaRecente, type DadosDoHistorico } from "./cache-mensagens";
import { type ConexaoTempoReal, type EstadoConexao, mesclarMensagens } from "./tempo-real";
import type { MensagemResposta } from "./types";

/** Historico por cursor, somado ao fluxo incremental do WebSocket e a reconciliacao de reconexao. */
export function useMensagens(
  atendimentoId: string | null,
  conexao: ConexaoTempoReal,
  estadoConexao: EstadoConexao,
) {
  const queryClient = useQueryClient();
  const queryKey = ["mensagens", atendimentoId] as const;
  const ultimoInstanteRef = useRef<string | null>(null);

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
          remetenteNome: null,
          tipo: evento.dados.tipo,
          conteudo: evento.dados.conteudo,
          midiaUrl: evento.dados.midiaUrl,
          midiaMetadados: evento.dados.midiaMetadados,
          statusEntrega: evento.dados.statusEntrega,
          enviadoEm: evento.dados.enviadoEm,
        };
        atualizarPaginaRecente(queryClient, queryKey, (atuais) => mesclarMensagens(atuais, [nova]));
        ultimoInstanteRef.current = evento.dados.enviadoEm;
      } else if (evento.tipo === "STATUS") {
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
      } else {
        queryClient.invalidateQueries({ queryKey: ["atendimentos"] });
      }
    });
    return () => conexao.fecharConversa();
    // eslint-disable-next-line react-hooks/exhaustive-deps -- a assinatura muda somente com a conversa
  }, [atendimentoId]);

  useEffect(() => {
    if (mensagens.length === 0) return;
    const maisRecente = mensagens.reduce((a, b) => (a.enviadoEm > b.enviadoEm ? a : b));
    if (!ultimoInstanteRef.current || maisRecente.enviadoEm > ultimoInstanteRef.current) {
      ultimoInstanteRef.current = maisRecente.enviadoEm;
    }
  }, [mensagens]);

  useEffect(() => {
    if (estadoConexao !== "conectado" || !atendimentoId || !ultimoInstanteRef.current) return;
    mensagensDesde(atendimentoId, ultimoInstanteRef.current).then((novas) => {
      if (novas.length > 0) {
        atualizarPaginaRecente(queryClient, queryKey, (atuais) => mesclarMensagens(atuais, novas));
      }
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps -- reage somente a transicao da conexao
  }, [estadoConexao]);

  return { ...query, data: mensagens };
}
