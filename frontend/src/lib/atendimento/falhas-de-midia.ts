import type { MensagemResposta } from "./types";

/** Uma falha de anexo que pode ser reprocessada fora do ciclo de vida do composer. */
export type FalhaDeEnvioMidia = {
  id: string;
  atendimentoId: string;
  leadId: string;
  arquivo: File;
  motivo: string;
  legenda?: string;
  resposta?: { mensagemId: string; enviadoEm: string };
  citacao?: MensagemResposta["citacao"];
  /** Reutilizada no retry para não duplicar uma aceitação cuja resposta se perdeu. */
  idempotencyKey?: string;
};

/** Só expõe o detalhe RFC 7807 já sanitizado; erros sem contrato ficam na mensagem catalogada. */
export function motivoDaFalhaDeMidia(erro: unknown, mensagemGenerica: string): string {
  if (erro && typeof erro === "object" && "problema" in erro) {
    const problema = (erro as { problema?: { detail?: unknown } | null }).problema;
    if (typeof problema?.detail === "string" && problema.detail.length > 0) {
      return problema.detail;
    }
  }
  return mensagemGenerica;
}
