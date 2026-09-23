import { describe, expect, it } from "vitest";

import { faixaDaHora, resumoDoHorario } from "./horario-de-pico";

function porHora(valores: Record<number, number>): Map<number, number> {
  return new Map(Object.entries(valores).map(([hora, quantidade]) => [Number(hora), quantidade]));
}

describe("faixaDaHora", () => {
  it("separa pico, intermediaria e baixa pela fracao do maximo", () => {
    expect(faixaDaHora(70, 100)).toBe("pico");
    expect(faixaDaHora(60, 100)).toBe("pico");
    expect(faixaDaHora(40, 100)).toBe("intermediaria");
    expect(faixaDaHora(10, 100)).toBe("baixa");
  });

  it("sem movimento nenhum, nada e pico", () => {
    expect(faixaDaHora(0, 0)).toBe("baixa");
  });
});

describe("resumoDoHorario", () => {
  it("acha o pico de cada turno e o vale entre eles, estendido a vizinha no mesmo nivel", () => {
    const resumo = resumoDoHorario(
      porHora({ 8: 6184, 9: 7003, 10: 6704, 11: 5338, 12: 3380, 13: 3178, 14: 5129, 15: 5256, 16: 5000 }),
    );

    // Mesmos numeros do mockup: "picos as 9h e 15h · vale entre 12h e 13h".
    expect(resumo).toEqual({ tipo: "picosEVale", manha: 9, tarde: 15, inicio: 12, fim: 13 });
  });

  it("vale de uma hora so quando as vizinhas ficam claramente acima", () => {
    // Toda hora entre os picos tem movimento: hora zerada ali seria, com razao, o proprio vale.
    const resumo = resumoDoHorario(porHora({ 9: 100, 10: 90, 11: 80, 12: 20, 13: 80, 14: 90, 15: 100 }));

    expect(resumo).toEqual({ tipo: "picosEVale", manha: 9, tarde: 15, inicio: 12, fim: 12 });
  });

  it("picos em horas vizinhas nao tem vale entre eles", () => {
    expect(resumoDoHorario(porHora({ 11: 50, 12: 60 }))).toEqual({ tipo: "picos", manha: 11, tarde: 12 });
  });

  it("movimento so em um turno mostra um pico so", () => {
    expect(resumoDoHorario(porHora({ 14: 30, 15: 90 }))).toEqual({ tipo: "picoUnico", hora: 15 });
  });

  it("sem nenhuma mensagem nao ha resumo a mostrar", () => {
    expect(resumoDoHorario(porHora({}))).toBeNull();
  });
});
