"use client";

import { useRef } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";

import { ErroDeApi } from "@/lib/api/errors";

import { enviarMensagem, enviarTemplate } from "./api";
import { atualizarPaginaRecente, identidadeAutenticada } from "./cache-mensagens";
import { reconciliarEnvioAmbiguo } from "./reconciliar-envio";
import { mesclarMensagens } from "./tempo-real";
import type { EnvioResposta, MensagemResposta } from "./types";

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

type ContextoOtimista = {
  queryKey: readonly ["mensagens", string];
  idOtimista: string;
  criadoEm: string;
};

function erroDefinitivo(erro: unknown): boolean {
  return erro instanceof ErroDeApi
    && erro.status >= 400
    && erro.status < 500
    && ![408, 425, 429].includes(erro.status);
}

function respostaDaReconciliacao(
  real: MensagemResposta,
  variaveis: VariaveisEnvio,
): EnvioResposta {
  return {
    atendimentoId: real.atendimentoId ?? variaveis.atendimentoId,
    mensagemId: real.id,
    statusEntrega: real.statusEntrega,
    enviadoEm: real.enviadoEm,
    // A propriedade pode ter mudado na confirmação que se perdeu. O cache da inbox é buscado de
    // novo abaixo, portanto este valor só força a reconciliação conservadora da lista.
    transferiuOLead: true,
    idempotencyKey: real.idempotencyKey ?? variaveis.idempotencyKey ?? null,
  };
}

/**
 * Estado real, não otimismo: a mensagem aparece assim que o `mutate` roda, já com o único status
 * possível naquele instante — `PENDENTE` — nunca fingindo `ENVIADO`. Falha de transporte mantém o
 * item PENDENTE enquanto a chave é reconciliada no histórico; só uma
 * recusa definitiva ou a exaustão documentada da reconciliação permite a transição para FALHOU.
 */
export function useEnviarMensagem(onMensagemEnviada?: () => void) {
  const queryClient = useQueryClient();
  const contextos = useRef(new Map<string, ContextoOtimista>());
  const chavesReconciliadas = useRef(new Set<string>());

  return useMutation({
    mutationFn: async (variaveis: VariaveisEnvio) => {
      try {
        return variaveis.template
          ? await enviarTemplate(
              variaveis.atendimentoId,
              variaveis.leadId,
              variaveis.template.nome,
              variaveis.template.idioma,
              variaveis.template.parametros,
              variaveis.idempotencyKey,
            )
          : await enviarMensagem(
              variaveis.atendimentoId,
              variaveis.leadId,
              variaveis.conteudo,
              variaveis.resposta,
              variaveis.idempotencyKey,
            );
      } catch (erro) {
        const chave = variaveis.idempotencyKey;
        const contexto = chave ? contextos.current.get(chave) : undefined;
        if (erroDefinitivo(erro) || !chave || !contexto) throw erro;

        const real = await reconciliarEnvioAmbiguo(
          queryClient,
          variaveis.atendimentoId,
          contexto.queryKey,
          contexto.idOtimista,
          chave,
          contexto.criadoEm,
        );
        if (!real) throw erro;
        chavesReconciliadas.current.add(chave);
        return respostaDaReconciliacao(real, variaveis);
      }
    },
    onMutate: (variaveis) => {
      const queryKey = ["mensagens", variaveis.atendimentoId] as const;
      const chaveIdempotencia = variaveis.idempotencyKey ?? crypto.randomUUID();
      variaveis.idempotencyKey = chaveIdempotencia;
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
        idempotencyKey: chaveIdempotencia,
      };
      atualizarPaginaRecente(queryClient, queryKey, (atual) => [...atual, otimista]);
      const contexto = { queryKey, idOtimista, criadoEm: otimista.enviadoEm };
      contextos.current.set(chaveIdempotencia, contexto);
      return contexto;
    },
    onError: (erro, variaveis, contexto) => {
      if (!contexto) {
        return;
      }
      if (variaveis.idempotencyKey) contextos.current.delete(variaveis.idempotencyKey);
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
      const reconciliada = resposta.idempotencyKey != null
        && chavesReconciliadas.current.delete(resposta.idempotencyKey);
      if (variaveis.idempotencyKey) contextos.current.delete(variaveis.idempotencyKey);
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
      if (resposta.transferiuOLead || reconciliada) {
        queryClient.invalidateQueries({ queryKey: ["atendimentos"] });
      }
      onMensagemEnviada?.();
    },
  });
}
