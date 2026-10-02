import { describe, expect, it } from "vitest";

import { estadoInicial, FILTRO_VAZIO, filtroSemVazios, pedidoDeCampanha } from "./estado-do-assistente";
import {
  alternarDia,
  atalhosDeLimite,
  confirmacaoConfere,
  janelaValida,
  limiteAcimaDaMeta,
  pendenciasDeInicio,
  percentual,
  validarLimiteDiario,
  validarRampa,
  validarRitmo,
  variaveisParaOTemplate,
} from "./validacao";

describe("validarLimiteDiario", () => {
  it("aceita de 1 até o teto da instância", () => {
    expect(validarLimiteDiario(1, 200)).toBe("ok");
    expect(validarLimiteDiario(200, 200)).toBe("ok");
  });

  it("recusa acima do teto, zero, negativo, fracionado e vazio", () => {
    expect(validarLimiteDiario(201, 200)).toBe("acima_do_teto");
    expect(validarLimiteDiario(0, 200)).toBe("invalido");
    expect(validarLimiteDiario(-5, 200)).toBe("invalido");
    expect(validarLimiteDiario(10.5, 200)).toBe("invalido");
    expect(validarLimiteDiario(null, 200)).toBe("invalido");
  });
});

describe("limiteAcimaDaMeta", () => {
  it("só avisa quando o limite da Meta foi informado e é ultrapassado", () => {
    expect(limiteAcimaDaMeta(300, 250)).toBe(true);
    expect(limiteAcimaDaMeta(250, 250)).toBe(false);
    expect(limiteAcimaDaMeta(300, 0)).toBe(false);
  });
});

describe("atalhosDeLimite", () => {
  it("nunca passa do teto e termina no próprio teto", () => {
    expect(atalhosDeLimite(200)).toEqual([50, 100, 200]);
    expect(atalhosDeLimite(80)).toEqual([50, 80]);
    expect(atalhosDeLimite(2000)).toEqual([50, 100, 200, 500, 1000, 2000]);
  });
});

describe("ritmo e janela", () => {
  it("ritmo fica entre 1 e 600", () => {
    expect(validarRitmo(1)).toBe(true);
    expect(validarRitmo(600)).toBe(true);
    expect(validarRitmo(0)).toBe(false);
    expect(validarRitmo(601)).toBe(false);
    expect(validarRitmo(null)).toBe(false);
  });

  it("o fim da janela precisa ser depois do início", () => {
    expect(janelaValida("09:00", "18:00")).toBe(true);
    expect(janelaValida("09:00:00", "09:30:00")).toBe(true);
    expect(janelaValida("18:00", "09:00")).toBe(false);
    expect(janelaValida("09:00", "09:00")).toBe(false);
    expect(janelaValida("", "09:00")).toBe(false);
  });
});

describe("validarRampa", () => {
  it("sem rampa é válido; com rampa o teto fica entre o limite e o teto da instância", () => {
    expect(validarRampa(null, 100, 200)).toBe(true);
    expect(validarRampa({ incrementoPorDia: 50, teto: 200 }, 100, 200)).toBe(true);
    expect(validarRampa({ incrementoPorDia: 50, teto: 90 }, 100, 200)).toBe(false);
    expect(validarRampa({ incrementoPorDia: 50, teto: 300 }, 100, 200)).toBe(false);
    expect(validarRampa({ incrementoPorDia: 0, teto: 150 }, 100, 200)).toBe(false);
  });
});

describe("confirmação digitada e pendências de início", () => {
  it("confere só o número exato de destinatários, ignorando espaços", () => {
    expect(confirmacaoConfere("120", 120)).toBe(true);
    expect(confirmacaoConfere(" 120 ", 120)).toBe(true);
    expect(confirmacaoConfere("12", 120)).toBe(false);
    expect(confirmacaoConfere("", 120)).toBe(false);
  });

  it("libera o início só com administrador, público, consentimento e número digitado", () => {
    const base = { ehAdministrador: true, consentimento: true, confirmacaoDigitada: "120", destinatarios: 120 };
    expect(pendenciasDeInicio(base)).toEqual([]);
    expect(pendenciasDeInicio({ ...base, ehAdministrador: false })).toEqual(["permissao"]);
    expect(pendenciasDeInicio({ ...base, consentimento: false })).toEqual(["consentimento"]);
    expect(pendenciasDeInicio({ ...base, confirmacaoDigitada: "99" })).toEqual(["total"]);
    expect(pendenciasDeInicio({ ...base, destinatarios: 0, confirmacaoDigitada: "0" })).toEqual(["publico"]);
  });
});

describe("percentual", () => {
  it("é 0 sem total e nunca passa de 100", () => {
    expect(percentual(5, 0)).toBe(0);
    expect(percentual(1, 3)).toBe(33);
    expect(percentual(5, 4)).toBe(100);
  });
});

describe("estado do assistente", () => {
  it("filtro vazio vira Agenda inteira (objeto sem chaves)", () => {
    expect(filtroSemVazios(FILTRO_VAZIO)).toEqual({});
    expect(filtroSemVazios({ ...FILTRO_VAZIO, tagIds: ["t1"], nuncaConversou: true, busca: " ana " })).toEqual({
      tagIds: ["t1"],
      nuncaConversou: true,
      busca: "ana",
    });
  });

  it("alterna dias da semana mantendo a ordem", () => {
    expect(alternarDia([1, 2, 3], 5)).toEqual([1, 2, 3, 5]);
    expect(alternarDia([1, 2, 3], 2)).toEqual([1, 3]);
  });

  it("ajusta as variáveis à contagem do template preservando a escolha", () => {
    const atuais = [{ posicao: 1, campo: "EMPRESA" as const, reserva: "sua empresa" }];
    const novas = variaveisParaOTemplate(atuais, 2);
    expect(novas).toHaveLength(2);
    expect(novas[0]).toEqual(atuais[0]);
    expect(novas[1]).toMatchObject({ posicao: 2, campo: "PRIMEIRO_NOME" });
    expect(variaveisParaOTemplate(novas, 0)).toEqual([]);
  });

  it("monta o pedido só com template e nome, e agenda em ISO", () => {
    const vazio = estadoInicial(100);
    expect(pedidoDeCampanha(vazio)).toBeNull();

    const pedido = pedidoDeCampanha({
      ...vazio,
      nome: "  Retorno  ",
      template: { nome: "retorno", idioma: "pt_BR", parametros: 0 },
      modoDeInicio: "AGENDADA",
      agendadaPara: "2030-01-10T09:00",
      rampaAtiva: true,
      rampaIncremento: 25,
      rampaTeto: 150,
    });
    expect(pedido).toMatchObject({
      nome: "Retorno",
      templateNome: "retorno",
      limiteDiario: 100,
      rampa: { incrementoPorDia: 25, teto: 150 },
    });
    expect(pedido?.agendadaPara).toMatch(/^2030-01-10T/);
    expect(pedido?.janela).toEqual({ inicio: "09:00", fim: "18:00", dias: [1, 2, 3, 4, 5] });
  });
});
