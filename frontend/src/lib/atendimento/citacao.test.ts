import { describe, expect, it } from "vitest";

import { citacaoDeResposta, previaExibida } from "./citacao";
import type { MensagemResposta } from "./types";

function mensagem(parcial: Partial<MensagemResposta>): MensagemResposta {
  return {
    id: "msg-1",
    remetenteTipo: "LEAD",
    remetenteId: null,
    remetenteNome: "Maria",
    tipo: "TEXTO",
    conteudo: "orçamento da suíte",
    midiaUrl: null,
    midiaMetadados: null,
    opcoes: null,
    statusEntrega: "ENTREGUE",
    erroEntrega: null,
    enviadoEm: "2026-08-29T12:00:00Z",
    ...parcial,
  };
}

const rotulos = {
  imagem: "Foto",
  audio: "Áudio",
  documento: "Documento",
  origemIndisponivel: "Mensagem original indisponível",
};

describe("citacaoDeResposta", () => {
  it("usa o nome conhecido e a prévia sanitizada", () => {
    const citacao = citacaoDeResposta(mensagem({ conteudo: "  linha\nquebra  " }));
    expect(citacao.tipoReferencia).toBe("RESPOSTA");
    expect(citacao.autor).toBe("Maria");
    expect(citacao.previa).toBe("linha quebra");
  });

  it("cai no tipo de mídia quando não há texto nem legenda", () => {
    const citacao = citacaoDeResposta(
      mensagem({ tipo: "AUDIO", conteudo: null, remetenteNome: null }),
    );
    expect(citacao.autor).toBe("Lead");
    expect(previaExibida(citacao, rotulos)).toBe("Áudio");
  });

  it("anuncia origem indisponível quando não há prévia nem tipo de mídia", () => {
    expect(
      previaExibida(
        { origemId: "x", tipoReferencia: "RESPOSTA", autor: "", tipoConteudo: "TEXTO", previa: "" },
        rotulos,
      ),
    ).toBe("Mensagem original indisponível");
  });
});
