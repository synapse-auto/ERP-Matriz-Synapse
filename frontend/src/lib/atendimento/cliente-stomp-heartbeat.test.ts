import { describe, expect, it, vi } from "vitest";

const construcoes = vi.hoisted(() => [] as Record<string, unknown>[]);

vi.mock("@stomp/stompjs", () => ({
  Client: class {
    constructor(configuracao: Record<string, unknown>) {
      construcoes.push(configuracao);
    }
  },
}));

import { clienteStompPadrao, HEARTBEAT_MS } from "./tempo-real";

/**
 * E193: sem heartbeat, os dois lados negociam `0,0` e uma conexão morta em silêncio só é percebida
 * quando o TCP do navegador desiste — foi o que deixou a mensagem recebida sem aparecer por cerca
 * de um minuto. O teste olha a configuração entregue ao Client, sem rede e sem esperar tempo real.
 */
describe("clienteStompPadrao", () => {
  it("configura o pulso nos dois sentidos com o valor combinado com o backend", () => {
    construcoes.length = 0;

    clienteStompPadrao({ brokerUrl: "ws://localhost:8080/ws", accessToken: "token-123" });

    expect(construcoes).toHaveLength(1);
    expect(construcoes[0]).toMatchObject({
      heartbeatIncoming: HEARTBEAT_MS,
      heartbeatOutgoing: HEARTBEAT_MS,
    });
    expect(HEARTBEAT_MS).toBe(10_000);
  });

  it("mantém o backoff próprio e o token na URL do broker", () => {
    construcoes.length = 0;

    clienteStompPadrao({ brokerUrl: "ws://localhost:8080/ws", accessToken: "tok en/+1" });

    // reconnectDelay: 0 é deliberado — quem reconecta é calcularBackoffMs, não o stompjs.
    expect(construcoes[0].reconnectDelay).toBe(0);
    expect(construcoes[0].brokerURL).toBe(
      "ws://localhost:8080/ws?access_token=tok%20en%2F%2B1",
    );
  });
});
