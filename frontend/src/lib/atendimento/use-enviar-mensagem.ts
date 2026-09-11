"use client";

import { useMutation, useQueryClient } from "@tanstack/react-query";

import { ErroDeApi } from "@/lib/api/errors";

import { enviarMensagem, enviarTemplate } from "./api";
import { atualizarPaginaRecente, identidadeAutenticada } from "./cache-mensagens";
import { reconciliarEnvioAmbiguo } from "./reconciliar-envio";
import { mesclarMensagens } from "./tempo-real";
import type { MensagemResposta } from "./types";

interface VariaveisEnvio {
  atendimentoId: string;
  leadId: string;
  conteudo: string;
  template?: { nome: string; idioma: string; parametros: string[] };
  resposta?: { mensagemId: string; enviadoEm: string };
  citacao?: MensagemResposta["citacao"];
  idempotencyKey?: string;
}

function idTemporario(): string {
  return `temp-${crypto.randomUUID()}`;
}

/**
 * Estado real, não otimismo: a mensagem aparece assim que o `mutate` roda, já com o único status
 * possível naquele instante — `PENDENTE` — nunca fingindo `ENVIADO`. Falha de transporte mantém o
 * item PENDENTE enquanto a chave é reconciliada no histórico; só uma
 * recusa definitiva ou a exaustão documentada da reconciliação permite a transição para FALHOU.
 */
export function useEnviarMensagem(onMensagemEnviada?: () => void) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (variaveis: VariaveisEnvio) =>
      variaveis.template
        ? enviarTemplate(
            variaveis.leadId,
            variaveis.template.nome,
            variaveis.template.idioma,
            variaveis.template.parametros,
            variaveis.idempotencyKey,
          )
        : enviarMensagem(
            variaveis.leadId,
            variaveis.conteudo,
            variaveis.resposta,
            variaveis.idempotencyKey,
          ),
    onMutate: (variaveis) => {
      const queryKey = ["mensagens", variaveis.atendimentoId] as const;
      variaveis.idempotencyKey ??= crypto.randomUUID();
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
        erroEntrega: null,
        enviadoEm: new Date().toISOString(),
        citacao: variaveis.citacao ?? null,
        idempotencyKey: variaveis.idempotencyKey,
      };
      atualizarPaginaRecente(queryClient, queryKey, (atual) => [...atual, otimista]);
      return { queryKey, idOtimista, criadoEm: otimista.enviadoEm };
    },
    onError: async (erro, variaveis, contexto) => {
      if (!contexto) {
        return;
      }
      const definitiva =
        erro instanceof ErroDeApi
        && erro.status >= 400
        && erro.status < 500
        && ![408, 425, 429].includes(erro.status);
      if (!definitiva) {
        const reconciliada = await reconciliarEnvioAmbiguo(
          queryClient,
          variaveis.atendimentoId,
          contexto.queryKey,
          contexto.idOtimista,
          variaveis.idempotencyKey!,
          contexto.criadoEm,
        );
        if (reconciliada) {
          queryClient.invalidateQueries({ queryKey: ["atendimentos"] });
          onMensagemEnviada?.();
          return;
        }
      }
      if (variaveis.resposta) {
        // Respostas/citações mantêm o comportamento existente: sem confirmação da API, a
        // referência otimista é retirada para não deixar um vínculo local que nunca foi aceito.
        atualizarPaginaRecente(queryClient, contexto.queryKey, (atual) =>
          atual.filter((mensagem) => mensagem.id !== contexto.idOtimista),
        );
        return;
      }
      const erroDefinitivo = erro instanceof ErroDeApi ? {
        codigo: erro.status,
        titulo: erro.message,
      } : null;
      atualizarPaginaRecente(queryClient, contexto.queryKey, (atual) =>
        atual.map((mensagem) =>
          mensagem.id === contexto.idOtimista
            ? ({
                ...mensagem,
                statusEntrega: "FALHOU",
                erroEntrega: erroDefinitivo ?? { codigo: -1, titulo: null },
              } as MensagemResposta)
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
        const otimista = atual.find(
          (mensagem) =>
            mensagem.id === contexto.idOtimista
            || mensagem.idempotencyKey === resposta.idempotencyKey,
        );
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
          erroEntrega: null,
          enviadoEm: resposta.enviadoEm,
          citacao: otimista?.citacao ?? variaveis.citacao ?? null,
          idempotencyKey: resposta.idempotencyKey ?? variaveis.idempotencyKey,
        };
        return mesclarMensagens(
          atual.filter(
            (mensagem) =>
              mensagem.id !== contexto.idOtimista
              && mensagem.idempotencyKey !== real.idempotencyKey,
          ),
          [real],
        );
      });
      if (resposta.transferiuOLead) {
        queryClient.invalidateQueries({ queryKey: ["atendimentos"] });
      }
      onMensagemEnviada?.();
    },
  });
}
