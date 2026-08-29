"use client";

import { useMutation, useQueryClient } from "@tanstack/react-query";

import { enviarMidia } from "./api";
import { atualizarPaginaRecente, identidadeAutenticada } from "./cache-mensagens";
import { mesclarMensagens } from "./tempo-real";
import type { MensagemResposta, TipoMensagem } from "./types";

interface VariaveisEnvioMidia {
  atendimentoId: string;
  leadId: string;
  arquivo: File;
  legenda?: string;
  onProgresso?: (percentual: number) => void;
  resposta?: { mensagemId: string; enviadoEm: string };
  citacao?: MensagemResposta["citacao"];
}

function idTemporario(): string {
  return `temp-${crypto.randomUUID()}`;
}

function tipoDoArquivo(mimetype: string): TipoMensagem {
  if (mimetype.startsWith("image/")) return "IMAGEM";
  if (mimetype.startsWith("audio/")) return "AUDIO";
  return "DOCUMENTO";
}

/**
 * Mesmo contrato de estado de {@link import("./use-enviar-mensagem").useEnviarMensagem}: bolha
 * `PENDENTE` de verdade assim que o `mutate` roda, `FALHOU` com reenviar no erro. A diferença é a
 * preview local — `URL.createObjectURL`, válida só nesta sessão do browser — porque o backend não
 * devolve a URL assinada na resposta de envio (só quem lê a conversa depois assina de novo).
 */
export function useEnviarMidia() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (variaveis: VariaveisEnvioMidia) =>
      enviarMidia(
        variaveis.atendimentoId,
        variaveis.arquivo,
        variaveis.legenda,
        variaveis.onProgresso ?? (() => {}),
        variaveis.resposta,
      ),
    onMutate: (variaveis) => {
      const queryKey = ["mensagens", variaveis.atendimentoId] as const;
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
        enviadoEm: new Date().toISOString(),
        citacao: variaveis.citacao ?? null,
      };
      atualizarPaginaRecente(queryClient, queryKey, (atual) => [...atual, otimista]);
      return { queryKey, idOtimista };
    },
    onError: (_erro, variaveis, contexto) => {
      if (!contexto) {
        return;
      }
      if (variaveis.resposta) {
        atualizarPaginaRecente(queryClient, contexto.queryKey, (atual) =>
          atual.filter((mensagem) => mensagem.id !== contexto.idOtimista),
        );
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
    onSuccess: (resposta, _variaveis, contexto) => {
      if (!contexto) {
        return;
      }
      const identidade = identidadeAutenticada(queryClient);
      atualizarPaginaRecente(queryClient, contexto.queryKey, (atual) => {
        const otimista = atual.find((mensagem) => mensagem.id === contexto.idOtimista);
        if (!otimista) return mesclarMensagens(atual, []);
        const real: MensagemResposta = {
          ...otimista,
          id: resposta.mensagemId,
          remetenteId: identidade.id ?? otimista.remetenteId,
          remetenteNome: identidade.nome ?? otimista.remetenteNome,
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
