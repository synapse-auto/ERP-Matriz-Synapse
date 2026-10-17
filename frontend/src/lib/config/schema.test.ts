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
  it("mantém mensagens da edição de telefone com catálogos antigos", () => {
    const catalogo = carregarJson("../backend/crm-app/src/main/resources/textos.json");
    const dados = (catalogo.painelLead as Record<string, unknown>).dados as Record<string, unknown>;
    delete dados.telefoneIndisponivel;
    delete dados.telefoneSemPermissao;
    delete dados.telefoneNaoEncontrado;
    const resultado = TextosSchema.parse(catalogo).painelLead.dados;
    expect(resultado.telefoneIndisponivel).toBeTruthy();
    expect(resultado.telefoneSemPermissao).toBeTruthy();
    expect(resultado.telefoneNaoEncontrado).toBeTruthy();
  });
  it("completa os textos de conta de teste em catalogos antigos sem reescrever personalizacao", () => {
    const catalogo=carregarJson("../backend/crm-app/src/main/resources/textos.json");
    delete catalogo.usuariosTeste;
    expect(TextosSchema.parse(catalogo).usuariosTeste.rotulo).toBe("Usuário de teste");
    catalogo.usuariosTeste={selo:"Ambiente de homologação"};
    expect(TextosSchema.parse(catalogo).usuariosTeste.selo).toBe("Ambiente de homologação");
  });
  it("completa títulos e estados das categorias em catálogos antigos", () => {
    const catalogo = carregarJson("../backend/crm-app/src/main/resources/textos.json");
    const painel = (catalogo.atendimentos as Record<string, unknown>).painel as Record<string, unknown>;
    const secoes = painel.secoes as Record<string, unknown>;
    delete secoes.midiasDaFicha;
    delete secoes.documentos;
    for (const chave of ["vazioMidiasDaFicha", "vazioDocumentos", "carregandoArquivos", "erroArquivos"]) delete painel[chave];
    const textos = TextosSchema.parse(catalogo).atendimentos.painel;
    expect(textos.secoes.midiasDaFicha).toBe("Mídias");
    expect(textos.secoes.documentos).toBe("Documentos");
    expect(textos.vazioDocumentos).toBe("Nenhum documento");
  });
  it("preserva catálogos anteriores aos motivos e paginação da finalização em massa", () => {
    const catalogo = carregarJson("../backend/crm-app/src/main/resources/textos.json");
    const massa = (catalogo.atendimentos as Record<string, unknown>).finalizacaoEmMassa as Record<string, Record<string, unknown>>;
    delete massa.resultado.paginaAnterior;
    delete massa.resultado.proximaPagina;
    delete massa.motivos.RESULTADO_VENDA_PENDENTE;
    const textos = TextosSchema.parse(catalogo).atendimentos.finalizacaoEmMassa;
    expect(textos.resultado.paginaAnterior).toBeTruthy();
    expect(textos.resultado.proximaPagina).toBeTruthy();
    expect(textos.motivos.RESULTADO_VENDA_PENDENTE).toContain("Negociação");
  });
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

  it("completa o bloco das informações do chatbot quando o backend ainda serve o catálogo anterior", () => {
    const catalogo = carregarJson("../backend/crm-app/src/main/resources/textos.json");
    delete (catalogo.atendimentos as Record<string, unknown>).informacoesChatbot;

    const textos = TextosSchema.parse(catalogo);

    expect(textos.atendimentos.informacoesChatbot.titulo).toBeTruthy();
    expect(textos.atendimentos.informacoesChatbot.verMais).toBeTruthy();
  });

  it("mantém obrigatória a estrutura anterior do catálogo", () => {
    const catalogo = carregarJson("../backend/crm-app/src/main/resources/textos.json");
    delete catalogo.login;

    expect(() => TextosSchema.parse(catalogo)).toThrow();
  });
});

describe("TemaSchema", () => {
  it("preserva o tema completo servido pelo backend e só acrescenta os dois tokens derivados da D1 da Disbrel", () => {
    const tema = carregarJson("../backend/crm-app/src/main/resources/tema.json");

    // O tema padrão não traz sidebarTextoForte nem mensagemFalhaTexto; o frontend os deriva reproduzindo o
    // comportamento anterior (texto branco na sidebar; mesma cor de perigo da sidebar sobre o balão).
    expect(TemaSchema.parse(tema)).toEqual({
      ...tema,
      sidebarTextoForte: "#FFFFFF",
      mensagemFalhaTexto: tema.sidebarItemTextoPerigo,
    });
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
    delete tema.mensagemLidaTexto;

    const resultado = TemaSchema.parse(tema);

    expect(resultado.fundoCanvas).toBe(resultado.fundoApp);
    expect(resultado.sidebarItemTextoHover).toBe(resultado.textoSidebarItem);
    expect(resultado.sidebarItemAcentoAtivo).toBe(resultado.corPrimaria);
    expect(resultado.sidebarItemTextoPerigo).toBe(resultado.corErro);
    expect(resultado.mensagemLidaTexto).toBe(resultado.corSucesso);
  });

  it("mantém obrigatórios os tokens da primeira versão", () => {
    const tema = carregarJson("../backend/crm-app/src/main/resources/tema.json");
    delete tema.corPrimaria;

    expect(() => TemaSchema.parse(tema)).toThrow();
  });
});
