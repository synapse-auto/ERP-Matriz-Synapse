import { afterEach, describe, expect, it, vi } from "vitest";

import type { MensagemResposta } from "@/lib/atendimento/types";

import { nomeDaAutoria, rotuloDaData } from "./lista-mensagens";

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
});
