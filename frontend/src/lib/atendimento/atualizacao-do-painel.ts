import type { QueryClient, QueryKey } from "@tanstack/react-query";

import { chaveTecnicaDaNotificacao } from "./servico-notificacoes-tempo-real";
import type { NotificacaoTempoReal } from "./types";

/**
 * Eventos que mudam QUEM enxerga um cartão ou pedem ação imediata de quem os recebe. Nunca
 * esperam a janela: transferência e devolução tiram/entregam o lead; convite põe o cartão em
 * Pendentes do convidado.
 */
const TIPOS_URGENTES: ReadonlySet<NotificacaoTempoReal["tipo"]> = new Set([
  "TRANSFERENCIA_RECEBIDA",
  "ATENDIMENTO_DEVOLVIDO_PARA_IA",
  "CONVITE_ATENDIMENTO",
]);

/**
 * E209 — intervalo mínimo entre dois refetches amplos do painel disparados por eventos. Cada
 * refetch amplo relê a contagem dos badges e TODAS as páginas já carregadas da inbox; sem este
 * limite, uma rajada de mensagens virava uma rajada igual de consultas por aba aberta.
 *
 * Constante técnica (como a coalescência do som de notificação), não regra de negócio: não muda
 * o que o usuário vê, só quantas vezes o mesmo dado é pedido de novo.
 */
export const JANELA_DE_COALESCENCIA_MS = 2000;

/** Eventos já tratados; evita que o mesmo evento, visto por dois ouvintes, peça duas vezes. */
const LIMITE_DE_CHAVES_LEMBRADAS = 500;

export interface PedidoDeAtualizacao {
  /** Identidade do evento de origem (mesma chave técnica das notificações). */
  chave?: string;
  /** Atendimento cujo estado canônico (`["atendimentos", "estado", id]`) também deve ser relido. */
  atendimentoId?: string | null;
  /**
   * Executa já, sem esperar a janela, e relê todos os estados abertos. Para o que protege
   * visibilidade ou responde a um clique: transferência, devolução para a IA, convite e revogação.
   */
  urgente?: boolean;
}

export interface Relogio {
  agora(): number;
  agendar(acao: () => void, atrasoMs: number): unknown;
  cancelar(agendamento: unknown): void;
}

const relogioDoNavegador: Relogio = {
  agora: () => Date.now(),
  agendar: (acao, atrasoMs) => globalThis.setTimeout(acao, atrasoMs),
  cancelar: (agendamento) => globalThis.clearTimeout(agendamento as ReturnType<typeof setTimeout>),
};

/**
 * Coalesce os pedidos de "a lista de atendimentos mudou". O primeiro pedido depois de um período
 * quieto executa imediatamente (a lista continua reagindo na hora a uma mensagem isolada); os
 * seguintes, dentro da janela, viram UM refetch no fim dela. Pedido urgente nunca espera.
 *
 * Só as chaves amplas (inbox, contagem, listas legadas) são relidas em todo refetch. O estado
 * canônico de um atendimento só é relido quando algum evento do lote é dele — antes, qualquer
 * mensagem de qualquer lead relia o `/estado` da conversa aberta.
 */
export class AgendadorDeAtualizacaoDoPainel {
  private ultimaExecucao = Number.NEGATIVE_INFINITY;
  private agendamento: unknown = null;
  private readonly atendimentosDoLote = new Set<string>();
  private readonly chavesLembradas = new Set<string>();

  constructor(
    private readonly cache: QueryClient,
    private readonly relogio: Relogio = relogioDoNavegador,
    private readonly janelaMs: number = JANELA_DE_COALESCENCIA_MS,
  ) {}

  solicitar(pedido: PedidoDeAtualizacao = {}): void {
    if (pedido.chave) {
      if (this.chavesLembradas.has(pedido.chave)) return;
      this.lembrar(pedido.chave);
    }
    if (pedido.atendimentoId) this.atendimentosDoLote.add(pedido.atendimentoId);

    const decorrido = this.relogio.agora() - this.ultimaExecucao;
    if (pedido.urgente || decorrido >= this.janelaMs) {
      this.executar(pedido.urgente === true);
      return;
    }
    if (this.agendamento === null) {
      this.agendamento = this.relogio.agendar(() => this.executar(false), this.janelaMs - decorrido);
    }
  }

  private executar(todosOsEstados: boolean): void {
    if (this.agendamento !== null) {
      this.relogio.cancelar(this.agendamento);
      this.agendamento = null;
    }
    this.ultimaExecucao = this.relogio.agora();
    const atendimentos = new Set(this.atendimentosDoLote);
    this.atendimentosDoLote.clear();
    void this.cache.invalidateQueries({
      queryKey: ["atendimentos"],
      predicate: (query) => todosOsEstados || deveReler(query.queryKey, atendimentos),
    });
  }

  private lembrar(chave: string): void {
    this.chavesLembradas.add(chave);
    if (this.chavesLembradas.size > LIMITE_DE_CHAVES_LEMBRADAS) {
      const maisAntiga = this.chavesLembradas.values().next().value;
      if (maisAntiga !== undefined) this.chavesLembradas.delete(maisAntiga);
    }
  }
}

function deveReler(chave: QueryKey, atendimentos: ReadonlySet<string>): boolean {
  if (chave[1] !== "estado") return true;
  return typeof chave[2] === "string" && atendimentos.has(chave[2]);
}

/** O mesmo evento produz o mesmo pedido em qualquer ouvinte — e por isso é deduplicado. */
export function pedidoDaNotificacao(notificacao: NotificacaoTempoReal): PedidoDeAtualizacao {
  return {
    chave: chaveTecnicaDaNotificacao(notificacao),
    atendimentoId: "atendimentoId" in notificacao.dados ? notificacao.dados.atendimentoId : null,
    urgente: TIPOS_URGENTES.has(notificacao.tipo),
  };
}

const agendadores = new WeakMap<QueryClient, AgendadorDeAtualizacaoDoPainel>();

/**
 * Ponto único para pedir que a lista de atendimentos seja relida. Um agendador por QueryClient
 * (uma aba do navegador): o ouvinte global de notificações e o da tela de Atendimentos recebem o
 * mesmo evento e, por aqui, disparam um refetch só.
 */
export function atualizarPainelDeAtendimentos(
  cache: QueryClient,
  pedido: PedidoDeAtualizacao = {},
): void {
  let agendador = agendadores.get(cache);
  if (!agendador) {
    agendador = new AgendadorDeAtualizacaoDoPainel(cache);
    agendadores.set(cache, agendador);
  }
  agendador.solicitar(pedido);
}
