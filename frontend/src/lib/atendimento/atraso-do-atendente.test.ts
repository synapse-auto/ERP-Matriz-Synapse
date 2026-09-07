import { describe, expect, it } from "vitest";

import type { RemetenteTipo, StatusAtendimento } from "./types";
import { atendenteEstaAtrasado } from "./atraso-do-atendente";

const AGORA = new Date("2026-08-16T12:30:00Z");

function mensagemHaMinutos(minutos: number): string {
  return new Date(AGORA.getTime() - minutos * 60 * 1000).toISOString();
}

describe("atendenteEstaAtrasado", () => {
  it.each([
    [19, false],
    [20, true],
    [21, true],
  ])("considera %i minutos como %s", (minutos, esperado) => {
    expect(
      atendenteEstaAtrasado("LEAD", mensagemHaMinutos(minutos), "EM_ATENDIMENTO", AGORA),
    ).toBe(esperado);
  });

  it.each(["ATENDENTE", "IA", "SISTEMA", null] as (RemetenteTipo | null)[])(
    "ignora quando a última mensagem veio de %s",
    (remetente) => {
      expect(
        atendenteEstaAtrasado(remetente, mensagemHaMinutos(60), "EM_ATENDIMENTO", AGORA),
      ).toBe(false);
    },
  );

  it.each(["EM_IA", "FINALIZADO"] as StatusAtendimento[])(
    "ignora o atraso quando o status é %s",
    (status) => {
      expect(atendenteEstaAtrasado("LEAD", mensagemHaMinutos(60), status, AGORA)).toBe(false);
    },
  );

  it("ignora timestamp ausente, inválido ou futuro", () => {
    expect(atendenteEstaAtrasado("LEAD", null, "EM_ATENDIMENTO", AGORA)).toBe(false);
    expect(atendenteEstaAtrasado("LEAD", "não é data", "EM_ATENDIMENTO", AGORA)).toBe(false);
    expect(
      atendenteEstaAtrasado("LEAD", mensagemHaMinutos(-1), "EM_ATENDIMENTO", AGORA),
    ).toBe(false);
  });
});
