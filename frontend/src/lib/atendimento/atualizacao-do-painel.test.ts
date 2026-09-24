import { QueryClient, QueryObserver } from "@tanstack/react-query";
import { afterEach, describe, expect, it } from "vitest";

import {
  AgendadorDeAtualizacaoDoPainel,
  JANELA_DE_COALESCENCIA_MS,
  pedidoDaNotificacao,
  type Relogio,
} from "./atualizacao-do-painel";
import type { NotificacaoTempoReal } from "./types";

class RelogioManual implements Relogio {
  private instante = 1_000_000;
  private readonly tarefas = new Map<number, { quando: number; acao: () => void }>();
  private proximoId = 1;

  agora() {
    return this.instante;
  }

  agendar(acao: () => void, atrasoMs: number) {
    const id = this.proximoId++;
    this.tarefas.set(id, { quando: this.instante + atrasoMs, acao });
    return id;
  }

  cancelar(agendamento: unknown) {
    this.tarefas.delete(agendamento as number);
  }

  avancar(ms: number) {
    const alvo = this.instante + ms;
    for (;;) {
      const vencida = [...this.tarefas.entries()]
        .filter(([, tarefa]) => tarefa.quando <= alvo)
        .sort((a, b) => a[1].quando - b[1].quando)[0];
      if (!vencida) break;
      this.tarefas.delete(vencida[0]);
      this.instante = vencida[1].quando;
      vencida[1].acao();
    }
    this.instante = alvo;
  }
}

interface Painel {
  cache: QueryClient;
  chamadas: Record<string, number>;
  encerrar: () => void;
}

/** QueryClient real com as consultas que a tela mantém ativas: contagem, inbox e dois estados. */
async function montarPainel(): Promise<Painel> {
  const cache = new QueryClient({ defaultOptions: { queries: { retry: false, staleTime: 30_000 } } });
  const chamadas: Record<string, number> = {};
  const chaves = [
    ["atendimentos", "contagem"],
    ["atendimentos", "inbox", "TODOS"],
    ["atendimentos", "estado", "aberto"],
    ["atendimentos", "estado", "outro"],
  ];
  const cancelamentos = chaves.map((queryKey) => {
    const nome = queryKey.join("/");
    chamadas[nome] = 0;
    const observador = new QueryObserver(cache, {
      queryKey,
      queryFn: () => {
        chamadas[nome] += 1;
        return Promise.resolve(nome);
      },
    });
    return observador.subscribe(() => undefined);
  });
  await new Promise((resolver) => setTimeout(resolver, 0));
  for (const nome of Object.keys(chamadas)) chamadas[nome] = 0;
  return { cache, chamadas, encerrar: () => cancelamentos.forEach((cancelar) => cancelar()) };
}

function novaMensagem(numero: number, atendimentoId = `atendimento-${numero}`): NotificacaoTempoReal {
  return {
    tipo: "NOVA_MENSAGEM",
    dados: {
      atendimentoId,
      leadId: `lead-${numero}`,
      leadNome: "Lead",
      mensagemId: `mensagem-${numero}`,
      remetenteTipo: "LEAD",
      remetenteId: null,
      tipo: "TEXTO",
      conteudo: "oi",
      midiaMetadados: null,
      enviadoEm: "2026-09-24T12:00:00Z",
    },
  } as NotificacaoTempoReal;
}

function transferencia(atendimentoId: string): NotificacaoTempoReal {
  return {
    tipo: "TRANSFERENCIA_RECEBIDA",
    dados: {
      atendimentoId,
      leadId: "lead-t",
      leadNome: "Lead",
      deAtendenteId: "a",
      paraAtendenteId: "b",
      quemTransferiu: "a",
      atorTipo: "USUARIO",
      ocorridoEm: "2026-09-24T12:00:00Z",
    },
  } as NotificacaoTempoReal;
}

const paineis: Painel[] = [];
async function painel() {
  const novo = await montarPainel();
  paineis.push(novo);
  return novo;
}

afterEach(() => {
  paineis.splice(0).forEach((aberto) => aberto.encerrar());
});

describe("AgendadorDeAtualizacaoDoPainel", () => {
  it("executa o primeiro pedido na hora: mensagem isolada continua atualizando a lista imediatamente", async () => {
    const { cache, chamadas } = await painel();
    const agendador = new AgendadorDeAtualizacaoDoPainel(cache, new RelogioManual());

    agendador.solicitar(pedidoDaNotificacao(novaMensagem(1)));

    expect(chamadas["atendimentos/contagem"]).toBe(1);
    expect(chamadas["atendimentos/inbox/TODOS"]).toBe(1);
  });

  it("junta uma rajada de 50 eventos em um refetch imediato e um no fim da janela", async () => {
    const { cache, chamadas } = await painel();
    const relogio = new RelogioManual();
    const agendador = new AgendadorDeAtualizacaoDoPainel(cache, relogio);

    for (let numero = 0; numero < 50; numero += 1) {
      agendador.solicitar(pedidoDaNotificacao(novaMensagem(numero)));
      relogio.avancar(20);
    }
    relogio.avancar(JANELA_DE_COALESCENCIA_MS);

    expect(chamadas["atendimentos/contagem"]).toBe(2);
    expect(chamadas["atendimentos/inbox/TODOS"]).toBe(2);
  });

  it("o mesmo evento recebido pelo ouvinte global e pela tela gera um pedido só", async () => {
    const { cache, chamadas } = await painel();
    const relogio = new RelogioManual();
    const agendador = new AgendadorDeAtualizacaoDoPainel(cache, relogio);
    const evento = novaMensagem(7);

    agendador.solicitar(pedidoDaNotificacao(evento));
    agendador.solicitar(pedidoDaNotificacao(evento));
    relogio.avancar(JANELA_DE_COALESCENCIA_MS * 2);

    expect(chamadas["atendimentos/contagem"]).toBe(1);
  });

  it("transferência não espera a janela, mesmo no meio de uma rajada, e cobre o que estava pendente", async () => {
    const { cache, chamadas } = await painel();
    const relogio = new RelogioManual();
    const agendador = new AgendadorDeAtualizacaoDoPainel(cache, relogio);

    agendador.solicitar(pedidoDaNotificacao(novaMensagem(1)));
    relogio.avancar(100);
    agendador.solicitar(pedidoDaNotificacao(novaMensagem(2)));
    agendador.solicitar(pedidoDaNotificacao(transferencia("outro")));

    expect(chamadas["atendimentos/contagem"]).toBe(2);
    expect(chamadas["atendimentos/estado/outro"]).toBe(1);
    relogio.avancar(JANELA_DE_COALESCENCIA_MS * 2);
    expect(chamadas["atendimentos/contagem"]).toBe(2);
  });

  it("revogação urgente relê todos os estados abertos na hora", async () => {
    const { cache, chamadas } = await painel();
    const relogio = new RelogioManual();
    const agendador = new AgendadorDeAtualizacaoDoPainel(cache, relogio);
    agendador.solicitar({});
    expect(chamadas["atendimentos/estado/aberto"]).toBe(0);

    agendador.solicitar({ atendimentoId: "aberto", urgente: true });

    expect(chamadas["atendimentos/estado/aberto"]).toBe(1);
    expect(chamadas["atendimentos/estado/outro"]).toBe(1);
  });

  it("mensagem de outro lead não relê o estado da conversa aberta; a do próprio atendimento relê", async () => {
    const { cache, chamadas } = await painel();
    const relogio = new RelogioManual();
    const agendador = new AgendadorDeAtualizacaoDoPainel(cache, relogio);

    agendador.solicitar(pedidoDaNotificacao(novaMensagem(1, "outro")));
    expect(chamadas["atendimentos/estado/aberto"]).toBe(0);
    expect(chamadas["atendimentos/estado/outro"]).toBe(1);

    relogio.avancar(JANELA_DE_COALESCENCIA_MS);
    agendador.solicitar(pedidoDaNotificacao(novaMensagem(2, "aberto")));
    expect(chamadas["atendimentos/estado/aberto"]).toBe(1);
  });

  it("cada aba (QueryClient) tem o próprio limite: duas abas fazem no máximo dois refetches por janela cada", async () => {
    const abas = await Promise.all([painel(), painel()]);
    const relogio = new RelogioManual();
    const agendadores = abas.map((aba) => new AgendadorDeAtualizacaoDoPainel(aba.cache, relogio));

    for (let numero = 0; numero < 30; numero += 1) {
      agendadores.forEach((agendador) => agendador.solicitar(pedidoDaNotificacao(novaMensagem(numero))));
      relogio.avancar(10);
    }
    relogio.avancar(JANELA_DE_COALESCENCIA_MS);

    abas.forEach((aba) => expect(aba.chamadas["atendimentos/contagem"]).toBe(2));
  });

  it("antes x depois: 50 eventos em 1 s, dois ouvintes por aba", async () => {
    const antes = await painel();
    for (let numero = 0; numero < 50; numero += 1) {
      // Comportamento anterior: cada ouvinte invalidava ["atendimentos"] por conta própria.
      void antes.cache.invalidateQueries({ queryKey: ["atendimentos"] });
      void antes.cache.invalidateQueries({ queryKey: ["atendimentos"] });
    }

    const depois = await painel();
    const relogio = new RelogioManual();
    const agendador = new AgendadorDeAtualizacaoDoPainel(depois.cache, relogio);
    for (let numero = 0; numero < 50; numero += 1) {
      const evento = novaMensagem(numero);
      agendador.solicitar(pedidoDaNotificacao(evento));
      agendador.solicitar(pedidoDaNotificacao(evento));
      relogio.avancar(20);
    }
    relogio.avancar(JANELA_DE_COALESCENCIA_MS);

    // Cada invalidate cancela o GET em voo e abre outro: o navegador descarta a resposta, mas o
    // servidor executa todas as consultas.
    expect(antes.chamadas["atendimentos/contagem"]).toBe(100);
    expect(antes.chamadas["atendimentos/estado/aberto"]).toBe(100);
    expect(depois.chamadas["atendimentos/contagem"]).toBe(2);
    expect(depois.chamadas["atendimentos/estado/aberto"]).toBe(0);
  });
});
