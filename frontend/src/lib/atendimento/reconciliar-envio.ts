import type { QueryClient } from "@tanstack/react-query";

import { mensagensDesde, paginaMensagens } from "./api";
import { atualizarPaginaRecente } from "./cache-mensagens";
import { mesclarMensagens } from "./tempo-real";
import type { MensagemResposta } from "./types";

/**
 * Falha de transporte não prova que o servidor recusou o envio. Buscamos a página mais recente e
 * o delta desde a criação do otimista; a chave, nunca o texto/horário, é a identidade da tentativa.
 * Três rodadas dão tempo para a transação/outbox aparecerem sem manter a tela bloqueada.
 */
export async function reconciliarEnvioAmbiguo(
  queryClient: QueryClient,
  atendimentoId: string,
  queryKey: readonly ["mensagens", string],
  idOtimista: string,
  chaveIdempotencia: string,
  criadoEm: string,
): Promise<boolean> {
  const tentativas = 3;
  for (let tentativa = 0; tentativa < tentativas; tentativa += 1) {
    try {
      const [pagina, delta] = await Promise.all([
        paginaMensagens(atendimentoId, null),
        mensagensDesde(atendimentoId, criadoEm),
      ]);
      const real = [...pagina.mensagens, ...delta].find(
        (mensagem) => mensagem.idempotencyKey === chaveIdempotencia,
      );
      if (real) {
        substituirOtimista(queryClient, queryKey, idOtimista, real);
        return true;
      }
    } catch {
      // A reconciliação é best effort nesta rodada; o critério só termina após todas as tentativas.
    }
    if (tentativa < tentativas - 1) {
      await new Promise<void>((resolve) => {
        setTimeout(resolve, 1000 * 2 ** tentativa);
      });
    }
  }
  return false;
}

export function substituirOtimista(
  queryClient: QueryClient,
  queryKey: readonly ["mensagens", string],
  idOtimista: string,
  real: MensagemResposta,
): void {
  atualizarPaginaRecente(queryClient, queryKey, (atuais) =>
    mesclarMensagens(atuais.filter((mensagem) => mensagem.id !== idOtimista), [real]),
  );
}
