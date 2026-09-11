import { ErroDeApi } from "@/lib/api/errors";

import type { NovoContatoResposta } from "./types";

export type TextosDeAberturaDeAtendimento = {
  erroAcesso: string;
  erroNaoEncontrado: string;
  erroConflito: string;
  erroGenerico: string;
};

/** A resposta do comando é a identidade canônica da conversa que deve ser aberta. */
export function destinoDaAberturaDeAtendimento({
  leadId,
  atendimentoId,
}: Pick<NovoContatoResposta, "leadId" | "atendimentoId">): string {
  const parametros = new URLSearchParams({ leadId, atendimentoId, visao: "ATIVOS" });
  return `/atendimentos?${parametros.toString()}`;
}

/** Nenhuma falha de abertura fica muda; status previstos recebem orientação estável do catálogo. */
export function mensagemDaFalhaDeAbertura(
  erro: unknown,
  textos: TextosDeAberturaDeAtendimento,
): string {
  if (!(erro instanceof ErroDeApi)) {
    return erro instanceof Error && erro.message ? erro.message : textos.erroGenerico;
  }
  if (erro.status === 403) return textos.erroAcesso;
  if (erro.status === 404) return textos.erroNaoEncontrado;
  if (erro.status === 409) return textos.erroConflito;
  return erro.message || textos.erroGenerico;
}

export type DiagnosticoDeAbertura = {
  origem: "agenda" | "lista" | "rota";
  etapa: "solicitada" | "confirmada" | "cartao_resolvido" | "falhou" | "evento_websocket";
  leadId?: string | null;
  atendimentoId?: string | null;
  usuarioId?: string | null;
  papel?: string | null;
  visao?: string | null;
  filtrosAtivos?: number;
  cartaoNaLista?: boolean;
  cartaoSelecionadoId?: string | null;
  httpStatus?: number;
  evento?: string;
};

/**
 * Diagnóstico deliberadamente mínimo para suporte: ids técnicos, papel e estado, nunca texto de
 * mensagem, telefone, token ou conteúdo de filtro. A infraestrutura atual não tem coletor de
 * telemetria do navegador; o evento estruturado fica disponível no console da sessão afetada.
 */
export function registrarDiagnosticoDeAbertura(evento: DiagnosticoDeAbertura): void {
  console.info("[atendimento.abertura]", evento);
}

export function statusHttpDoErro(erro: unknown): number | undefined {
  return erro instanceof ErroDeApi ? erro.status : undefined;
}
