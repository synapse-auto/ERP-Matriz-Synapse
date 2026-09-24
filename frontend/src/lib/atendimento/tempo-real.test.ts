import { describe, expect, it, vi } from "vitest";

import {
  CABECALHOS_DE_BACKOFF_DE_RECONEXAO,
  calcularBackoffMs,
  configuracaoDeBackoffDoServidor,
  ConexaoTempoReal,
  mesclarMensagens,
  type ClienteStompLike,
} from "./tempo-real";
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

  it("varia o atraso entre clientes sem perder o teto configurado", () => {
    const configuracao = { atrasoInicialMs: 1_000, fator: 2, atrasoMaximoMs: 30_000 };
    vi.spyOn(Math, "random").mockReturnValueOnce(0).mockReturnValueOnce(0.99);

    const menor = calcularBackoffMs(0, true, configuracao);
    const maior = calcularBackoffMs(0, true, configuracao);

    expect(menor).toBe(500);
    expect(maior).toBe(995);
    expect(maior).toBeLessThanOrEqual(configuracao.atrasoMaximoMs);
  });

  it("aceita o perfil anunciado pelo backend e rejeita valores inválidos", () => {
    expect(configuracaoDeBackoffDoServidor({
      [CABECALHOS_DE_BACKOFF_DE_RECONEXAO.atrasoInicialMs]: "1500",
      [CABECALHOS_DE_BACKOFF_DE_RECONEXAO.fator]: "2.5",
      [CABECALHOS_DE_BACKOFF_DE_RECONEXAO.atrasoMaximoMs]: "20000",
    })).toEqual({ atrasoInicialMs: 1500, fator: 2.5, atrasoMaximoMs: 20000 });

    expect(configuracaoDeBackoffDoServidor({
      [CABECALHOS_DE_BACKOFF_DE_RECONEXAO.atrasoInicialMs]: "0",
      [CABECALHOS_DE_BACKOFF_DE_RECONEXAO.fator]: "1",
      [CABECALHOS_DE_BACKOFF_DE_RECONEXAO.atrasoMaximoMs]: "-1",
    })).toEqual({ atrasoInicialMs: 1000, fator: 2, atrasoMaximoMs: 30000 });
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
    erroEntrega: null,
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

  it("une as entradas quando o WebSocket conecta id do servidor e chave idempotente", () => {
    const otimista = { ...mensagem("temp-1", "2026-01-01T00:00:00Z", "PENDENTE"), idempotencyKey: "clique-1" };
    const historicoSemChave = mensagem("real-1", "2026-01-01T00:00:01Z", "ENVIADO");
    const websocket = {
      ...mensagem("real-1", "2026-01-01T00:00:01Z", "ENVIADO"),
      idempotencyKey: "clique-1",
    };

    const resultado = mesclarMensagens([otimista, historicoSemChave], [websocket]);

    expect(resultado).toHaveLength(1);
    expect(resultado[0]).toMatchObject({ id: "real-1", idempotencyKey: "clique-1" });
  });

  it("nao deixa a resposta HTTP pendente rebaixar o status entregue pelo WebSocket", () => {
    const websocket = mensagem("real-1", "2026-01-01T00:00:01Z", "ENTREGUE");
    const respostaHttp = mensagem("real-1", "2026-01-01T00:00:01Z", "PENDENTE");

    const resultado = mesclarMensagens([websocket], [respostaHttp]);

    expect(resultado).toHaveLength(1);
    expect(resultado[0].statusEntrega).toBe("ENTREGUE");
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
  it("reinicia o backoff após uma reconexão bem-sucedida", () => {
    vi.useFakeTimers();
    vi.spyOn(Math, "random").mockReturnValue(0);
    const primeiro = clienteStompFalso().cliente;
    const segundo = clienteStompFalso().cliente;
    const criarCliente = vi.fn().mockReturnValueOnce(primeiro).mockReturnValueOnce(segundo);
    const agendamentos = vi.spyOn(globalThis, "setTimeout");
    const conexao = new ConexaoTempoReal({
      brokerUrl: "ws://test",
      obterAccessToken: () => "token",
      criarCliente,
    });

    conexao.conectar();
    primeiro.onWebSocketClose?.();
    expect(agendamentos).toHaveBeenLastCalledWith(expect.any(Function), 500);

    vi.advanceTimersByTime(500);
    segundo.onWebSocketClose?.();
    expect(agendamentos).toHaveBeenLastCalledWith(expect.any(Function), 500);

    vi.useRealTimers();
  });

  it("usa o perfil anunciado no CONNECTED para a próxima reconexão", () => {
    vi.useFakeTimers();
    vi.spyOn(Math, "random").mockReturnValue(0);
    const cliente = clienteStompFalso().cliente;
    const agendamentos = vi.spyOn(globalThis, "setTimeout");
    const conexao = new ConexaoTempoReal({
      brokerUrl: "ws://test",
      obterAccessToken: () => "token",
      criarCliente: () => cliente,
    });

    conexao.conectar();
    cliente.onConnect?.({
      headers: {
        [CABECALHOS_DE_BACKOFF_DE_RECONEXAO.atrasoInicialMs]: "2000",
        [CABECALHOS_DE_BACKOFF_DE_RECONEXAO.fator]: "2",
        [CABECALHOS_DE_BACKOFF_DE_RECONEXAO.atrasoMaximoMs]: "10000",
      },
    });
    cliente.onWebSocketClose?.();

    expect(agendamentos).toHaveBeenLastCalledWith(expect.any(Function), 1000);
    vi.useRealTimers();
  });

  it("E208: reconectar (renovação do token) desativa o cliente anterior em vez de deixá-lo vivo", () => {
    const primeiro = clienteStompFalso().cliente;
    const segundo = clienteStompFalso().cliente;
    const criarCliente = vi.fn().mockReturnValueOnce(primeiro).mockReturnValueOnce(segundo);
    const conexao = new ConexaoTempoReal({
      brokerUrl: "ws://test",
      obterAccessToken: () => "token",
      criarCliente,
    });

    conexao.conectar();
    conexao.conectar();

    expect(criarCliente).toHaveBeenCalledTimes(2);
    expect(primeiro.deactivate).toHaveBeenCalledTimes(1);
    expect(segundo.deactivate).not.toHaveBeenCalled();
  });

  it("E208: fechamento tardio de um cliente substituído não abre outro socket", () => {
    vi.useFakeTimers();
    const criados: ClienteStompLike[] = [];
    const conexao = new ConexaoTempoReal({
      brokerUrl: "ws://test",
      obterAccessToken: () => "token",
      criarCliente: () => {
        const { cliente } = clienteStompFalso();
        criados.push(cliente);
        return cliente;
      },
    });

    conexao.conectar();
    const fecharAntigo = criados[0].onWebSocketClose;
    const erroAntigo = criados[0].onStompError;
    conexao.conectar(); // renovação do token
    fecharAntigo?.();
    erroAntigo?.();
    vi.advanceTimersByTime(60_000);

    expect(criados).toHaveLength(2);
    expect(criados[1].deactivate).not.toHaveBeenCalled();
    vi.useRealTimers();
  });

  it("E208: várias renovações de token com quedas de rede mantêm um único cliente ativo", () => {
    vi.useFakeTimers();
    const criados: ClienteStompLike[] = [];
    const conexao = new ConexaoTempoReal({
      brokerUrl: "ws://test",
      obterAccessToken: () => "token",
      criarCliente: () => {
        const { cliente } = clienteStompFalso();
        criados.push(cliente);
        return cliente;
      },
    });

    for (let i = 0; i < 10; i += 1) {
      conexao.conectar();
      criados[criados.length - 1].onWebSocketClose?.();
      vi.advanceTimersByTime(60_000);
    }

    const ativos = criados.filter((cliente) => vi.mocked(cliente.deactivate).mock.calls.length === 0);
    expect(ativos).toHaveLength(1);
    vi.useRealTimers();
  });

  it("E208: erro STOMP seguido de close do mesmo cliente agenda uma única reconexão", () => {
    vi.useFakeTimers();
    const criados: ClienteStompLike[] = [];
    const conexao = new ConexaoTempoReal({
      brokerUrl: "ws://test",
      obterAccessToken: () => "token",
      criarCliente: () => {
        const { cliente } = clienteStompFalso();
        criados.push(cliente);
        return cliente;
      },
    });

    conexao.conectar();
    criados[0].onStompError?.();
    criados[0].onWebSocketClose?.();
    vi.advanceTimersByTime(60_000);

    expect(criados).toHaveLength(2);
    vi.useRealTimers();
  });

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

  it("entrega o estado atual ao ouvinte que chega depois de a conexão compartilhada já estar de pé", () => {
    // Produção: NotificacoesTempoReal (layout raiz) conecta antes de a tela de Atendimentos montar.
    const { cliente } = clienteStompFalso();
    const conexao = new ConexaoTempoReal({
      brokerUrl: "ws://test",
      obterAccessToken: () => "token",
      criarCliente: () => cliente,
    });
    conexao.conectar();

    const estadosDoOuvinteTardio: string[] = [];
    conexao.adicionarOuvinteDeEstado((estado) => estadosDoOuvinteTardio.push(estado));

    expect(estadosDoOuvinteTardio).toEqual(["conectado"]);
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

  it("encaminha convites para atendimento pela fila pessoal", () => {
    const { cliente } = clienteStompFalso();
    const onNotificacao = vi.fn();
    const ouvinte = vi.fn();
    const conexao = new ConexaoTempoReal({
      brokerUrl: "ws://test",
      obterAccessToken: () => "token",
      onNotificacao,
      criarCliente: () => cliente,
    });
    conexao.adicionarOuvinteDeNotificacao(ouvinte);
    conexao.conectar();

    const callback = (cliente.subscribe as ReturnType<typeof vi.fn>).mock.calls[1]?.[1] as
      | ((mensagem: { body: string }) => void)
      | undefined;
    callback?.({
      body: JSON.stringify({
        tipo: "CONVITE_ATENDIMENTO",
        eventoId: "convite-1",
        dados: {
          atendimentoId: "atendimento-1",
          leadId: "lead-1",
          convidadorId: "atendente-1",
          ocorridoEm: "2026-09-20T12:00:00Z",
        },
      }),
    });

    expect(onNotificacao).toHaveBeenCalledWith(expect.objectContaining({ tipo: "CONVITE_ATENDIMENTO" }));
    expect(ouvinte).toHaveBeenCalledWith(expect.objectContaining({ tipo: "CONVITE_ATENDIMENTO" }));
  });

  it("encaminha nova mensagem para todos os ouvintes da fila pessoal", () => {
    const { cliente } = clienteStompFalso();
    const ouvinte = vi.fn();
    const conexao = new ConexaoTempoReal({
      brokerUrl: "ws://test",
      obterAccessToken: () => "token",
      criarCliente: () => cliente,
    });
    conexao.adicionarOuvinteDeNotificacao(ouvinte);
    conexao.conectar();

    const callback = (cliente.subscribe as ReturnType<typeof vi.fn>).mock.calls[1]?.[1] as
      | ((mensagem: { body: string }) => void)
      | undefined;
    callback?.({
      body: JSON.stringify({
        tipo: "NOVA_MENSAGEM",
        eventoId: "evento-1",
        dados: { atendimentoId: "a", leadId: "l", mensagemId: "m", leadNome: "Lead" },
      }),
    });

    expect(ouvinte).toHaveBeenCalledWith(expect.objectContaining({ tipo: "NOVA_MENSAGEM", eventoId: "evento-1" }));
  });

  it("encaminha o evento canônico versionado pela fila pessoal", () => {
    const { cliente } = clienteStompFalso();
    const ouvinte = vi.fn();
    const conexao = new ConexaoTempoReal({
      brokerUrl: "ws://test",
      obterAccessToken: () => "token",
      criarCliente: () => cliente,
    });
    conexao.adicionarOuvinteDeNotificacao(ouvinte);
    conexao.conectar();

    const callback = (cliente.subscribe as ReturnType<typeof vi.fn>).mock.calls[1]?.[1] as
      | ((mensagem: { body: string }) => void)
      | undefined;
    callback?.({
      body: JSON.stringify({
        tipo: "ATENDIMENTO_ESTADO",
        contrato: "atendimento.estado.v1",
        eventoId: "evento-estado-1",
        versaoContrato: 1,
        dados: {
          atendimentoId: "atendimento-1",
          leadId: "lead-1",
          eventoTipo: "ATENDIMENTO_FINALIZADO",
          versao: 8,
          ocorridoEm: "2026-09-12T10:00:00Z",
        },
      }),
    });

    expect(ouvinte).toHaveBeenCalledWith(expect.objectContaining({
      tipo: "ATENDIMENTO_ESTADO",
      eventoId: "evento-estado-1",
    }));
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

  it("ignora frame que chegou atrasado da assinatura da conversa anterior", () => {
    const { cliente } = clienteStompFalso();
    const callbackDaConversa1 = vi.fn();
    const callbackDaConversa2 = vi.fn();
    const conexao = new ConexaoTempoReal({
      brokerUrl: "ws://test",
      obterAccessToken: () => "token",
      criarCliente: () => cliente,
    });

    conexao.abrirConversa("atendimento-1", callbackDaConversa1);
    conexao.conectar();
    const callbackAntigo = (cliente.subscribe as ReturnType<typeof vi.fn>).mock.calls[2]?.[1] as
      | ((mensagem: { body: string }) => void)
      | undefined;
    conexao.abrirConversa("atendimento-2", callbackDaConversa2);

    callbackAntigo?.({
      body: JSON.stringify({
        tipo: "TRANSFERENCIA",
        dados: {
          atendimentoId: "atendimento-1",
          leadId: "lead-1",
          paraAtendenteId: "bruno",
          ocorridoEm: "2026-09-11T20:00:00Z",
        },
      }),
    });

    expect(callbackDaConversa1).not.toHaveBeenCalled();
    expect(callbackDaConversa2).not.toHaveBeenCalled();
  });
});
