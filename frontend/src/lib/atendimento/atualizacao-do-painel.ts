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
 * E209/E225 — intervalo mínimo entre dois refetches da LISTA (inbox e listas legadas) disparados por eventos. Cada
 * refetch relê TODAS as páginas já carregadas da inbox; sem este limite, uma rajada de mensagens virava uma rajada
 * igual de consultas por aba aberta. A E225 subiu de 2 s para 5 s: no máximo 12 recargas da lista por minuto por aba
 * (eram 30), ao custo de a lista poder ficar até 5 s defasada quando chegam eventos em sequência.
 *
 * Constante técnica (como a coalescência do som de notificação), não regra de negócio: não muda
 * o que o usuário vê, só quantas vezes o mesmo dado é pedido de novo.
 */
export const JANELA_DA_LISTA_MS = 5_000;

/**
 * E225 — intervalo mínimo entre dois refetches da CONTAGEM dos badges. Maior que o da lista: o número muda menos
 * do que o cartão e é a consulta que mais pesa (uma por aba do papel a cada chamada). No máximo 6 por minuto por aba.
 */
export const JANELA_DA_CONTAGEM_MS = 10_000;

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
   * Também ignora a regra de aba oculta: o dado é relido na hora.
   */
  urgente?: boolean;
}

export interface Relogio {
  agora(): number;
  agendar(acao: () => void, atrasoMs: number): unknown;
  cancelar(agendamento: unknown): void;
}

/** O que o agendador precisa saber da aba: se alguém está olhando para ela. */
export interface AmbienteDaAba {
  visivel(): boolean;
}

const relogioDoNavegador: Relogio = {
  agora: () => Date.now(),
  agendar: (acao, atrasoMs) => globalThis.setTimeout(acao, atrasoMs),
  cancelar: (agendamento) => globalThis.clearTimeout(agendamento as ReturnType<typeof setTimeout>),
};

const ambienteDoNavegador: AmbienteDaAba = {
  visivel: () => typeof document === "undefined" || document.visibilityState !== "hidden",
};

export interface JanelasDoPainel {
  listaMs: number;
  contagemMs: number;
}

const JANELAS_PADRAO: JanelasDoPainel = { listaMs: JANELA_DA_LISTA_MS, contagemMs: JANELA_DA_CONTAGEM_MS };

/**
 * Uma janela de coalescência: o primeiro pedido depois de um período quieto executa na hora; os seguintes, dentro da
 * janela, viram UM disparo no fim dela.
 */
class JanelaDeCoalescencia {
  private ultimaExecucao = Number.NEGATIVE_INFINITY;
  private agendamento: unknown = null;

  constructor(
    private readonly relogio: Relogio,
    private readonly janelaMs: number,
    private readonly executar: () => void,
  ) {}

  solicitar(): void {
    const decorrido = this.relogio.agora() - this.ultimaExecucao;
    if (decorrido >= this.janelaMs) {
      this.disparar();
      return;
    }
    if (this.agendamento === null) {
      this.agendamento = this.relogio.agendar(() => this.disparar(), this.janelaMs - decorrido);
    }
  }

  /** Um pedido urgente já releu tudo: o que esperava a janela está coberto e a janela recomeça agora. */
  cobertaPorRecargaAmpla(): void {
    this.cancelarPendente();
    this.ultimaExecucao = this.relogio.agora();
  }

  private disparar(): void {
    this.cancelarPendente();
    this.ultimaExecucao = this.relogio.agora();
    this.executar();
  }

  private cancelarPendente(): void {
    if (this.agendamento !== null) {
      this.relogio.cancelar(this.agendamento);
      this.agendamento = null;
    }
  }
}

/**
 * Coalesce os pedidos de "a lista de atendimentos mudou", com DUAS janelas independentes (E225): a lista
 * (5 s) e a contagem dos badges (10 s). Em cada uma o primeiro pedido depois de um período quieto executa
 * imediatamente (a tela continua reagindo na hora a uma mensagem isolada); os seguintes viram um refetch no fim da
 * janela. Pedido urgente nunca espera e relê tudo.
 *
 * Aba oculta não recarrega (E225): o evento só marca as consultas como desatualizadas (`refetchType: "none"`); ao
 * voltar para a aba o React Query relê as ativas desatualizadas (foco da janela). Pedido urgente é a exceção.
 *
 * Só as chaves amplas (inbox, listas legadas) são relidas em todo refetch da lista. O estado canônico de um
 * atendimento só é relido quando algum evento do lote é dele — antes, qualquer mensagem de qualquer lead relia o
 * `/estado` da conversa aberta. Consultas fora do prefixo `["atendimentos"]` (as listas dos diálogos) não são tocadas.
 */
export class AgendadorDeAtualizacaoDoPainel {
  private readonly atendimentosDoLote = new Set<string>();
  private readonly chavesLembradas = new Set<string>();
  private readonly lista: JanelaDeCoalescencia;
  private readonly contagem: JanelaDeCoalescencia;

  constructor(
    private readonly cache: QueryClient,
    relogio: Relogio = relogioDoNavegador,
    janelas: JanelasDoPainel = JANELAS_PADRAO,
    private readonly ambiente: AmbienteDaAba = ambienteDoNavegador,
  ) {
    this.lista = new JanelaDeCoalescencia(relogio, janelas.listaMs, () => this.recarregarLista());
    this.contagem = new JanelaDeCoalescencia(relogio, janelas.contagemMs, () => this.recarregarContagem());
  }

  solicitar(pedido: PedidoDeAtualizacao = {}): void {
    if (pedido.chave) {
      if (this.chavesLembradas.has(pedido.chave)) return;
      this.lembrar(pedido.chave);
    }
    if (pedido.atendimentoId) this.atendimentosDoLote.add(pedido.atendimentoId);

    if (pedido.urgente) {
      this.recarregarTudo();
      return;
    }
    this.lista.solicitar();
    this.contagem.solicitar();
  }

  private recarregarLista(): void {
    const atendimentos = new Set(this.atendimentosDoLote);
    this.atendimentosDoLote.clear();
    void this.cache.invalidateQueries({
      queryKey: ["atendimentos"],
      predicate: (query) => !ehContagem(query.queryKey) && deveReler(query.queryKey, atendimentos),
      refetchType: this.tipoDeRecarga(),
    });
  }

  private recarregarContagem(): void {
    void this.cache.invalidateQueries({
      queryKey: ["atendimentos", "contagem"],
      refetchType: this.tipoDeRecarga(),
    });
  }

  private recarregarTudo(): void {
    this.atendimentosDoLote.clear();
    this.lista.cobertaPorRecargaAmpla();
    this.contagem.cobertaPorRecargaAmpla();
    void this.cache.invalidateQueries({ queryKey: ["atendimentos"] });
  }

  private tipoDeRecarga(): "active" | "none" {
    return this.ambiente.visivel() ? "active" : "none";
  }

  private lembrar(chave: string): void {
    this.chavesLembradas.add(chave);
    if (this.chavesLembradas.size > LIMITE_DE_CHAVES_LEMBRADAS) {
      const maisAntiga = this.chavesLembradas.values().next().value;
      if (maisAntiga !== undefined) this.chavesLembradas.delete(maisAntiga);
    }
  }
}

function ehContagem(chave: QueryKey): boolean {
  return chave[1] === "contagem";
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
