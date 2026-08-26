"use client";

import { useMutation, useQueryClient } from "@tanstack/react-query";

import { enviarMensagem } from "./api";
import { atualizarPaginaRecente, identidadeAutenticada } from "./cache-mensagens";
import { mesclarMensagens } from "./tempo-real";
import type { MensagemResposta } from "./types";

interface VariaveisEnvio {
  atendimentoId: string;
  leadId: string;
  conteudo: string;
}

function idTemporario(): string {
  return `temp-${crypto.randomUUID()}`;
}

/**
 * Estado real, não otimismo: a mensagem aparece assim que o `mutate` roda, já com o único status
 * possível naquele instante — `PENDENTE` — nunca fingindo `ENVIADO`. Falha de rede transita o mesmo
 * item para `FALHOU`, com o botão de reenviar; sucesso troca o id temporário pelo id real que o
 * backend devolveu, para os eventos de status via WebSocket encontrarem a linha certa depois.
 */
export function useEnviarMensagem() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (variaveis: VariaveisEnvio) => enviarMensagem(variaveis.leadId, variaveis.conteudo),
    onMutate: (variaveis) => {
      const queryKey = ["mensagens", variaveis.atendimentoId] as const;
      const idOtimista = idTemporario();
      const identidade = identidadeAutenticada(queryClient);
      const otimista: MensagemResposta = {
        id: idOtimista,
        remetenteTipo: "ATENDENTE",
        remetenteId: identidade.id,
        remetenteNome: identidade.nome,
        tipo: "TEXTO",
        conteudo: variaveis.conteudo,
        midiaUrl: null,
        midiaMetadados: null,
        opcoes: null,
        statusEntrega: "PENDENTE",
        enviadoEm: new Date().toISOString(),
      };
      atualizarPaginaRecente(queryClient, queryKey, (atual) => [...atual, otimista]);
      return { queryKey, idOtimista };
    },
    onError: (_erro, _variaveis, contexto) => {
      if (!contexto) {
        return;
      }
      atualizarPaginaRecente(queryClient, contexto.queryKey, (atual) =>
        atual.map((mensagem) =>
          mensagem.id === contexto.idOtimista
            ? ({ ...mensagem, statusEntrega: "FALHOU" } as MensagemResposta)
            : mensagem,
        ),
      );
    },
    onSuccess: (resposta, variaveis, contexto) => {
      if (!contexto) {
        return;
      }
      const identidade = identidadeAutenticada(queryClient);
      atualizarPaginaRecente(queryClient, contexto.queryKey, (atual) => {
        const otimista = atual.find((mensagem) => mensagem.id === contexto.idOtimista);
        const real: MensagemResposta = {
          id: resposta.mensagemId,
          remetenteTipo: otimista?.remetenteTipo ?? "ATENDENTE",
          remetenteId: identidade.id ?? otimista?.remetenteId ?? null,
          remetenteNome: identidade.nome ?? otimista?.remetenteNome ?? null,
          tipo: otimista?.tipo ?? "TEXTO",
          conteudo: otimista?.conteudo ?? variaveis.conteudo,
          midiaUrl: otimista?.midiaUrl ?? null,
          midiaMetadados: otimista?.midiaMetadados ?? null,
          opcoes: otimista?.opcoes ?? null,
          statusEntrega: resposta.statusEntrega,
          enviadoEm: resposta.enviadoEm,
        };
        return mesclarMensagens(
          atual.filter((mensagem) => mensagem.id !== contexto.idOtimista),
          [real],
        );
      });
      if (resposta.transferiuOLead) {
        queryClient.invalidateQueries({ queryKey: ["atendimentos"] });
      }
    },
  });
}
