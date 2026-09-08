import { readFileSync } from "node:fs";
import { resolve } from "node:path";

import { describe, expect, it } from "vitest";

import { TemaSchema, TextosSchema } from "./schema";

function carregarJson(caminho: string): Record<string, unknown> {
  return JSON.parse(readFileSync(resolve(process.cwd(), caminho), "utf8")) as Record<
    string,
    unknown
  >;
}

describe("TextosSchema", () => {
  it("aceita o catálogo servido pelo backend e mantém os textos da automação no grupo correto", () => {
    const catalogo = carregarJson("../backend/crm-app/src/main/resources/textos.json");

    const textos = TextosSchema.parse(catalogo);

    expect(textos).toEqual(catalogo);
    expect(textos.automacao.abas.followUp).toBe("Follow-up");
    expect(textos.automacao.recursosIa.resumo).toBe("Resumo automático por IA");
    expect(textos.automacao.regras.novo).toBe("Nova regra");
    expect(textos.automacao.disponibilidade.contagem).toContain("{disponiveis}");
    expect(textos.automacao.regras.visualizacaoWhatsapp).toBe("VISUALIZAÇÃO NO WHATSAPP");
    expect(textos.atendimentos.mensagem.prefixoAtendente).toContain("{nome}");
    expect(textos.atendimentos.mensagem.prefixoAtendente).toContain("{mensagem}");
  });

  it("completa campos adicionados nas E158 e E159 quando o backend ainda serve o catálogo anterior", () => {
    const catalogo = carregarJson("../backend/crm-app/src/main/resources/textos.json");
    const atendimentos = catalogo.atendimentos as Record<string, unknown>;
    const cartao = atendimentos.cartao as Record<string, unknown>;
    const agenda = catalogo.agenda as Record<string, unknown>;
    delete cartao.atrasoAtendente;
    delete agenda.importarCsv;
    delete agenda.exportarCsv;
    delete agenda.importacao;
    delete agenda.exportacao;

    const textos = TextosSchema.parse(catalogo);

    expect(textos.atendimentos.cartao.atrasoAtendente).toBeTruthy();
    expect(textos.agenda.importarCsv).toBeTruthy();
    expect(textos.agenda.importacao.titulo).toBeTruthy();
    expect(textos.agenda.exportacao.arquivo).toBeTruthy();
  });

  it("mantém obrigatória a estrutura anterior do catálogo", () => {
    const catalogo = carregarJson("../backend/crm-app/src/main/resources/textos.json");
    delete catalogo.login;

    expect(() => TextosSchema.parse(catalogo)).toThrow();
  });
});

describe("TemaSchema", () => {
  it("preserva sem alteração o tema completo servido pelo backend", () => {
    const tema = carregarJson("../backend/crm-app/src/main/resources/tema.json");

    expect(TemaSchema.parse(tema)).toEqual(tema);
  });

  it("deriva tokens adicionados depois da primeira versão quando o backend ainda não os fornece", () => {
    const tema = carregarJson("../backend/crm-app/src/main/resources/tema.json");
    delete tema.fundoCanvas;
    delete tema.sidebarItemTextoHover;
    delete tema.sidebarItemIconeAtivo;
    delete tema.sidebarItemOverlayHover;
    delete tema.sidebarItemOverlayAtivo;
    delete tema.sidebarItemOverlayAtivoHover;
    delete tema.sidebarItemAcentoAtivo;
    delete tema.marcaIconeGradienteInicio;
    delete tema.marcaIconeGradienteFim;
    delete tema.sidebarItemTextoPerigo;
    delete tema.sidebarItemOverlayPerigo;

    const resultado = TemaSchema.parse(tema);

    expect(resultado.fundoCanvas).toBe(resultado.fundoApp);
    expect(resultado.sidebarItemTextoHover).toBe(resultado.textoSidebarItem);
    expect(resultado.sidebarItemAcentoAtivo).toBe(resultado.corPrimaria);
    expect(resultado.sidebarItemTextoPerigo).toBe(resultado.corErro);
  });

  it("mantém obrigatórios os tokens da primeira versão", () => {
    const tema = carregarJson("../backend/crm-app/src/main/resources/tema.json");
    delete tema.corPrimaria;

    expect(() => TemaSchema.parse(tema)).toThrow();
  });
});
