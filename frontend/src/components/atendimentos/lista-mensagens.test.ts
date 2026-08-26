import { afterEach, describe, expect, it, vi } from "vitest";

import type { MensagemResposta } from "@/lib/atendimento/types";

import { chaveDaMensagem, nomeDaAutoria, rotuloDaData } from "./lista-mensagens";

function mensagem(parcial: Partial<MensagemResposta>): MensagemResposta {
  return {
    id: "mensagem-1",
    remetenteTipo: "ATENDENTE",
    remetenteId: "bruno-id",
    remetenteNome: "Bruno Atendente",
    tipo: "TEXTO",
    conteudo: "Olá",
    midiaUrl: null,
    midiaMetadados: null,
    opcoes: null,
    statusEntrega: "ENTREGUE",
    enviadoEm: "2026-08-16T12:00:00Z",
    ...parcial,
  };
}

describe("rotuloDaData", () => {
  afterEach(() => vi.useRealTimers());

  it("usa Hoje e Ontem no calendário local em pt-BR", () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date(2026, 7, 16, 12));

    expect(
      rotuloDaData(new Date(2026, 7, 16, 8).toISOString(), "Hoje", "Ontem"),
    ).toBe("Hoje");
    expect(
      rotuloDaData(new Date(2026, 7, 15, 23).toISOString(), "Hoje", "Ontem"),
    ).toBe("Ontem");
    expect(
      rotuloDaData(new Date(2026, 6, 10, 8).toISOString(), "Hoje", "Ontem"),
    ).toContain("julho");
  });
});

describe("chaveDaMensagem", () => {
  it("mantem a altura associada ao id quando uma pagina anterior entra no topo", () => {
    const paginaInicial = [mensagem({ id: "nova" }), mensagem({ id: "mais-nova" })];
    const depoisDoBackfill = [mensagem({ id: "antiga" }), ...paginaInicial];

    expect(chaveDaMensagem(paginaInicial, 0)).toBe("nova");
    expect(chaveDaMensagem(depoisDoBackfill, 1)).toBe("nova");
    expect(chaveDaMensagem(depoisDoBackfill, 2)).toBe("mais-nova");
  });
});

describe("nomeDaAutoria", () => {
  it("preserva o autor historico mesmo quando o responsavel atual e outra pessoa", () => {
    expect(
      nomeDaAutoria(mensagem({}), "ana-id", "Ana Atendente"),
    ).toBe("Bruno Atendente");
  });

  it("usa o responsavel atual apenas durante o evento em tempo real ainda nao enriquecido", () => {
    expect(
      nomeDaAutoria(
        mensagem({ remetenteId: "ana-id", remetenteNome: null }),
        "ana-id",
        "Ana Atendente",
      ),
    ).toBe("Ana Atendente");
  });

  it("rotula mensagens da automacao como IA", () => {
    expect(
      nomeDaAutoria(
        mensagem({ remetenteTipo: "IA", remetenteId: null, remetenteNome: null }),
        null,
        null,
        "IA",
      ),
    ).toBe("IA");
  });

  it("nao inventa autoria para lead, sistema ou tipo desconhecido", () => {
    expect(nomeDaAutoria(mensagem({ remetenteTipo: "LEAD" }), null, null, "IA")).toBeNull();
    expect(nomeDaAutoria(mensagem({ remetenteTipo: "SISTEMA" }), null, null, "IA")).toBeNull();
    expect(
      nomeDaAutoria(
        mensagem({ remetenteTipo: "DESCONHECIDO" as MensagemResposta["remetenteTipo"] }),
        null,
        null,
        "IA",
      ),
    ).toBeNull();
  });
});
