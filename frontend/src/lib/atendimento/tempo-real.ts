"use client";

import { useEffect, useState } from "react";

import { Client, type IMessage, type StompSubscription } from "@stomp/stompjs";

import type { EventoTempoReal, MensagemResposta, RevogacaoTempoReal } from "./types";

export type EstadoConexao = "conectando" | "conectado" | "reconectando" | "desconectado";

/** Só o subconjunto de `@stomp/stompjs` Client que usamos — permite injetar um dublê em teste. */
export interface ClienteStompLike {
  activate(): void;
  deactivate(): unknown;
  subscribe(destino: string, callback: (mensagem: IMessage) => void): StompSubscription;
  onConnect?: () => void;
  onWebSocketClose?: () => void;
  onStompError?: () => void;
  connected: boolean;
}

export interface OpcoesConexaoTempoReal {
  brokerUrl: string;
  obterAccessToken: () => string | null;
  onEstadoMudou?: (estado: EstadoConexao) => void;
  /** A conversa aberta foi revogada (ex.: transferência) — quem chama decide o que mostrar. */
  onRevogacao?: (atendimentoId: string) => void;
  /** Injeção para teste — por padrão cria um Client real do @stomp/stompjs. */
  criarCliente?: (opcoes: { brokerUrl: string; accessToken: string | null }) => ClienteStompLike;
}

const DESTINO_REVOGACOES = "/user/queue/revogacoes";
const destinoAtendimento = (id: string) => `/user/queue/atendimento.${id}`;

function clienteStompPadrao(opcoes: {
  brokerUrl: string;
  accessToken: string | null;
}): ClienteStompLike {
  // Cast necessário: os handlers do Client real recebem um frame (IFrame/CloseEvent) que aqui
  // nunca usamos — a interface ClienteStompLike só declara a forma que de fato consumimos.
  return new Client({
    brokerURL: `${opcoes.brokerUrl}?access_token=${encodeURIComponent(opcoes.accessToken ?? "")}`,
    // Backoff é nosso, não o do stompjs (que é delay fixo) — ver calcularBackoffMs.
    reconnectDelay: 0,
  }) as unknown as ClienteStompLike;
}

/**
 * Backoff exponencial com teto: base 1s, fator 2, teto 30s. `comJitter: false` existe só para o
 * teste determinístico da progressão base/teto — em produção o jitter evita que várias abas
 * reconectem no mesmo instante exato (thundering herd contra o servidor).
 */
export function calcularBackoffMs(tentativa: number, comJitter = true): number {
  const BASE_MS = 1000;
  const FATOR = 2;
  const TETO_MS = 30000;
  const bruto = Math.min(BASE_MS * FATOR ** tentativa, TETO_MS);
  if (!comJitter) {
    return bruto;
  }
  return Math.round(bruto * (0.5 + Math.random() * 0.5));
}

/**
 * Funde o backfill HTTP (`GET /mensagens?desde=`) com o que já estava no cache, por `id` — uma
 * mensagem que chega tanto pelo backfill quanto por um evento WS não pode duplicar na tela, e uma
 * mudança de status (PENDENTE → ENVIADO) precisa substituir a entrada antiga, não somar outra.
 */
export function mesclarMensagens(
  existentes: MensagemResposta[],
  novas: MensagemResposta[],
): MensagemResposta[] {
  const porId = new Map(existentes.map((mensagem) => [mensagem.id, mensagem]));
  for (const mensagem of novas) {
    porId.set(mensagem.id, mensagem);
  }
  return Array.from(porId.values()).sort(
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
  private atendimentoAberto: string | null = null;
  private onEventoAtual: ((evento: EventoTempoReal) => void) | null = null;
  private tentativas = 0;
  private timerReconexao: ReturnType<typeof setTimeout> | null = null;
  private desativadoManualmente = false;

  constructor(private readonly opcoes: OpcoesConexaoTempoReal) {}

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
    this.assinaturaAtendimento = null;
    this.atendimentoAberto = null;
    this.onEventoAtual = null;
    this.cliente?.deactivate();
    this.cliente = null;
    this.opcoes.onEstadoMudou?.("desconectado");
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
    return this.cliente?.subscribe(destinoAtendimento(atendimentoId), (mensagem) => {
      onEvento(JSON.parse(mensagem.body) as EventoTempoReal);
    });
  }

  private abrirClienteEConectar(): void {
    const accessToken = this.opcoes.obterAccessToken();
    const cliente = (this.opcoes.criarCliente ?? clienteStompPadrao)({
      brokerUrl: this.opcoes.brokerUrl,
      accessToken,
    });
    this.cliente = cliente;

    cliente.onConnect = () => {
      this.tentativas = 0;
      cliente.subscribe(DESTINO_REVOGACOES, (mensagem) => {
        const revogacao = JSON.parse(mensagem.body) as RevogacaoTempoReal;
        if (revogacao.atendimentoId === this.atendimentoAberto) {
          this.fecharConversa();
          this.opcoes.onRevogacao?.(revogacao.atendimentoId);
        }
      });
      if (this.atendimentoAberto && this.onEventoAtual) {
        this.assinaturaAtendimento = this.assinar(this.atendimentoAberto, this.onEventoAtual) ?? null;
      }
      // Só depois de assinar: é este callback que o chamador usa como gatilho do backfill.
      this.opcoes.onEstadoMudou?.("conectado");
    };

    const agendarReconexao = () => {
      if (this.desativadoManualmente) {
        return;
      }
      this.opcoes.onEstadoMudou?.("reconectando");
      const atraso = calcularBackoffMs(this.tentativas);
      this.tentativas += 1;
      this.timerReconexao = setTimeout(() => this.abrirClienteEConectar(), atraso);
    };

    cliente.onWebSocketClose = agendarReconexao;
    cliente.onStompError = agendarReconexao;

    this.opcoes.onEstadoMudou?.("conectando");
    cliente.activate();
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
): { conexao: ConexaoTempoReal; estado: EstadoConexao } {
  const [estado, setEstado] = useState<EstadoConexao>("desconectado");
  const [conexao] = useState(
    () =>
      new ConexaoTempoReal({
        brokerUrl: process.env.NEXT_PUBLIC_WS_URL ?? "",
        obterAccessToken,
        onEstadoMudou: setEstado,
        onRevogacao,
      }),
  );

  useEffect(() => {
    conexao.conectar();
    return () => conexao.desconectar();
  }, [conexao]);

  return { conexao, estado };
}
