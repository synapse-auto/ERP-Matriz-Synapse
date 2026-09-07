import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import type { ComponentProps, ReactElement } from "react";
import { useState } from "react";
import { afterEach, describe, expect, it, vi } from "vitest";

const authMock = vi.hoisted(() => ({ papel: "ATENDENTE" }));
const apiMock = vi.hoisted(() => ({
  editar: vi.fn(),
  excluir: vi.fn(),
}));

vi.mock("@/lib/auth/auth-store", () => ({
  useAuthStore: (seletor: (estado: { papel: string }) => unknown) => seletor(authMock),
}));

vi.mock("@/lib/config/textos-provider", () => ({
  useTextos: () => ({
    templatesWhatsApp: {
      editar: "Editar",
      excluir: "Excluir",
      formulario: {
        editarTitulo: "Editar template",
        corpo: "Corpo",
        variavelInvalida: "Variável inválida",
        cancelar: "Cancelar edição",
        salvarEdicao: "Salvar alteração",
        erroEdicao: "Erro ao editar",
      },
      confirmacaoExclusao: {
        titulo: "Excluir template?",
        descricao: "A exclusão de {nome} afeta a conta compartilhada.",
        confirmar: "Excluir na Meta",
        cancelar: "Cancelar exclusão",
      },
    },
  }),
}));

vi.mock("@/lib/atendimento/api", () => ({
  editarTemplateWhatsApp: apiMock.editar,
  excluirTemplateWhatsApp: apiMock.excluir,
}));

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
  id: "template-1",
  nome: "boas_vindas",
  idioma: "pt_BR",
  categoria: "UTILIDADE",
  status: "APROVADO",
  corpo: "Olá",
  quantidadeDeParametros: 0,
};

const comVariaveis: TemplateWhatsApp = {
  id: "template-2",
  nome: "retorno_orcamento",
  idioma: "pt_BR",
  categoria: "UTILIDADE",
  status: "APROVADO",
  corpo: "Olá {{1}}, o orçamento do pedido {{2}} ficou pronto.",
  quantidadeDeParametros: 2,
};

const marketing: TemplateWhatsApp = {
  id: "template-3",
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

afterEach(() => {
  authMock.papel = "ATENDENTE";
  apiMock.editar.mockReset();
  apiMock.excluir.mockReset();
});

function comProvider(element: ReactElement) {
  return (
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      {element}
    </QueryClientProvider>
  );
}

function renderizar(
  props: Partial<ComponentProps<typeof ModalDeTemplates>> = {},
) {
  return render(
    comProvider(<ModalDeTemplates
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
    />),
  );
}

function ModalControlado(
  props: Partial<ComponentProps<typeof ModalDeTemplates>> & {
    parametrosIniciais?: Record<string, string[]>;
  },
) {
  const { parametrosIniciais = {}, ...rest } = props;
  const [parametros, setParametros] = useState(parametrosIniciais);
  return comProvider(
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
    />,
  );
}

describe("ModalDeTemplates", () => {
  it("esconde editar e excluir para atendente", () => {
    authMock.papel = "ATENDENTE";
    renderizar();

    expect(screen.queryByRole("button", { name: "Editar: boas_vindas" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Excluir: boas_vindas" })).not.toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Criar template" })).toBeInTheDocument();
  });

  it("mostra acoes de gestao sem selecionar o card e abre os dialogos compartilhados", () => {
    authMock.papel = "GESTOR";
    renderizar();

    fireEvent.click(screen.getByRole("button", { name: "Editar: boas_vindas" }));

    expect(screen.getByRole("dialog")).toHaveTextContent("Editar template");
    expect(screen.getByText("Escolha um template para preencher as variáveis de envio.")).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "Cancelar edição" }));
    fireEvent.click(screen.getByRole("button", { name: "Excluir: boas_vindas" }));
    const confirmacao = screen.getByRole("dialog");
    expect(confirmacao).toHaveTextContent("boas_vindas");
    expect(confirmacao).toHaveTextContent("conta compartilhada");
  });

  it("edita pelo modal e reflete o corpo atualizado na previa após a revalidacao", async () => {
    authMock.papel = "SUBGESTOR";
    apiMock.editar.mockResolvedValue(undefined);
    const { rerender } = renderizar({
      templates: { data: [comVariaveis], isError: false, isLoading: false },
    });

    const item = screen.getByText("retorno_orcamento").closest("li");
    fireEvent.click(item!.querySelector("button[aria-pressed]")!);
    fireEvent.click(screen.getByRole("button", { name: "Editar: retorno_orcamento" }));
    const formulario = screen.getByRole("dialog");
    fireEvent.change(within(formulario).getByRole("textbox", { name: "Corpo" }), {
      target: { value: "Mensagem nova {{1}} e {{2}}" },
    });
    fireEvent.click(within(formulario).getByRole("button", { name: "Salvar alteração" }));

    await waitFor(() =>
      expect(apiMock.editar).toHaveBeenCalledWith("template-2", { corpo: "Mensagem nova {{1}} e {{2}}" }),
    );
    rerender(
      comProvider(
        <ModalDeTemplates
          aberto
          onAbertoChange={vi.fn()}
          textos={textos}
          rotulosDeCategoria={rotulosDeCategoria}
          rotulosDeStatus={rotulosDeStatus}
          templates={{
            data: [{ ...comVariaveis, corpo: "Mensagem nova {{1}} e {{2}}" }],
            isError: false,
            isLoading: false,
          }}
          parametros={{ "retorno_orcamento:pt_BR": ["Maria", "42"] }}
          onParametros={vi.fn()}
          enviando={false}
          onEnviar={vi.fn()}
        />,
      ),
    );

    expect(screen.getByText("Mensagem nova Maria e 42")).toBeInTheDocument();
  });

  it("limpa a selecao quando exclui o template selecionado", async () => {
    authMock.papel = "ADMINISTRADOR";
    apiMock.excluir.mockResolvedValue(undefined);
    const { rerender } = renderizar({
      templates: { data: [aprovado, comVariaveis], isError: false, isLoading: false },
    });

    const item = screen.getByText("boas_vindas").closest("li");
    fireEvent.click(item!.querySelector("button[aria-pressed]")!);
    fireEvent.click(screen.getByRole("button", { name: "Excluir: boas_vindas" }));
    fireEvent.click(screen.getByRole("button", { name: "Excluir na Meta" }));
    await waitFor(() => expect(apiMock.excluir).toHaveBeenCalledWith("template-1", "boas_vindas"));

    rerender(
      comProvider(
        <ModalDeTemplates
          aberto
          onAbertoChange={vi.fn()}
          textos={textos}
          rotulosDeCategoria={rotulosDeCategoria}
          rotulosDeStatus={rotulosDeStatus}
          templates={{ data: [comVariaveis], isError: false, isLoading: false }}
          parametros={{}}
          onParametros={vi.fn()}
          enviando={false}
          onEnviar={vi.fn()}
        />,
      ),
    );

    expect(screen.queryByRole("button", { name: /boas_vindas/ })).not.toBeInTheDocument();
    expect(screen.getByText("Escolha um template para preencher as variáveis de envio.")).toBeInTheDocument();
  });

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
      comProvider(<ModalDeTemplates
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
      />),
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
