import { describe, expect, it } from "vitest";

import type { FiltroDeFinalizacao } from "./types";
import { filtroNormalizado, novaChaveDeIdempotencia, problemaDoFiltro } from "./validacao";

const base: FiltroDeFinalizacao = {
  atendenteIds: ["b", "a"],
  de: "2026-09-01",
  ate: "2026-09-02",
  horaInicio: null,
  horaFim: null,
};

describe("problemaDoFiltro", () => {
  it("aceita atendentes, datas na ordem e horários opcionais", () => {
    expect(problemaDoFiltro(base)).toBeNull();
  });

  it("recusa sem atendente", () => {
    expect(problemaDoFiltro({ ...base, atendenteIds: [] })).toBe("SEM_ATENDENTE");
  });

  it("recusa data inicial ou final ausente", () => {
    expect(problemaDoFiltro({ ...base, de: "" })).toBe("SEM_PERIODO");
    expect(problemaDoFiltro({ ...base, ate: "" })).toBe("SEM_PERIODO");
  });

  it("recusa início depois do fim", () => {
    expect(problemaDoFiltro({ ...base, de: "2026-09-03", ate: "2026-09-02" })).toBe("PERIODO_INVERTIDO");
  });

  it("no mesmo dia, hora final antes da inicial é inválida; igual é válida", () => {
    const mesmoDia = { ...base, ate: base.de };
    expect(problemaDoFiltro({ ...mesmoDia, horaInicio: "18:00", horaFim: "09:00" })).toBe("PERIODO_INVERTIDO");
    expect(problemaDoFiltro({ ...mesmoDia, horaInicio: "09:00", horaFim: "09:00" })).toBeNull();
  });

  it("em dias diferentes a hora final menor que a inicial é válida (a janela vira a meia-noite)", () => {
    expect(problemaDoFiltro({ ...base, horaInicio: "18:00", horaFim: "09:00" })).toBeNull();
  });

  it("só uma das horas informada não invalida", () => {
    expect(problemaDoFiltro({ ...base, ate: base.de, horaInicio: "10:00" })).toBeNull();
    expect(problemaDoFiltro({ ...base, ate: base.de, horaFim: "10:00" })).toBeNull();
  });
});

describe("filtroNormalizado", () => {
  it("ordena os atendentes sem mudar o original e troca hora vazia por null", () => {
    const original = { ...base, horaInicio: "", horaFim: "" };

    const normalizado = filtroNormalizado(original);

    expect(normalizado.atendenteIds).toEqual(["a", "b"]);
    expect(original.atendenteIds).toEqual(["b", "a"]);
    expect(normalizado.horaInicio).toBeNull();
    expect(normalizado.horaFim).toBeNull();
  });

  it("preserva 00:00 como horário informado", () => {
    expect(filtroNormalizado({ ...base, horaInicio: "00:00" }).horaInicio).toBe("00:00");
  });
});

describe("novaChaveDeIdempotencia", () => {
  it("gera chaves distintas no formato que o servidor aceita", () => {
    const a = novaChaveDeIdempotencia();
    const b = novaChaveDeIdempotencia();

    expect(a).not.toBe(b);
    expect(a).toMatch(/^[A-Za-z0-9._:-]{8,80}$/);
  });
});
