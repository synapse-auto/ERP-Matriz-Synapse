import { fireEvent, render, screen, within } from "@testing-library/react";
import type { ComponentProps } from "react";
import { useState } from "react";
import { describe, expect, it, vi } from "vitest";

import type { TemplateWhatsApp } from "@/lib/atendimento/types";
import type { Textos } from "@/lib/config/schema";

import { ModalDeTemplates } from "./modal-de-templates";

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
  escolherTemplate: "Enviar template",
  criarTemplate: "Criar template",
  colunaTemplates: "Templates",
  colunaConfiguracao: "Configuração de envio",
  colunaPrevia: "Prévia",
  parametroEnvio: "Mensagem — variável {indice}",
  marcadorVariavelVazia: "[variável {indice}]",
  configuracaoSemSelecao: "Escolha um template para preencher as variáveis de envio.",
  previaSemSelecao: "Escolha um template para ver como a mensagem chega.",
  configuracaoSemVariaveis: "Não há nada a preencher neste template.",
  novaMensagem: "Nova mensagem",
  cancelarTemplate: "Cancelar",
} as Textos["atendimentos"]["composer"];

const rotulosDeCategoria = {
  UTILIDADE: "Utilidade",
  MARKETING: "Marketing",
  AUTENTICACAO: "Autenticação",
};

const rotulosDeStatus = {
  APROVADO: "Aprovado",
  PENDENTE: "Pendente",
  REJEITADO: "Rejeitado",
  PAUSADO: "Pausado",
  DESCONHECIDO: "Desconhecido",
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

const marketing: TemplateWhatsApp = {
  nome: "promocao",
  idioma: "pt_BR",
  categoria: "MARKETING",
  status: "APROVADO",
  corpo: "Oferta *especial* para {{1}}",
  quantidadeDeParametros: 1,
};

const pendente: TemplateWhatsApp = {
  ...aprovado,
  nome: "ainda_nao",
  status: "PENDENTE",
};

function renderizar(
  props: Partial<ComponentProps<typeof ModalDeTemplates>> = {},
) {
  return render(
    <ModalDeTemplates
      aberto
      onAbertoChange={vi.fn()}
      textos={textos}
      rotulosDeCategoria={rotulosDeCategoria}
      rotulosDeStatus={rotulosDeStatus}
      templates={{ data: [aprovado, pendente], isError: false, isLoading: false }}
      parametros={{}}
      onParametros={vi.fn()}
      enviando={false}
      onEnviar={vi.fn()}
      {...props}
    />,
  );
}

function ModalControlado(
  props: Partial<ComponentProps<typeof ModalDeTemplates>> & {
    parametrosIniciais?: Record<string, string[]>;
  },
) {
  const { parametrosIniciais = {}, ...rest } = props;
  const [parametros, setParametros] = useState(parametrosIniciais);
  return (
    <ModalDeTemplates
      aberto
      onAbertoChange={vi.fn()}
      textos={textos}
      rotulosDeCategoria={rotulosDeCategoria}
      rotulosDeStatus={rotulosDeStatus}
      templates={{ data: [comVariaveis], isError: false, isLoading: false }}
      parametros={parametros}
      onParametros={(chave, valores) =>
        setParametros((atual) => ({ ...atual, [chave]: valores }))
      }
      enviando={false}
      onEnviar={vi.fn()}
      {...rest}
    />
  );
}

describe("ModalDeTemplates", () => {
  it("só oferece templates aprovados e envia o escolhido", () => {
    const onEnviar = vi.fn();
    renderizar({
      templates: { data: [aprovado, pendente], isError: false, isLoading: false },
      onEnviar,
    });

    expect(screen.getByText("boas_vindas")).toBeInTheDocument();
    expect(screen.queryByText("ainda_nao")).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Enviar este template" })).toBeDisabled();

    fireEvent.click(screen.getByRole("button", { name: /boas_vindas/ }));
    fireEvent.click(screen.getByRole("button", { name: "Enviar este template" }));
    expect(onEnviar).toHaveBeenCalledWith(aprovado, []);
  });

  it("preenche configuração e prévia ao selecionar, e troca as duas ao mudar de template", () => {
    renderizar({
      templates: { data: [aprovado, comVariaveis], isError: false, isLoading: false },
    });

    expect(screen.getByText("Escolha um template para preencher as variáveis de envio.")).toBeInTheDocument();
    expect(screen.getByText("Escolha um template para ver como a mensagem chega.")).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: /boas_vindas/ }));
    expect(screen.getByText("Não há nada a preencher neste template.")).toBeInTheDocument();
    expect(screen.getByText("Olá")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Enviar este template" })).toBeEnabled();

    fireEvent.click(screen.getByRole("button", { name: /retorno_orcamento/ }));
    expect(screen.queryByText("Não há nada a preencher neste template.")).not.toBeInTheDocument();
    expect(screen.getByLabelText("Mensagem — variável 1")).toBeInTheDocument();
    expect(screen.getByLabelText("Mensagem — variável 2")).toBeInTheDocument();
    expect(screen.getByText("Olá [variável 1], o orçamento do pedido [variável 2] ficou pronto.")).toBeInTheDocument();
    expect(screen.queryByText(/\{\{1\}\}/)).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Enviar este template" })).toBeDisabled();
  });

  it("bloqueia a ação com variável vazia e marca o campo com aria-invalid", () => {
    const onEnviar = vi.fn();
    render(<ModalControlado onEnviar={onEnviar} />);

    fireEvent.click(screen.getByRole("button", { name: /retorno_orcamento/ }));
    fireEvent.change(screen.getByLabelText("Mensagem — variável 1"), { target: { value: "Maria" } });

    expect(screen.getByText("Olá Maria, o orçamento do pedido [variável 2] ficou pronto.")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Enviar este template" })).toBeDisabled();
    expect(screen.getByLabelText("Mensagem — variável 2")).toHaveAttribute("aria-invalid", "true");
    expect(screen.getByText("Preencha esta variável para enviar.")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Enviar este template" }));
    expect(onEnviar).not.toHaveBeenCalled();
  });

  it("atualiza a prévia com o valor preenchido e envia quando as variáveis estão completas", () => {
    const onEnviar = vi.fn();
    const { rerender } = renderizar({
      templates: { data: [comVariaveis], isError: false, isLoading: false },
      parametros: { "retorno_orcamento:pt_BR": ["Maria", ""] },
      onEnviar,
    });

    fireEvent.click(screen.getByRole("button", { name: /retorno_orcamento/ }));
    expect(screen.getByText("Olá Maria, o orçamento do pedido [variável 2] ficou pronto.")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Enviar este template" })).toBeDisabled();

    rerender(
      <ModalDeTemplates
        aberto
        onAbertoChange={vi.fn()}
        textos={textos}
        rotulosDeCategoria={rotulosDeCategoria}
        rotulosDeStatus={rotulosDeStatus}
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

  it("mostra o corpo literal na prévia, sem transformar asteriscos em negrito", () => {
    renderizar({
      templates: { data: [marketing], isError: false, isLoading: false },
      parametros: { "promocao:pt_BR": ["Ana"] },
    });

    fireEvent.click(screen.getByRole("button", { name: /promocao/ }));
    const previa = screen.getByText("Oferta *especial* para Ana");
    expect(previa.tagName).toBe("P");
    expect(previa.querySelector("strong, b, em")).toBeNull();
  });

  it("empilha as colunas na ordem TEMPLATES → CONFIGURAÇÃO → PRÉVIA e abre em três no lg", () => {
    renderizar();
    const dialog = screen.getByRole("dialog");
    const titulos = within(dialog).getAllByRole("heading", { level: 3 });
    expect(titulos.map((titulo) => titulo.textContent)).toEqual([
      "Templates",
      "Configuração de envio",
      "Prévia",
    ]);
    expect(dialog.querySelector(".lg\\:grid-cols-3")).toBeTruthy();
    expect(dialog.querySelector(".grid-cols-1")).toBeTruthy();
  });

  it("mantém o link de criar template e o cancelar no rodapé", () => {
    renderizar();
    expect(screen.getByRole("link", { name: "Criar template" })).toHaveAttribute(
      "href",
      "/templates-whatsapp",
    );
    expect(screen.getByRole("button", { name: "Cancelar" })).toBeInTheDocument();
  });
});
