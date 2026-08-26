import { describe, expect, it, vi } from "vitest";

import { calcularBackoffMs, ConexaoTempoReal, mesclarMensagens, type ClienteStompLike } from "./tempo-real";
import type { MensagemResposta } from "./types";

describe("calcularBackoffMs", () => {
  it("cresce exponencialmente sem jitter (base 1s, fator 2)", () => {
    expect(calcularBackoffMs(0, false)).toBe(1000);
    expect(calcularBackoffMs(1, false)).toBe(2000);
    expect(calcularBackoffMs(2, false)).toBe(4000);
    expect(calcularBackoffMs(3, false)).toBe(8000);
    expect(calcularBackoffMs(4, false)).toBe(16000);
  });

  it("respeita o teto de 30s", () => {
    expect(calcularBackoffMs(10, false)).toBe(30000);
    expect(calcularBackoffMs(20, false)).toBe(30000);
  });

  it("com jitter, nunca ultrapassa o teto nem fica negativo", () => {
    for (let tentativa = 0; tentativa < 8; tentativa += 1) {
      const atraso = calcularBackoffMs(tentativa);
      expect(atraso).toBeGreaterThanOrEqual(0);
      expect(atraso).toBeLessThanOrEqual(30000);
    }
  });
});

function mensagem(
  id: string,
  enviadoEm: string,
  statusEntrega: MensagemResposta["statusEntrega"] = "ENVIADO",
): MensagemResposta {
  return {
    id,
    remetenteTipo: "ATENDENTE",
    remetenteId: null,
    remetenteNome: null,
    tipo: "TEXTO",
    conteudo: "conteúdo",
    midiaUrl: null,
    midiaMetadados: null,
    opcoes: null,
    statusEntrega,
    enviadoEm,
  };
}

describe("mesclarMensagens", () => {
  it("dedupe por id: a versão nova (ex.: mudança de status) substitui a antiga", () => {
    const existentes = [mensagem("1", "2026-01-01T00:00:00Z", "PENDENTE")];
    const novas = [mensagem("1", "2026-01-01T00:00:00Z", "ENVIADO")];

    const resultado = mesclarMensagens(existentes, novas);

    expect(resultado).toHaveLength(1);
    expect(resultado[0].statusEntrega).toBe("ENVIADO");
  });

  it("ordena o resultado por enviadoEm", () => {
    const resultado = mesclarMensagens(
      [mensagem("2", "2026-01-01T00:02:00Z")],
      [mensagem("1", "2026-01-01T00:01:00Z")],
    );

    expect(resultado.map((m) => m.id)).toEqual(["1", "2"]);
  });

  it("mantem uma única mensagem quando otimista, WebSocket e backfill trazem o mesmo id", () => {
    const otimista = mensagem("temp-1", "2026-01-01T00:00:00Z", "PENDENTE");
    const websocket = mensagem("real-1", "2026-01-01T00:00:01Z", "ENVIADO");
    websocket.remetenteId = "atendente-1";
    websocket.remetenteNome = "Ana Atendente";
    const backfill = { ...websocket, statusEntrega: "ENTREGUE" as const };

    const reconciliada = mesclarMensagens(
      [{ ...otimista, id: "real-1" }, websocket],
      [backfill, { ...backfill }],
    );

    expect(reconciliada).toHaveLength(1);
    expect(new Set(reconciliada.map((mensagem) => mensagem.id)).size).toBe(1);
    expect(reconciliada.find((mensagem) => mensagem.id === "real-1")).toMatchObject({
      statusEntrega: "ENTREGUE",
      remetenteId: "atendente-1",
      remetenteNome: "Ana Atendente",
    });
  });
});

function clienteStompFalso() {
  const chamadas: string[] = [];
  const cliente: ClienteStompLike = {
    connected: false,
    activate: vi.fn(() => {
      cliente.connected = true;
      cliente.onConnect?.();
    }),
    deactivate: vi.fn(),
    subscribe: vi.fn((destino: string) => {
      chamadas.push(`subscribe:${destino}`);
      return { id: destino, unsubscribe: vi.fn() };
    }),
  };
  return { cliente, chamadas };
}

describe("ConexaoTempoReal", () => {
  it("nao cria nem ativa cliente STOMP sem access token", () => {
    const { cliente } = clienteStompFalso();
    const criarCliente = vi.fn(() => cliente);
    const estados: string[] = [];
    const conexao = new ConexaoTempoReal({
      brokerUrl: "ws://test",
      obterAccessToken: () => null,
      onEstadoMudou: (estado) => estados.push(estado),
      criarCliente,
    });

    conexao.conectar();

    expect(criarCliente).not.toHaveBeenCalled();
    expect(cliente.activate).not.toHaveBeenCalled();
    expect(estados).toEqual(["desconectado"]);
  });

  it("assina a conversa e a fila de revogações ANTES de avisar 'conectado' — o gatilho do backfill", () => {
    const { cliente, chamadas } = clienteStompFalso();

    const conexao = new ConexaoTempoReal({
      brokerUrl: "ws://test",
      obterAccessToken: () => "token",
      onEstadoMudou: (estado) => {
        if (estado === "conectado") {
          chamadas.push("onEstadoMudou:conectado");
        }
      },
      criarCliente: () => cliente,
    });

    conexao.abrirConversa("atendimento-1", () => {});
    conexao.conectar();

    const indiceRevogacoes = chamadas.indexOf("subscribe:/user/queue/revogacoes");
    const indiceAtendimento = chamadas.indexOf("subscribe:/user/queue/atendimento.atendimento-1");
    const indiceConectado = chamadas.indexOf("onEstadoMudou:conectado");

    expect(indiceRevogacoes).toBeGreaterThanOrEqual(0);
    expect(indiceAtendimento).toBeGreaterThanOrEqual(0);
    expect(indiceConectado).toBeGreaterThan(indiceRevogacoes);
    expect(indiceConectado).toBeGreaterThan(indiceAtendimento);
  });

  it("assina a fila pessoal e encaminha avisos de transferência", () => {
    const { cliente, chamadas } = clienteStompFalso();
    const onNotificacao = vi.fn();
    const conexao = new ConexaoTempoReal({
      brokerUrl: "ws://test",
      obterAccessToken: () => "token",
      onNotificacao,
      criarCliente: () => cliente,
    });

    conexao.conectar();

    const callback = (cliente.subscribe as ReturnType<typeof vi.fn>).mock.calls[1]?.[1] as
      | ((mensagem: { body: string }) => void)
      | undefined;
    callback?.({
      body: JSON.stringify({
        tipo: "TRANSFERENCIA_RECEBIDA",
        dados: {
          atendimentoId: "a",
          leadId: "l",
          leadNome: "Lead",
          quemTransferiu: null,
          atorTipo: "AUTOMACAO",
          ocorridoEm: "2026-08-23T12:00:00Z",
        },
      }),
    });

    expect(chamadas).toContain("subscribe:/user/queue/notificacoes");
    expect(onNotificacao).toHaveBeenCalledOnce();
  });

  it("trocar de conversa desassina a anterior antes de assinar a nova", () => {
    const { cliente } = clienteStompFalso();
    const unsubscribeConversa1 = vi.fn();
    (cliente.subscribe as ReturnType<typeof vi.fn>).mockImplementationOnce(() => ({
      id: "revogacoes",
      unsubscribe: vi.fn(),
    }));
    (cliente.subscribe as ReturnType<typeof vi.fn>).mockImplementationOnce(() => ({
      id: "notificacoes",
      unsubscribe: vi.fn(),
    }));
    (cliente.subscribe as ReturnType<typeof vi.fn>).mockImplementationOnce(() => ({
      id: "atendimento-1",
      unsubscribe: unsubscribeConversa1,
    }));

    const conexao = new ConexaoTempoReal({
      brokerUrl: "ws://test",
      obterAccessToken: () => "token",
      criarCliente: () => cliente,
    });

    conexao.abrirConversa("atendimento-1", () => {});
    conexao.conectar();
    conexao.abrirConversa("atendimento-2", () => {});

    expect(unsubscribeConversa1).toHaveBeenCalledTimes(1);
  });
});
