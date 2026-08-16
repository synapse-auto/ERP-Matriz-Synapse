import { afterEach, describe, expect, it, vi } from "vitest";

import { rotuloDaData } from "./lista-mensagens";

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
