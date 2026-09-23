import type { QueryClient } from "@tanstack/react-query";

import { paginaMensagens } from "./api";
import { atualizarPaginaRecente, type ChaveDoHistorico, type DadosDoHistorico } from "./cache-mensagens";
import { mesclarMensagens } from "./tempo-real";
import type { MensagemResposta, StatusTempoReal } from "./types";

function correspondeAoStatus(mensagem: MensagemResposta, status: StatusTempoReal): boolean {
  return mensagem.id === status.mensagemId
    || (status.idempotencyKey != null && mensagem.idempotencyKey === status.idempotencyKey);
}

function comStatus(mensagem: MensagemResposta, status: StatusTempoReal): MensagemResposta {
  return {
    ...mensagem,
    id: status.mensagemId,
    statusEntrega: status.statusEntrega,
    // Um status posterior do backend reconcilia qualquer marcador local transitório; motivo de erro
    // só pertence a FALHOU persistido.
    erroEntrega: status.statusEntrega === "FALHOU" ? mensagem.erroEntrega : null,
    idempotencyKey: mensagem.idempotencyKey ?? status.idempotencyKey ?? null,
  };
}

/**
 * Aplica um STATUS às páginas carregadas, sem nunca rebaixar (a fusão escolhe o mais avançado).
 * Devolve `false` quando nenhuma bolha corresponde — o evento chegou antes da mensagem.
 */
export function aplicarStatusNoHistorico(
  queryClient: QueryClient,
  queryKey: ChaveDoHistorico,
  status: StatusTempoReal,
): boolean {
  const atual = queryClient.getQueryData<DadosDoHistorico>(queryKey);
  const temAlvo = atual?.pages.some((pagina) =>
    pagina.mensagens.some((mensagem) => correspondeAoStatus(mensagem, status)));
  if (!atual || !temAlvo) return false;
  queryClient.setQueryData<DadosDoHistorico>(queryKey, {
    ...atual,
    pages: atual.pages.map((pagina) => {
      const alvos = pagina.mensagens.filter((mensagem) => correspondeAoStatus(mensagem, status));
      if (alvos.length === 0) return pagina;
      return {
        ...pagina,
        mensagens: mesclarMensagens(pagina.mensagens, alvos.map((alvo) => comStatus(alvo, status))),
      };
    }),
  });
  return true;
}

function temEnvioPendenteNaPaginaRecente(dados: DadosDoHistorico | undefined): boolean {
  return dados?.pages[0]?.mensagens.some((mensagem) => mensagem.statusEntrega === "PENDENTE") ?? false;
}

interface Rodada {
  repetir: boolean;
}

const rodadasEmCurso = new Map<string, Rodada>();

/**
 * Status de mensagem já conhecida não volta pelo `/desde` (o backend filtra `enviado_em > desde`, e
 * mudar de status não muda `enviado_em`). Quando há bolha PENDENTE, a página recente do histórico —
 * a mesma da primeira carga, já autorizada por atendimento — traz o status persistido. A fusão une
 * pela chave idempotente, então também resolve a bolha otimista cujo STATUS chegou antes do HTTP.
 *
 * Só roda com PENDENTE na página recente. Pedidos de STATUS que chegam durante uma rodada viram uma
 * única rodada seguinte, porque a em curso pode ter lido antes do commit que originou o pedido. A
 * reconexão (`substituirRodadaEmCurso`) nunca espera: a queda que a provocou pode ter deixado a
 * leitura anterior pendurada para sempre (fetch sem resposta nem erro), e esperar por ela travaria
 * a conversa em "Enviando" até o F5.
 */
export async function reconciliarEnviosPendentes(
  queryClient: QueryClient,
  queryKey: ChaveDoHistorico,
  atendimentoId: string,
  substituirRodadaEmCurso = false,
): Promise<void> {
  const emCurso = rodadasEmCurso.get(atendimentoId);
  if (emCurso && !substituirRodadaEmCurso) {
    emCurso.repetir = true;
    return;
  }
  if (!temEnvioPendenteNaPaginaRecente(queryClient.getQueryData(queryKey))) return;

  const rodada: Rodada = { repetir: false };
  rodadasEmCurso.set(atendimentoId, rodada);
  try {
    do {
      rodada.repetir = false;
      const pagina = await paginaMensagens(atendimentoId, null);
      atualizarPaginaRecente(queryClient, queryKey, (atuais) => mesclarMensagens(atuais, pagina.mensagens));
    } while (rodada.repetir && temEnvioPendenteNaPaginaRecente(queryClient.getQueryData(queryKey)));
  } catch {
    // Best effort: a próxima reconexão ou o próximo STATUS sem bolha dispara outra rodada. A tela não
    // mostra erro — a bolha continua honestamente PENDENTE, como o servidor a conhecia por último.
  } finally {
    // Uma rodada substituída não apaga a que a substituiu.
    if (rodadasEmCurso.get(atendimentoId) === rodada) rodadasEmCurso.delete(atendimentoId);
  }
}
