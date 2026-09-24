"use client";

import { useEffect, useState } from "react";

import { Client, type IMessage, type StompSubscription } from "@stomp/stompjs";

import type {
  EventoTempoReal,
  MensagemResposta,
  NotificacaoTempoReal,
  RevogacaoTempoReal,
} from "./types";

export type EstadoConexao = "conectando" | "conectado" | "reconectando" | "desconectado";

/** Só o subconjunto de `@stomp/stompjs` Client que usamos — permite injetar um dublê em teste. */
export interface ClienteStompLike {
  activate(): void;
  deactivate(): unknown;
  subscribe(destino: string, callback: (mensagem: IMessage) => void): StompSubscription;
  onConnect?: (frame?: { headers?: Record<string, string> }) => void;
  onWebSocketClose?: () => void;
  onStompError?: () => void;
  connected: boolean;
}

export type OuvinteDeNotificacao = (notificacao: NotificacaoTempoReal) => void;

export interface OpcoesConexaoTempoReal {
  brokerUrl: string;
  obterAccessToken: () => string | null;
  onEstadoMudou?: (estado: EstadoConexao) => void;
  /** A conversa aberta foi revogada (ex.: transferência) — quem chama decide o que mostrar. */
  onRevogacao?: (atendimentoId: string) => void;
  onNotificacao?: (notificacao: NotificacaoTempoReal) => void;
  /** Injeção para teste — por padrão cria um Client real do @stomp/stompjs. */
  criarCliente?: (opcoes: { brokerUrl: string; accessToken: string | null }) => ClienteStompLike;
}

const DESTINO_REVOGACOES = "/user/queue/revogacoes";
export const DESTINO_NOTIFICACOES = "/user/queue/notificacoes";
const destinoAtendimento = (id: string) => `/user/queue/atendimento.${id}`;

/**
 * Pulso STOMP, o mesmo valor configurado no backend (`synapse.tempo-real.heartbeat-*`, 10s).
 *
 * Sem heartbeat os dois lados negociam `0,0` e uma conexão que morre em silêncio (proxy derrubando
 * conexão ociosa, troca de rede, laptop suspenso) só é percebida quando o TCP do navegador desiste,
 * sem prazo previsível. Como esta tela não tem polling por decisão de projeto (docs/40), esse prazo
 * é o tempo que o atendente fica sem ver a mensagem que já chegou. Com o pulso, o stompjs fecha a
 * conexão em cerca de dois intervalos e o reconector com backoff assume.
 */
export const HEARTBEAT_MS = 10_000;

export const CABECALHOS_DE_BACKOFF_DE_RECONEXAO = {
  atrasoInicialMs: "x-synapse-reconexao-atraso-inicial-ms",
  fator: "x-synapse-reconexao-fator",
  atrasoMaximoMs: "x-synapse-reconexao-atraso-maximo-ms",
} as const;

export interface ConfiguracaoDeBackoffDeReconexao {
  atrasoInicialMs: number;
  fator: number;
  atrasoMaximoMs: number;
}

export const CONFIGURACAO_DE_BACKOFF_PADRAO: ConfiguracaoDeBackoffDeReconexao = {
  atrasoInicialMs: 1_000,
  fator: 2,
  atrasoMaximoMs: 30_000,
};

function numeroPositivoConfigurado(valor: string | undefined, padrao: number, minimo: number): number {
  const numero = Number(valor);
  return Number.isFinite(numero) && numero >= minimo ? numero : padrao;
}

/**
 * O primeiro CONNECT usa defaults seguros. Depois, o backend anuncia a configuração efetiva no
 * frame CONNECTED; assim a imagem genérica do frontend não precisa ser reconstruída por filho.
 */
export function configuracaoDeBackoffDoServidor(
  cabecalhos: Record<string, string> | undefined,
): ConfiguracaoDeBackoffDeReconexao {
  const atrasoInicialMs = numeroPositivoConfigurado(
    cabecalhos?.[CABECALHOS_DE_BACKOFF_DE_RECONEXAO.atrasoInicialMs],
    CONFIGURACAO_DE_BACKOFF_PADRAO.atrasoInicialMs,
    1,
  );
  const fator = numeroPositivoConfigurado(
    cabecalhos?.[CABECALHOS_DE_BACKOFF_DE_RECONEXAO.fator],
    CONFIGURACAO_DE_BACKOFF_PADRAO.fator,
    1.01,
  );
  const atrasoMaximoMs = Math.max(
    atrasoInicialMs,
    numeroPositivoConfigurado(
      cabecalhos?.[CABECALHOS_DE_BACKOFF_DE_RECONEXAO.atrasoMaximoMs],
      CONFIGURACAO_DE_BACKOFF_PADRAO.atrasoMaximoMs,
      1,
    ),
  );
  return { atrasoInicialMs, fator, atrasoMaximoMs };
}

const CONFIGURACAO_DE_BACKOFF_DE_RECONEXAO = configuracaoDeBackoffDoServidor(undefined);

export function clienteStompPadrao(opcoes: {
  brokerUrl: string;
  accessToken: string | null;
}): ClienteStompLike {
  // Cast necessário: os handlers do Client real recebem um frame (IFrame/CloseEvent) que aqui
  // nunca usamos — a interface ClienteStompLike só declara a forma que de fato consumimos.
  return new Client({
    brokerURL: `${opcoes.brokerUrl}?access_token=${encodeURIComponent(opcoes.accessToken ?? "")}`,
    // Backoff é nosso, não o do stompjs (que é delay fixo) — ver calcularBackoffMs.
    reconnectDelay: 0,
    heartbeatIncoming: HEARTBEAT_MS,
    heartbeatOutgoing: HEARTBEAT_MS,
  }) as unknown as ClienteStompLike;
}

/** URL explicita em dev; na imagem generica usa a origem HTTPS que o browser realmente abriu. */
function resolverBrokerUrl(configurada: string): string {
  if (configurada) {
    return configurada;
  }
  if (typeof window === "undefined") {
    return "";
  }
  const protocolo = window.location.protocol === "https:" ? "wss:" : "ws:";
  return `${protocolo}//${window.location.host}/ws`;
}

/**
 * Backoff exponencial com teto e jitter. `comJitter: false` existe só para o teste determinístico
 * da progressão base/teto — em produção o jitter evita que várias abas reconectem no mesmo instante
 * exato (thundering herd contra o servidor).
 */
export function calcularBackoffMs(
  tentativa: number,
  comJitter = true,
  configuracao = CONFIGURACAO_DE_BACKOFF_DE_RECONEXAO,
): number {
  const tentativaSegura = Math.max(0, Math.floor(tentativa));
  const bruto = Math.min(
    configuracao.atrasoInicialMs * configuracao.fator ** tentativaSegura,
    configuracao.atrasoMaximoMs,
  );
  if (!comJitter) {
    return bruto;
  }
  return Math.round(bruto * (0.5 + Math.random() * 0.5));
}

function mesmaMensagem(a: MensagemResposta, b: MensagemResposta): boolean {
  return a.id === b.id
    || (a.idempotencyKey != null
      && b.idempotencyKey != null
      && a.idempotencyKey === b.idempotencyKey);
}

function idPreferencial(anterior: string, nova: string): string {
  if (anterior.startsWith("temp-") && !nova.startsWith("temp-")) return nova;
  return nova.startsWith("temp-") && !anterior.startsWith("temp-") ? anterior : nova;
}

function statusMaisAvancado(
  anterior: MensagemResposta["statusEntrega"],
  novo: MensagemResposta["statusEntrega"],
): MensagemResposta["statusEntrega"] {
  if (anterior === "FALHOU") return novo === "PENDENTE" ? anterior : novo;
  if (novo === "FALHOU") return anterior === "PENDENTE" ? novo : anterior;
  const ordem = { PENDENTE: 0, ENVIADO: 1, ENTREGUE: 2, LIDO: 3, FALHOU: 0 } as const;
  return ordem[novo] >= ordem[anterior] ? novo : anterior;
}

function fundirMensagem(anterior: MensagemResposta, nova: MensagemResposta): MensagemResposta {
  const statusEntrega = statusMaisAvancado(anterior.statusEntrega, nova.statusEntrega);
  const manterMidiaAnterior = nova.midiaUrl?.startsWith("blob:") && anterior.midiaUrl != null;
  return {
    ...anterior,
    ...nova,
    id: idPreferencial(anterior.id, nova.id),
    atendimentoId: nova.atendimentoId ?? anterior.atendimentoId,
    remetenteId: nova.remetenteId ?? anterior.remetenteId,
    remetenteNome: nova.remetenteNome ?? anterior.remetenteNome,
    midiaUrl: manterMidiaAnterior ? anterior.midiaUrl : nova.midiaUrl ?? anterior.midiaUrl,
    midiaMetadados: nova.midiaMetadados ?? anterior.midiaMetadados,
    statusEntrega,
    erroEntrega:
      statusEntrega === "FALHOU"
        ? (nova.statusEntrega === "FALHOU" ? nova.erroEntrega : anterior.erroEntrega)
        : null,
    idempotencyKey: nova.idempotencyKey ?? anterior.idempotencyKey,
  };
}

/**
 * Funde histórico, resposta HTTP e WebSocket pelas duas identidades duráveis que podem coexistir:
 * `id` do servidor e `idempotencyKey` do clique. Um evento que traz ambas também une entradas que
 * chegaram por caminhos distintos. Status nunca recua quando a resposta HTTP chega depois do WS.
 */
export function mesclarMensagens(
  existentes: MensagemResposta[],
  novas: MensagemResposta[],
): MensagemResposta[] {
  const resultado: MensagemResposta[] = [];
  for (const mensagem of [...existentes, ...novas]) {
    const indices = resultado
      .map((existente, indice) => (mesmaMensagem(existente, mensagem) ? indice : -1))
      .filter((indice) => indice >= 0);
    if (indices.length === 0) {
      resultado.push(mensagem);
      continue;
    }

    let fundida = mensagem;
    for (const indice of indices) {
      fundida = fundirMensagem(resultado[indice], fundida);
    }
    for (const indice of [...indices].reverse()) {
      resultado.splice(indice, 1);
    }
    resultado.push(fundida);
  }
  return resultado.sort(
    (a, b) => new Date(a.enviadoEm).getTime() - new Date(b.enviadoEm).getTime(),
  );
}

/**
 * Conexão STOMP com a assinatura do atendimento aberto e a fila de revogações — sempre a mesma
 * ordem em cada (re)conexão: assina primeiro, só então avisa "conectado" (via `onEstadoMudou`), que
 * é o gancho que o chamador usa para buscar o que perdeu offline. Nunca o contrário: buscar antes
 * de assinar deixaria uma janela em que algo que chega entre o fetch e a assinatura se perde.
 */
export class ConexaoTempoReal {
  private cliente: ClienteStompLike | null = null;
  private assinaturaAtendimento: StompSubscription | null = null;
  private assinaturaNotificacoes: StompSubscription | null = null;
  private atendimentoAberto: string | null = null;
  private onEventoAtual: ((evento: EventoTempoReal) => void) | null = null;
  private tentativas = 0;
  private configuracaoDeBackoff = CONFIGURACAO_DE_BACKOFF_DE_RECONEXAO;
  private timerReconexao: ReturnType<typeof setTimeout> | null = null;
  private desativadoManualmente = false;
  private leitorDeAccessToken: () => string | null;
  private readonly ouvintesDeNotificacao = new Set<OuvinteDeNotificacao>();
  private readonly ouvintesDeRevogacao = new Set<(atendimentoId: string) => void>();
  private readonly ouvintesDeEstado = new Set<(estado: EstadoConexao) => void>();
  private estadoAtual: EstadoConexao = "desconectado";

  constructor(private readonly opcoes: OpcoesConexaoTempoReal) {
    this.leitorDeAccessToken = opcoes.obterAccessToken;
  }

  atualizarLeitorDeAccessToken(leitor: () => string | null): void {
    this.leitorDeAccessToken = leitor;
  }

  adicionarOuvinteDeNotificacao(ouvinte: OuvinteDeNotificacao): () => void {
    this.ouvintesDeNotificacao.add(ouvinte);
    return () => this.ouvintesDeNotificacao.delete(ouvinte);
  }

  /**
   * A conexão é compartilhada: quem assina depois de ela já estar de pé (a tela de Atendimentos,
   * montada após o `NotificacoesTempoReal` do layout) recebe o estado atual na hora. Sem isso o
   * ouvinte tardio ficaria em "desconectado" até a próxima reconexão.
   */
  adicionarOuvinteDeEstado(ouvinte: (estado: EstadoConexao) => void): () => void {
    this.ouvintesDeEstado.add(ouvinte);
    ouvinte(this.estadoAtual);
    return () => this.ouvintesDeEstado.delete(ouvinte);
  }

  adicionarOuvinteDeRevogacao(ouvinte: (atendimentoId: string) => void): () => void {
    this.ouvintesDeRevogacao.add(ouvinte);
    return () => this.ouvintesDeRevogacao.delete(ouvinte);
  }

  conectar(): void {
    this.desativadoManualmente = false;
    this.abrirClienteEConectar();
  }

  desconectar(): void {
    this.desativadoManualmente = true;
    if (this.timerReconexao) {
      clearTimeout(this.timerReconexao);
      this.timerReconexao = null;
    }
    this.atendimentoAberto = null;
    this.onEventoAtual = null;
    this.descartarCliente();
    this.emitirEstado("desconectado");
  }

  /**
   * Encerra o cliente atual antes de outro assumir o lugar (E208). Sem isto, cada renovação do
   * access token (a cada 15 min) e cada reconexão abriam um socket novo e deixavam o anterior vivo,
   * com heartbeat e assinaturas: em produção chegou a mais de 130 sessões STOMP por usuário, e as
   * revalidações dessas assinaturas esgotaram o pool de banco do chat. Os handlers do cliente
   * descartado viram no-op — o fechamento dele não pode agendar reconexão nem trocar o cliente atual.
   */
  private descartarCliente(): void {
    if (this.timerReconexao) {
      clearTimeout(this.timerReconexao);
      this.timerReconexao = null;
    }
    const anterior = this.cliente;
    this.cliente = null;
    this.assinaturaAtendimento = null;
    this.assinaturaNotificacoes = null;
    if (!anterior) {
      return;
    }
    const ignorar = () => undefined;
    anterior.onConnect = ignorar;
    anterior.onWebSocketClose = ignorar;
    anterior.onStompError = ignorar;
    anterior.deactivate();
  }

  /** Desassina a conversa anterior (se houver) antes de assinar a nova. */
  abrirConversa(atendimentoId: string, onEvento: (evento: EventoTempoReal) => void): void {
    this.assinaturaAtendimento?.unsubscribe();
    this.atendimentoAberto = atendimentoId;
    this.onEventoAtual = onEvento;
    this.assinaturaAtendimento =
        this.cliente?.connected ? (this.assinar(atendimentoId, onEvento) ?? null) : null;
  }

  fecharConversa(): void {
    this.assinaturaAtendimento?.unsubscribe();
    this.assinaturaAtendimento = null;
    this.atendimentoAberto = null;
    this.onEventoAtual = null;
  }

  private assinar(
    atendimentoId: string,
    onEvento: (evento: EventoTempoReal) => void,
  ): StompSubscription | undefined {
    const clienteDaAssinatura = this.cliente;
    return clienteDaAssinatura?.subscribe(destinoAtendimento(atendimentoId), (mensagem) => {
      // unsubscribe é assíncrono no broker. Um frame que já estava em trânsito da conversa
      // anterior não pode alcançar os callbacks (nem o cache) da conversa atualmente aberta.
      if (
        this.cliente !== clienteDaAssinatura
        || this.atendimentoAberto !== atendimentoId
        || this.onEventoAtual !== onEvento
      ) {
        return;
      }
      const evento = JSON.parse(mensagem.body) as EventoTempoReal;
      if (evento.dados.atendimentoId !== atendimentoId) {
        return;
      }
      onEvento(evento);
    });
  }

  private abrirClienteEConectar(): void {
    this.descartarCliente();
    const accessToken = this.leitorDeAccessToken();
    if (!accessToken) {
      this.emitirEstado("desconectado");
      return;
    }
    const cliente = (this.opcoes.criarCliente ?? clienteStompPadrao)({
      brokerUrl: resolverBrokerUrl(this.opcoes.brokerUrl),
      accessToken,
    });
    this.cliente = cliente;

    cliente.onConnect = (frame) => {
      if (this.cliente !== cliente) {
        // CONNECTED tardio de um cliente já substituído: não assina nada nem anuncia "conectado".
        cliente.deactivate();
        return;
      }
      this.configuracaoDeBackoff = configuracaoDeBackoffDoServidor(frame?.headers);
      this.tentativas = 0;
      cliente.subscribe(DESTINO_REVOGACOES, (mensagem) => {
        const revogacao = JSON.parse(mensagem.body) as RevogacaoTempoReal;
        if (revogacao.atendimentoId === this.atendimentoAberto) {
          this.fecharConversa();
          this.opcoes.onRevogacao?.(revogacao.atendimentoId);
          for (const ouvinte of this.ouvintesDeRevogacao) {
            ouvinte(revogacao.atendimentoId);
          }
        }
      });
      this.assinaturaNotificacoes = cliente.subscribe(DESTINO_NOTIFICACOES, (mensagem) => {
        const notificacao = JSON.parse(mensagem.body) as NotificacaoTempoReal;
        if (
          notificacao.tipo === "NOVA_MENSAGEM" ||
          notificacao.tipo === "ATENDIMENTO_ESTADO" ||
          notificacao.tipo === "TRANSFERENCIA_RECEBIDA" ||
          notificacao.tipo === "ATENDIMENTO_DEVOLVIDO_PARA_IA" ||
          notificacao.tipo === "CONVITE_ATENDIMENTO" ||
          notificacao.tipo === "CHAT_INTERNO_MENSAGEM" ||
          notificacao.tipo === "CHAT_INTERNO_MENSAGEM_EDITADA" ||
          notificacao.tipo === "CHAT_INTERNO_MENSAGEM_REMOVIDA" ||
          notificacao.tipo === "CHAT_INTERNO_REACAO"
        ) {
          this.opcoes.onNotificacao?.(notificacao);
          for (const ouvinte of this.ouvintesDeNotificacao) {
            ouvinte(notificacao);
          }
        }
      });
      if (this.atendimentoAberto && this.onEventoAtual) {
        this.assinaturaAtendimento = this.assinar(this.atendimentoAberto, this.onEventoAtual) ?? null;
      }
      // Só depois de assinar: é este callback que o chamador usa como gatilho do backfill.
      this.emitirEstado("conectado");
    };

    const agendarReconexao = () => {
      // Só o cliente atual reconecta. Um cliente já substituído que fecha depois não pode
      // derrubar o atual nem abrir mais um socket — era isso que multiplicava as sessões.
      if (this.desativadoManualmente || this.cliente !== cliente || this.timerReconexao) {
        return;
      }
      this.emitirEstado("reconectando");
      const atraso = calcularBackoffMs(this.tentativas, true, this.configuracaoDeBackoff);
      this.tentativas += 1;
      this.timerReconexao = setTimeout(() => {
        this.timerReconexao = null;
        this.abrirClienteEConectar();
      }, atraso);
    };

    cliente.onWebSocketClose = agendarReconexao;
    cliente.onStompError = agendarReconexao;

    this.emitirEstado("conectando");
    cliente.activate();
  }

  private emitirEstado(estado: EstadoConexao): void {
    this.estadoAtual = estado;
    this.opcoes.onEstadoMudou?.(estado);
    for (const ouvinte of this.ouvintesDeEstado) {
      ouvinte(estado);
    }
  }
}

/**
 * Uma conexão por sessão da tela de Atendimentos, não uma por conversa — trocar de conversa
 * reassina o mesmo socket (`abrirConversa`), nunca reconecta do zero. `useState` com inicializador
 * preguiçoso (e não `useRef`) porque o valor participa do render — o React Compiler proíbe ler
 * `ref.current` durante o render; `useState(() => ...)` cria a instância uma única vez e devolve um
 * valor estável do jeito que o render pode consumir diretamente.
 */
export function useConexaoTempoReal(
  obterAccessToken: () => string | null,
  onRevogacao?: (atendimentoId: string) => void,
  onNotificacao?: (notificacao: NotificacaoTempoReal) => void,
): { conexao: ConexaoTempoReal; estado: EstadoConexao; ciclo: number } {
  const [estado, setEstado] = useState<EstadoConexao>("desconectado");
  const [ciclo, setCiclo] = useState(0);
  const [conexao] = useState(() => obterConexaoTempoRealCompartilhada(obterAccessToken));
  const accessTokenAtual = obterAccessToken();

  useEffect(() => {
    conexao.atualizarLeitorDeAccessToken(obterAccessToken);
  }, [conexao, obterAccessToken]);

  useEffect(() => {
    const removerOuvinte = conexao.adicionarOuvinteDeEstado((proximo) => {
      setEstado(proximo);
      if (proximo === "conectado") setCiclo((atual) => atual + 1);
    });
    return () => {
      removerOuvinte();
      setEstado("desconectado");
    };
  }, [conexao]);

  useEffect(() => {
    if (!onNotificacao) return;
    return conexao.adicionarOuvinteDeNotificacao(onNotificacao);
  }, [conexao, onNotificacao]);

  useEffect(() => {
    if (!onRevogacao) return;
    // Revogações continuam vinculadas à tela que possui a conversa aberta. A fila pessoal é
    // compartilhada, mas somente esse consumidor deve fechar o painel que ele controla.
    return conexao.adicionarOuvinteDeRevogacao(onRevogacao);
  }, [conexao, onRevogacao]);

  useEffect(() => {
    consumidoresDaConexao += 1;
    if (accessTokenAtual && (consumidoresDaConexao === 1 || tokenDaConexao !== accessTokenAtual)) {
      tokenDaConexao = accessTokenAtual;
      conexao.conectar();
    }
    return () => {
      consumidoresDaConexao -= 1;
      if (consumidoresDaConexao === 0) {
        conexao.desconectar();
        conexaoCompartilhada = null;
        tokenDaConexao = null;
      }
    };
  }, [accessTokenAtual, conexao]);

  return { conexao, estado, ciclo };
}

let conexaoCompartilhada: ConexaoTempoReal | null = null;
let consumidoresDaConexao = 0;
let tokenDaConexao: string | null = null;

function obterConexaoTempoRealCompartilhada(obterAccessToken: () => string | null): ConexaoTempoReal {
  if (!conexaoCompartilhada) {
    conexaoCompartilhada = new ConexaoTempoReal({
      brokerUrl: process.env.NEXT_PUBLIC_WS_URL ?? "",
      obterAccessToken,
    });
  }
  return conexaoCompartilhada;
}
