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
    tipo: "TEXTO",
    conteudo: "conteúdo",
    midiaUrl: null,
    midiaMetadados: null,
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

  it("trocar de conversa desassina a anterior antes de assinar a nova", () => {
    const { cliente } = clienteStompFalso();
    const unsubscribeConversa1 = vi.fn();
    (cliente.subscribe as ReturnType<typeof vi.fn>).mockImplementationOnce(() => ({
      id: "revogacoes",
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
