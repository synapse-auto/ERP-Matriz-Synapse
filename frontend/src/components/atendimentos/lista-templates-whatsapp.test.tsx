import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

import type { TemplateWhatsApp } from "@/lib/atendimento/types";
import type { Textos } from "@/lib/config/schema";

import { ListaTemplatesWhatsApp } from "./lista-templates-whatsapp";

const textos = {
  semTemplates: "Nenhum template aprovado ainda.",
  templatesCarregando: "Carregando templates...",
  templatesErro: "Erro templates",
  parametroTemplate: "Variável {indice}",
  parametroObrigatorio: "Preencha esta variável para enviar.",
  previaTemplate: "Prévia",
  buscaTemplate: "Buscar template",
  semResultadosTemplate: "Nenhum template encontrado.",
  enviarTemplate: "Enviar este template",
  criarTemplate: "Criar template",
} as Textos["atendimentos"]["composer"];

const rotulosDeCategoria = {
  UTILIDADE: "Utilidade",
  MARKETING: "Marketing",
  AUTENTICACAO: "Autenticação",
};

const aprovado: TemplateWhatsApp = {
  nome: "boas_vindas",
  idioma: "pt_BR",
  categoria: "UTILIDADE",
  status: "APROVADO",
  corpo: "Olá",
  quantidadeDeParametros: 0,
};

const comVariaveis: TemplateWhatsApp = {
  nome: "retorno_orcamento",
  idioma: "pt_BR",
  categoria: "UTILIDADE",
  status: "APROVADO",
  corpo: "Olá {{1}}, o orçamento do pedido {{2}} ficou pronto.",
  quantidadeDeParametros: 2,
};

const pendente: TemplateWhatsApp = {
  ...aprovado,
  nome: "ainda_nao",
  status: "PENDENTE",
};

describe("ListaTemplatesWhatsApp", () => {
  it("só oferece templates aprovados e envia o escolhido", () => {
    const onEnviar = vi.fn();
    render(
      <ListaTemplatesWhatsApp
        textos={textos}
        rotulosDeCategoria={rotulosDeCategoria}
        templates={{ data: [aprovado, pendente], isError: false, isLoading: false }}
        parametros={{}}
        onParametros={vi.fn()}
        enviando={false}
        onEnviar={onEnviar}
      />,
    );

    expect(screen.getByText("boas_vindas")).toBeInTheDocument();
    expect(screen.queryByText("ainda_nao")).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "Enviar este template" }));
    expect(onEnviar).toHaveBeenCalledWith(aprovado, []);
  });

  it("reflete o valor digitado na prévia e mostra o trecho da variável", () => {
    const onParametros = vi.fn();
    render(
      <ListaTemplatesWhatsApp
        textos={textos}
        rotulosDeCategoria={rotulosDeCategoria}
        templates={{ data: [comVariaveis], isError: false, isLoading: false }}
        parametros={{}}
        onParametros={onParametros}
        enviando={false}
        onEnviar={vi.fn()}
      />,
    );

    expect(screen.getByText("Olá {{1}}, o orçamento do pedido {{2}} ficou pronto.")).toBeInTheDocument();
    expect(screen.getByText("Variável 1")).toBeInTheDocument();
    expect(screen.getByText("Variável 2")).toBeInTheDocument();

    fireEvent.change(screen.getByLabelText("Variável 1"), { target: { value: "Maria" } });
    expect(onParametros).toHaveBeenCalledWith("retorno_orcamento:pt_BR", ["Maria", ""]);
  });

  it("atualiza a prévia conforme os parâmetros e impede envio com variável vazia", () => {
    const onEnviar = vi.fn();
    const { rerender } = render(
      <ListaTemplatesWhatsApp
        textos={textos}
        rotulosDeCategoria={rotulosDeCategoria}
        templates={{ data: [comVariaveis], isError: false, isLoading: false }}
        parametros={{ "retorno_orcamento:pt_BR": ["Maria", ""] }}
        onParametros={vi.fn()}
        enviando={false}
        onEnviar={onEnviar}
      />,
    );

    expect(screen.getByText("Olá Maria, o orçamento do pedido {{2}} ficou pronto.")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Enviar este template" })).toBeDisabled();
    fireEvent.click(screen.getByRole("button", { name: "Enviar este template" }));
    expect(onEnviar).not.toHaveBeenCalled();

    rerender(
      <ListaTemplatesWhatsApp
        textos={textos}
        rotulosDeCategoria={rotulosDeCategoria}
        templates={{ data: [comVariaveis], isError: false, isLoading: false }}
        parametros={{ "retorno_orcamento:pt_BR": ["Maria", "42"] }}
        onParametros={vi.fn()}
        enviando={false}
        onEnviar={onEnviar}
      />,
    );

    expect(screen.getByText("Olá Maria, o orçamento do pedido 42 ficou pronto.")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Enviar este template" }));
    expect(onEnviar).toHaveBeenCalledWith(comVariaveis, ["Maria", "42"]);
  });

  it("marca o campo vazio depois que o atendente começa a preencher", () => {
    render(
      <ListaTemplatesWhatsApp
        textos={textos}
        rotulosDeCategoria={rotulosDeCategoria}
        templates={{ data: [comVariaveis], isError: false, isLoading: false }}
        parametros={{}}
        onParametros={vi.fn()}
        enviando={false}
        onEnviar={vi.fn()}
      />,
    );

    fireEvent.change(screen.getByLabelText("Variável 1"), { target: { value: "Maria" } });
    expect(screen.getAllByText("Preencha esta variável para enviar.").length).toBeGreaterThan(0);
    expect(screen.getByLabelText("Variável 2")).toHaveAttribute("aria-invalid", "true");
  });
});
