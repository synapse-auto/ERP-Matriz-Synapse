import { describe, expect, it } from "vitest";

import { ehAvisoDeFinalizacaoEmMassa, listaDeNomes } from "./aviso";

const valido = {
  tipo: "FINALIZACAO_EM_MASSA_CONCLUIDA",
  eventoId: "op-1:usuario-1",
  dados: {
    operacaoId: "op-1",
    finalizadosDoUsuario: 2,
    totalFinalizados: 3,
    ignorados: 0,
    falhas: 0,
    parcial: false,
    afetados: [{ nome: "Clayton", finalizados: 2 }],
  },
};

describe("ehAvisoDeFinalizacaoEmMassa", () => {
  it("reconhece o aviso completo", () => {
    expect(ehAvisoDeFinalizacaoEmMassa(valido)).toBe(true);
  });

  it("recusa tipo diferente, sem eventoId, sem afetados ou valores que não são objeto", () => {
    expect(ehAvisoDeFinalizacaoEmMassa({ ...valido, tipo: "ACESSO_ALTERADO" })).toBe(false);
    expect(ehAvisoDeFinalizacaoEmMassa({ ...valido, eventoId: undefined })).toBe(false);
    expect(ehAvisoDeFinalizacaoEmMassa({ ...valido, dados: { ...valido.dados, afetados: undefined } })).toBe(false);
    expect(ehAvisoDeFinalizacaoEmMassa({ ...valido, dados: null })).toBe(false);
    expect(ehAvisoDeFinalizacaoEmMassa(null)).toBe(false);
    expect(ehAvisoDeFinalizacaoEmMassa("FINALIZACAO_EM_MASSA_CONCLUIDA")).toBe(false);
  });
});

describe("listaDeNomes", () => {
  it("junta em português", () => {
    expect(listaDeNomes(["Clayton"])).toBe("Clayton");
    expect(listaDeNomes(["Clayton", "Nayara"])).toBe("Clayton e Nayara");
    expect(listaDeNomes(["Clayton", "Nayara", "Bruno"])).toBe("Clayton, Nayara e Bruno");
  });
});
