import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act } from "react";
import { describe, expect, it, vi } from "vitest";

import { ErroDeApi } from "@/lib/api/errors";
import type { MinhasPermissoes } from "@/lib/gestao/types";
import { usarCapacidadesReais } from "@/test/capacidades-de-teste";

const capacidadeMock = vi.hoisted(() => ({ gerenciaTemplates: true }));
const gestaoApi = vi.hoisted(() => ({ obterMinhasPermissoes: vi.fn() }));

vi.mock("@/lib/gestao/api", () => gestaoApi);

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
  useAuthStore: (seletor: (estado: { papel: string; status: string; precisaTrocarSenha: boolean }) => unknown) =>
    seletor({ papel: "GESTOR", status: "autenticado", precisaTrocarSenha: false }),
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
      erros: {
        semPermissao: "Sem permissão para templates.",
        naoEncontrado: "Template não existe mais.",
        invalido: "Pedido inválido: {motivo}",
        recusado: "A Meta recusou: {motivo}",
        recusadoSemMotivo: "A Meta recusou.",
        indisponivel: "Provedor indisponível.",
        generico: "Não foi possível.",
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

/** Efetivas de um ATENDENTE vindas de `/gestao/permissoes/minhas` (perfil + exceção já aplicados). */
function minhas(negadas: string[]): MinhasPermissoes {
  const ids = ["templates.ver", "templates.criar", "templates.editar", "templates.excluir"];
  return {
    usuarioId: "ana",
    papel: "ATENDENTE",
    revisao: 1,
    acessaGestao: false,
    editaPerfis: false,
    editaExcecoes: false,
    capacidades: Object.fromEntries(
      ids.map((id) => [id, { permitido: !negadas.includes(id), motivo: "PERMITIDO" as const, alcance: null }]),
    ),
  };
}

function renderizarComCliente() {
  const cliente = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(
    <QueryClientProvider client={cliente}>
      <PaginaTemplatesWhatsApp />
    </QueryClientProvider>,
  );
  return cliente;
}

describe("pagina de templates WhatsApp — permissões efetivas (Gestão, docs/47)", () => {
  it("com Ver permitido e Criar negado, a lista continua e o botão Novo template não existe", async () => {
    usarCapacidadesReais();
    gestaoApi.obterMinhasPermissoes.mockResolvedValue(
      minhas(["templates.criar", "templates.editar", "templates.excluir"]),
    );
    renderizarComCliente();

    expect(await screen.findByText("retorno_orcamento")).toBeInTheDocument();
    await waitFor(() => expect(gestaoApi.obterMinhasPermissoes).toHaveBeenCalled());
    expect(screen.queryByRole("button", { name: "Novo template" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Editar: retorno_orcamento" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Excluir: retorno_orcamento" })).not.toBeInTheDocument();
  });

  it("quem ainda tem Criar vê o botão e conclui a criação", async () => {
    usarCapacidadesReais();
    gestaoApi.obterMinhasPermissoes.mockResolvedValue(minhas(["templates.editar", "templates.excluir"]));
    vi.mocked(criarTemplateWhatsApp).mockResolvedValue(undefined as never);
    renderizarComCliente();

    fireEvent.click(await screen.findByRole("button", { name: "Novo template" }));
    const formulario = await screen.findByRole("dialog");
    fireEvent.change(within(formulario).getByRole("textbox", { name: "Nome" }), { target: { value: "boas_vindas" } });
    fireEvent.change(within(formulario).getByRole("textbox", { name: "Corpo" }), { target: { value: "Olá {{1}}" } });
    fireEvent.click(within(formulario).getByRole("button", { name: "Salvar" }));
    await waitFor(() => expect(criarTemplateWhatsApp).toHaveBeenCalled());
  });

  it("revogação com a tela aberta some com o botão sem F5, e a restauração o traz de volta", async () => {
    usarCapacidadesReais();
    gestaoApi.obterMinhasPermissoes.mockResolvedValue(minhas([]));
    const cliente = renderizarComCliente();
    expect(await screen.findByRole("button", { name: "Novo template" })).toBeInTheDocument();

    // O mesmo efeito do aviso ACESSO_ALTERADO (NotificacoesTempoReal): invalida e revalida no backend.
    gestaoApi.obterMinhasPermissoes.mockResolvedValue(minhas(["templates.criar"]));
    await act(() => cliente.invalidateQueries({ queryKey: ["permissoes"] }));
    await waitFor(() => expect(screen.queryByRole("button", { name: "Novo template" })).not.toBeInTheDocument());
    expect(screen.getByText("retorno_orcamento")).toBeInTheDocument();

    gestaoApi.obterMinhasPermissoes.mockResolvedValue(minhas([]));
    await act(() => cliente.invalidateQueries({ queryKey: ["permissoes"] }));
    expect(await screen.findByRole("button", { name: "Novo template" })).toBeInTheDocument();
  });

  it("enquanto as permissões carregam, nenhuma ação privilegiada aparece", async () => {
    usarCapacidadesReais();
    gestaoApi.obterMinhasPermissoes.mockReturnValue(new Promise(() => {}));
    renderizarComCliente();

    expect(await screen.findByText("retorno_orcamento")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Novo template" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Editar: retorno_orcamento" })).not.toBeInTheDocument();
  });

  it("falha ao consultar permissões mantém o estado seguro", async () => {
    usarCapacidadesReais();
    gestaoApi.obterMinhasPermissoes.mockRejectedValue(new Error("indisponível"));
    renderizarComCliente();

    expect(await screen.findByText("retorno_orcamento")).toBeInTheDocument();
    await waitFor(() => expect(gestaoApi.obterMinhasPermissoes).toHaveBeenCalled());
    expect(screen.queryByRole("button", { name: "Novo template" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Excluir: retorno_orcamento" })).not.toBeInTheDocument();
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

describe("pagina de templates WhatsApp — falhas ao excluir e editar", () => {
  it("403 na exclusão mostra falta de permissão e mantém o diálogo", async () => {
    vi.mocked(excluirTemplateWhatsApp).mockRejectedValueOnce(
      new ErroDeApi(403, { status: 403, detail: "Access Denied" }, "Erro 403"),
    );
    renderizar();
    fireEvent.click(await screen.findByRole("button", { name: "Excluir: retorno_orcamento" }));
    const confirmacao = await screen.findByRole("dialog");
    fireEvent.click(within(confirmacao).getByRole("button", { name: "Excluir na Meta" }));

    expect(await within(confirmacao).findByRole("alert")).toHaveTextContent("Sem permissão para templates.");
    expect(screen.getByRole("dialog")).toBeInTheDocument();
  });

  it("503 na edição não expõe o diagnóstico cru do provedor", async () => {
    vi.mocked(editarTemplateWhatsApp).mockRejectedValueOnce(
      new ErroDeApi(503, { status: 503, detail: "provedor recusou: HTTP 200 text/html <html>" }, "Erro 503"),
    );
    renderizar();
    fireEvent.click(await screen.findByRole("button", { name: "Editar: retorno_orcamento" }));
    const formulario = await screen.findByRole("dialog");
    fireEvent.click(within(formulario).getByRole("button", { name: "Salvar alteração" }));

    const alerta = await within(formulario).findByRole("alert");
    expect(alerta).toHaveTextContent("Provedor indisponível.");
    expect(alerta).not.toHaveTextContent("html");
  });
});
