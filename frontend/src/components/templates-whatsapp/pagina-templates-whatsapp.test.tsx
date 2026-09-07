import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { describe, expect, it, vi } from "vitest";

const capacidadeMock = vi.hoisted(() => ({ gerenciaTemplates: true }));

vi.mock("@/lib/atendimento/api", () => ({
  obterCapacidadeDoCanal: () =>
    Promise.resolve({ exigeTemplateForaDaJanela: true, gerenciaTemplates: capacidadeMock.gerenciaTemplates }),
  listarTemplatesWhatsApp: () =>
    Promise.resolve([
      {
        id: "template-1",
        nome: "retorno_orcamento",
        idioma: "pt_BR",
        categoria: "UTILIDADE",
        status: "PENDENTE",
        corpo: "Olá {{1}}, o orçamento ficou pronto.",
        quantidadeDeParametros: 1,
      },
      {
        id: "template-2",
        nome: "promo_agosto",
        idioma: "pt_BR",
        categoria: "MARKETING",
        status: "APROVADO",
        corpo: "Promoção da semana.",
        quantidadeDeParametros: 0,
      },
    ]),
  criarTemplateWhatsApp: vi.fn(),
  editarTemplateWhatsApp: vi.fn(),
  excluirTemplateWhatsApp: vi.fn(),
}));

vi.mock("@/lib/auth/auth-store", () => ({
  useAuthStore: (seletor: (estado: { papel: string }) => unknown) => seletor({ papel: "GESTOR" }),
}));

vi.mock("@/lib/config/textos-provider", () => ({
  useTextos: () => ({
    templatesWhatsApp: {
      titulo: "Templates do WhatsApp",
      descricao: "Crie modelos na Meta.",
      novo: "Novo template",
      carregando: "Carregando",
      vazio: "Vazio",
      erro: "Erro",
      dica: "Depois de aprovado aparece no composer.",
      avisoPendente: "Aguardando Meta",
      gerenciaIndisponivel: "Indisponível",
      editar: "Editar",
      excluir: "Excluir",
      busca: "Buscar template",
      semResultados: "Nenhum template encontrado.",
      categorias: { UTILIDADE: "Utilidade", MARKETING: "Marketing", AUTENTICACAO: "Autenticação" },
      status: {
        APROVADO: "Aprovado",
        PENDENTE: "Pendente",
        REJEITADO: "Rejeitado",
        PAUSADO: "Pausado",
        DESCONHECIDO: "Desconhecido",
      },
      formulario: {
        criarTitulo: "Criar",
        nome: "Nome",
        nomeAjuda: "Ajuda nome",
        idioma: "Idioma",
        categoria: "Categoria",
        corpo: "Corpo",
        corpoAjuda: "Sem cabeçalho nesta versão.",
        variaveisDetectadas: "Variáveis sequenciais: {lista}",
        variavelAusente: "Falta {marcador}.",
        variavelInvalida: "O índice {marcador} é inválido.",
        salvar: "Salvar",
        salvarEdicao: "Salvar alteração",
        cancelar: "Cancelar",
        erro: "Erro ao salvar",
        erroEdicao: "Erro ao editar",
        editarTitulo: "Editar",
      },
      confirmacaoExclusao: {
        titulo: "Excluir?",
        descricao:
          "A exclusão de {nome} acontece na conta WhatsApp Business compartilhada e pode afetar outros sistemas. Mensagens pendentes que usam este template podem falhar. Templates aprovados podem não aceitar o mesmo nome por 30 dias.",
        confirmar: "Excluir na Meta",
        cancelar: "Cancelar",
      },
    },
  }),
}));

import { criarTemplateWhatsApp, editarTemplateWhatsApp, excluirTemplateWhatsApp } from "@/lib/atendimento/api";
import { PaginaTemplatesWhatsApp } from "./pagina-templates-whatsapp";

describe("pagina de templates WhatsApp", () => {
  it("lista o template devolvido pelo provedor, sem inventar modelo", async () => {
    const cliente = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    render(
      <QueryClientProvider client={cliente}>
        <PaginaTemplatesWhatsApp />
      </QueryClientProvider>,
    );

    expect(await screen.findByText("retorno_orcamento")).toBeInTheDocument();
    expect(screen.getByText("Pendente")).toBeInTheDocument();
    expect(screen.getByText("Olá {{1}}, o orçamento ficou pronto.")).toBeInTheDocument();
  });

  it("agrupa pela categoria que o backend devolve e filtra pela busca", async () => {
    renderizar();
    expect(await screen.findByText("Utilidade · 1")).toBeInTheDocument();
    expect(screen.getByText("Marketing · 1")).toBeInTheDocument();
    expect(screen.getByText("Aprovado")).toBeInTheDocument();

    fireEvent.change(screen.getByLabelText("Buscar template"), {
      target: { value: "promo" },
    });
    expect(screen.getByText("promo_agosto")).toBeInTheDocument();
    expect(screen.queryByText("retorno_orcamento")).not.toBeInTheDocument();
  });

  it("lista todas as variaveis presentes no corpo", async () => {
    const formulario = await abrirFormulario();
    fireEvent.change(within(formulario).getByRole("textbox", { name: "Corpo" }), {
      target: { value: "Olá {{1}}, {{2}}, {{3}} e {{4}}." },
    });

    expect(
      within(formulario).getByText(/Variáveis sequenciais: \{\{1\}\}, \{\{2\}\}, \{\{3\}\}, \{\{4\}\}/),
    ).toBeInTheDocument();
  });

  it("bloqueia envio quando falta um indice", async () => {
    const formulario = await abrirFormulario();
    fireEvent.change(within(formulario).getByRole("textbox", { name: "Nome" }), {
      target: { value: "retorno" },
    });
    fireEvent.change(within(formulario).getByRole("textbox", { name: "Corpo" }), {
      target: { value: "Olá {{1}} e {{3}}" },
    });

    expect(within(formulario).getByRole("alert")).toHaveTextContent("Falta {{2}}.");
    expect(within(formulario).getByRole("button", { name: "Salvar" })).toBeDisabled();
    fireEvent.click(within(formulario).getByRole("button", { name: "Salvar" }));
    expect(criarTemplateWhatsApp).not.toHaveBeenCalled();
  });

  it("oferece edicao e exclusao apenas na gestao e confirma impacto na conta", async () => {
    renderizar();
    await screen.findByText("retorno_orcamento");

    fireEvent.click(screen.getByRole("button", { name: "Editar: retorno_orcamento" }));
    const formulario = await screen.findByRole("dialog");
    fireEvent.change(within(formulario).getByRole("textbox", { name: "Corpo" }), {
      target: { value: "Texto atualizado {{1}}" },
    });
    await waitFor(() =>
      expect(within(formulario).getByRole("button", { name: "Salvar alteração" })).not.toBeDisabled(),
    );
    fireEvent.click(within(formulario).getByRole("button", { name: "Salvar alteração" }));
    await waitFor(() =>
      expect(editarTemplateWhatsApp).toHaveBeenCalledWith("template-1", { corpo: "Texto atualizado {{1}}" }),
    );

    fireEvent.click(screen.getByRole("button", { name: "Excluir: retorno_orcamento" }));
    const confirmacao = await screen.findByRole("dialog");
    expect(confirmacao).toHaveTextContent("conta WhatsApp Business compartilhada");
    expect(confirmacao).toHaveTextContent("Mensagens pendentes");
    expect(confirmacao).toHaveTextContent("30 dias");
    fireEvent.click(within(confirmacao).getByRole("button", { name: "Excluir na Meta" }));
    await waitFor(() =>
      expect(excluirTemplateWhatsApp).toHaveBeenCalledWith("template-1", "retorno_orcamento"),
    );
  });

  it("nao renderiza a tela quando o provedor nao gerencia templates", async () => {
    capacidadeMock.gerenciaTemplates = false;
    renderizar();
    await waitFor(() => expect(screen.queryByText("retorno_orcamento")).not.toBeInTheDocument());
    capacidadeMock.gerenciaTemplates = true;
  });
});

async function abrirFormulario() {
  renderizar();
  await screen.findByText("retorno_orcamento");
  fireEvent.click(screen.getByRole("button", { name: "Novo template" }));
  return screen.findByRole("dialog");
}

function renderizar() {
  const cliente = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(
    <QueryClientProvider client={cliente}>
      <PaginaTemplatesWhatsApp />
    </QueryClientProvider>,
  );
}
