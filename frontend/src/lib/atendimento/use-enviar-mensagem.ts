"use client";

import { useMutation, useQueryClient } from "@tanstack/react-query";

import { enviarMensagem } from "./api";
import { atualizarPaginaRecente } from "./cache-mensagens";
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
      const otimista: MensagemResposta = {
        id: idOtimista,
        remetenteTipo: "ATENDENTE",
        remetenteId: null,
        tipo: "TEXTO",
        conteudo: variaveis.conteudo,
        midiaUrl: null,
        midiaMetadados: null,
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
      const real: MensagemResposta = {
        id: resposta.mensagemId,
        remetenteTipo: "ATENDENTE",
        remetenteId: null,
        tipo: "TEXTO",
        conteudo: variaveis.conteudo,
        midiaUrl: null,
        midiaMetadados: null,
        statusEntrega: resposta.statusEntrega,
        enviadoEm: resposta.enviadoEm,
      };
      atualizarPaginaRecente(queryClient, contexto.queryKey, (atual) =>
        atual.map((mensagem) => (mensagem.id === contexto.idOtimista ? real : mensagem)),
      );
      if (resposta.transferiuOLead) {
        queryClient.invalidateQueries({ queryKey: ["atendimentos"] });
      }
    },
  });
}
