"use client";

import { useRef } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";

import { ErroDeApi } from "@/lib/api/errors";

import { enviarMidia } from "./api";
import { atualizarPaginaRecente, identidadeAutenticada } from "./cache-mensagens";
import { reconciliarEnvioAmbiguo } from "./reconciliar-envio";
import { mesclarMensagens } from "./tempo-real";
import type { EnvioResposta, MensagemResposta, TipoMensagem } from "./types";

interface VariaveisEnvioMidia {
  atendimentoId: string;
  leadId: string;
  arquivo: File;
  legenda?: string;
  onProgresso?: (percentual: number) => void;
  resposta?: { mensagemId: string; enviadoEm: string };
  citacao?: MensagemResposta["citacao"];
  gravacaoDoComposer?: boolean;
  idempotencyKey?: string;
}

function idTemporario(): string {
  return `temp-${crypto.randomUUID()}`;
}

function tipoDoArquivo(mimetype: string): TipoMensagem {
  if (mimetype.startsWith("image/")) return "IMAGEM";
  if (mimetype.startsWith("audio/")) return "AUDIO";
  return "DOCUMENTO";
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
  variaveis: VariaveisEnvioMidia,
): EnvioResposta {
  return {
    atendimentoId: real.atendimentoId ?? variaveis.atendimentoId,
    mensagemId: real.id,
    statusEntrega: real.statusEntrega,
    enviadoEm: real.enviadoEm,
    transferiuOLead: true,
    idempotencyKey: real.idempotencyKey ?? variaveis.idempotencyKey ?? null,
  };
}

/**
 * Mesmo contrato de estado de {@link import("./use-enviar-mensagem").useEnviarMensagem}: bolha
 * `PENDENTE` de verdade assim que o `mutate` roda; falhas de transporte ficam pendentes durante a
 * reconciliação pela chave. A diferença é a preview local — `URL.createObjectURL`, válida só nesta
 * sessão do browser — porque o backend não devolve a URL assinada na resposta de envio.
 */
export function useEnviarMidia() {
  const queryClient = useQueryClient();
  const contextos = useRef(new Map<string, ContextoOtimista>());
  const chavesReconciliadas = useRef(new Set<string>());

  return useMutation({
    mutationFn: async (variaveis: VariaveisEnvioMidia) => {
      try {
        return await enviarMidia(
          variaveis.atendimentoId,
          variaveis.arquivo,
          variaveis.legenda,
          variaveis.onProgresso ?? (() => {}),
          variaveis.resposta,
          variaveis.gravacaoDoComposer,
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
      const previewUrl = URL.createObjectURL(variaveis.arquivo);
      const otimista: MensagemResposta = {
        id: idOtimista,
        remetenteTipo: "ATENDENTE",
        remetenteId: identidade.id,
        remetenteNome: identidade.nome,
        tipo: tipoDoArquivo(variaveis.arquivo.type),
        conteudo: null,
        midiaUrl: previewUrl,
        midiaMetadados: JSON.stringify({
          nome: variaveis.arquivo.name,
          mimetype: variaveis.arquivo.type,
          tamanho: variaveis.arquivo.size,
          legenda: variaveis.legenda,
        }),
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
        // O WebSocket pode ter entregue a versão definitiva (inclusive a URL assinada) antes
        // da resposta HTTP. Nesse caso, não reconstrua a mensagem com a prévia blob: obsoleta.
        const definitiva = atual.find(
          (mensagem) =>
            mensagem.id === resposta.mensagemId
            || mensagem.idempotencyKey === resposta.idempotencyKey,
        );
        if (definitiva) {
          return atual
            .filter((mensagem) => mensagem.id !== contexto.idOtimista)
            .map((mensagem) =>
              mensagem.id === resposta.mensagemId
                ? {
                    ...mensagem,
                    // O evento não carrega o nome do remetente; apenas enriquecemos os campos
                    // ausentes com a identidade da sessão, sem tocar na URL/dados da mídia real.
                    remetenteId: mensagem.remetenteId ?? identidade.id,
                    remetenteNome: mensagem.remetenteNome ?? identidade.nome,
                  }
                : mensagem,
            );
        }
        const otimista = atual.find((mensagem) => mensagem.id === contexto.idOtimista);
        if (!otimista) return mesclarMensagens(atual, []);
        const real: MensagemResposta = {
          ...otimista,
          id: resposta.mensagemId,
          remetenteId: identidade.id ?? otimista.remetenteId,
          remetenteNome: identidade.nome ?? otimista.remetenteNome,
          statusEntrega: resposta.statusEntrega,
          enviadoEm: resposta.enviadoEm,
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
    },
  });
}
