import type { RemetenteTipo, StatusAtendimento } from "./types";

export const LIMITE_DE_ATRASO_MINUTOS = 20;

/**
 * Informa se um atendimento humano está aguardando resposta há pelo menos 20 minutos.
 *
 * O relógio só começa quando a última mensagem foi enviada pelo lead. Mensagens do atendente,
 * da IA ou do sistema encerram o estado de atraso para o objetivo deste aviso.
 */
export function atendenteEstaAtrasado(
  ultimaMensagemRemetenteTipo: RemetenteTipo | null,
  ultimaMensagemEm: string | null,
  status: StatusAtendimento,
  agora: Date = new Date(),
): boolean {
  if (
    ultimaMensagemRemetenteTipo !== "LEAD" ||
    !ultimaMensagemEm ||
    status !== "EM_ATENDIMENTO"
  ) {
    return false;
  }

  const ultimaMensagem = new Date(ultimaMensagemEm);
  if (Number.isNaN(ultimaMensagem.getTime())) return false;

  const limiteMs = LIMITE_DE_ATRASO_MINUTOS * 60 * 1000;
  return agora.getTime() - ultimaMensagem.getTime() >= limiteMs;
}
