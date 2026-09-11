import type { NotificacaoTempoReal } from "./types";

export type ConversaAtiva =
  | { origem: "ATENDIMENTO"; id: string }
  | { origem: "CHAT_INTERNO"; id: string }
  | null;

let conversaAtiva: ConversaAtiva = null;

export function definirConversaAtiva(novaConversa: ConversaAtiva): void {
  conversaAtiva = novaConversa;
}

export function obterConversaAtiva(): ConversaAtiva {
  return conversaAtiva;
}

export interface DecisaoDeNotificacao {
  chave: string;
  exibir: boolean;
  tocar: boolean;
  atualizarAtendimentos: boolean;
  atualizarChatInterno: boolean;
}

export interface ContextoDaNotificacao {
  usuarioId: string | null;
  conversaAtiva: ConversaAtiva;
  somHabilitado: boolean;
  agora?: number;
}

const INTERVALO_DE_COALESCENCIA_DO_SOM_MS = 1500;

/**
 * Decide a apresentação de eventos recebidos pela fila pessoal. O componente visual não conhece
 * regras de autoria, conversa aberta ou deduplicação: isso fica aqui para que Atendimentos e Chat
 * interno tenham exatamente o mesmo comportamento.
 */
export class ServicoDeNotificacoesTempoReal {
  private readonly chavesProcessadas = new Set<string>();
  private ultimoSomEm: number | null = null;

  decidir(
    notificacao: NotificacaoTempoReal,
    contexto: ContextoDaNotificacao,
  ): DecisaoDeNotificacao | null {
    const chave = chaveTecnicaDaNotificacao(notificacao);
    if (this.chavesProcessadas.has(chave)) return null;
    this.chavesProcessadas.add(chave);

    const origem = origemDaNotificacao(notificacao);
    const atualizarAtendimentos = origem === "ATENDIMENTO" || notificacao.tipo === "TRANSFERENCIA_RECEBIDA";
    const atualizarChatInterno = origem === "CHAT_INTERNO";
    const ehMensagemInternaDeUsuario = notificacao.tipo === "CHAT_INTERNO_MENSAGEM"
      && notificacao.dados.tipo != null;
    const ehEventoVisual = notificacao.tipo === "NOVA_MENSAGEM"
      || notificacao.tipo === "TRANSFERENCIA_RECEBIDA"
      || notificacao.tipo === "ATENDIMENTO_DEVOLVIDO_PARA_IA"
      || ehMensagemInternaDeUsuario;

    if (notificacao.tipo === "CHAT_INTERNO_MENSAGEM"
      && contexto.usuarioId != null
      && notificacao.dados.remetenteId === contexto.usuarioId) {
      return { chave, exibir: false, tocar: false, atualizarAtendimentos, atualizarChatInterno };
    }
    if (notificacao.tipo === "TRANSFERENCIA_RECEBIDA"
      && contexto.usuarioId != null
      && notificacao.dados.quemTransferiu === contexto.usuarioId) {
      return { chave, exibir: false, tocar: false, atualizarAtendimentos, atualizarChatInterno };
    }

    const conversaDaNotificacao = conversaDaNotificacaoRecebida(notificacao);
    const conversaEstaAberta = conversaDaNotificacao != null
      && contexto.conversaAtiva?.origem === conversaDaNotificacao.origem
      && contexto.conversaAtiva.id === conversaDaNotificacao.id;
    const tocar = ehEventoVisual
      && !conversaEstaAberta
      && contexto.somHabilitado
      && this.podeTocar(contexto.agora ?? Date.now());

    return {
      chave,
      exibir: ehEventoVisual && !conversaEstaAberta,
      tocar,
      atualizarAtendimentos,
      atualizarChatInterno,
    };
  }

  private podeTocar(agora: number): boolean {
    if (this.ultimoSomEm != null && agora - this.ultimoSomEm < INTERVALO_DE_COALESCENCIA_DO_SOM_MS) {
      return false;
    }
    this.ultimoSomEm = agora;
    return true;
  }
}

export function chaveTecnicaDaNotificacao(notificacao: NotificacaoTempoReal): string {
  // Edições compartilham o mesmo mensagemId do evento de criação. Prefixar o tipo
  // antes de considerar eventoId evita que a deduplicação descarte a edição como
  // se fosse a mensagem original.
  if (notificacao.tipo === "CHAT_INTERNO_MENSAGEM_EDITADA") {
    return `${notificacao.tipo}:${notificacao.dados.mensagemId}`;
  }
  if (notificacao.eventoId) return notificacao.eventoId;
  if (notificacao.tipo === "NOVA_MENSAGEM" || notificacao.tipo === "CHAT_INTERNO_MENSAGEM") {
    return notificacao.dados.mensagemId;
  }
  if (notificacao.tipo === "CHAT_INTERNO_MENSAGEM_REMOVIDA") {
    return `${notificacao.tipo}:${notificacao.dados.mensagemId}`;
  }
  if (notificacao.tipo === "CHAT_INTERNO_REACAO") {
    return `${notificacao.tipo}:${notificacao.dados.mensagemId}:${notificacao.dados.atorId}`;
  }
  return [
    notificacao.tipo,
    notificacao.dados.atendimentoId,
    notificacao.dados.leadId,
    notificacao.dados.ocorridoEm,
  ].join(":");
}

function origemDaNotificacao(notificacao: NotificacaoTempoReal): "ATENDIMENTO" | "CHAT_INTERNO" {
  return notificacao.tipo === "CHAT_INTERNO_MENSAGEM"
    || notificacao.tipo === "CHAT_INTERNO_MENSAGEM_EDITADA"
    || notificacao.tipo === "CHAT_INTERNO_MENSAGEM_REMOVIDA"
    || notificacao.tipo === "CHAT_INTERNO_REACAO"
    ? "CHAT_INTERNO"
    : "ATENDIMENTO";
}

function conversaDaNotificacaoRecebida(notificacao: NotificacaoTempoReal): ConversaAtiva {
  if (notificacao.tipo === "NOVA_MENSAGEM") {
    return { origem: "ATENDIMENTO", id: notificacao.dados.atendimentoId };
  }
  if (notificacao.tipo === "CHAT_INTERNO_MENSAGEM" || notificacao.tipo === "CHAT_INTERNO_MENSAGEM_EDITADA") {
    return { origem: "CHAT_INTERNO", id: notificacao.dados.conversaId };
  }
  return null;
}
