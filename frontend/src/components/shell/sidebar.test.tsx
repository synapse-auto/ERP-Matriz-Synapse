import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { EXPANSAO_DA_SIDEBAR, estiloDaLarguraDoSlot, useExpansaoDaSidebar } from "./expansao-da-sidebar";

const authMock = vi.hoisted(() => ({
  papel: "ATENDENTE",
  accessToken: "token-de-teste",
  limparSessao: vi.fn(),
  definirSessao: vi.fn(),
}));
const fetchMock = vi.hoisted(() => vi.fn());
const contagemMock = vi.hoisted(() => vi.fn());
const textosNovidades = vi.hoisted(() => ({ titulo: undefined as string | undefined }));

const ROTULO_NOVIDADES_TESTE = "O que há de novo";

vi.mock("next/navigation", () => ({
  usePathname: () => "/atendimentos",
}));

vi.mock("@/lib/auth/auth-store", () => ({
  useAuthStore: Object.assign(
    (seletor: (estado: typeof authMock & { email: string }) => unknown) =>
      seletor({ ...authMock, email: "ana@dev.local" }),
    { getState: () => authMock },
  ),
}));

const atualizarPresencaMock = vi.fn((status: string) => Promise.resolve({ status }));

vi.mock("@/lib/equipe/api", () => ({
  atualizarPresenca: (status: string) => atualizarPresencaMock(status),
}));

vi.mock("@/lib/equipe/use-equipe", () => ({
  useMeuUsuario: () => ({ data: { nome: "Ana Beatriz", papel: "ATENDENTE", presenca: "ONLINE" } }),
}));

vi.mock("@/lib/atendimento/use-atendimentos", () => ({
  useContagemDeAtendimentos: () => contagemMock(),
}));

vi.mock("@/lib/config/textos-provider", () => ({
  useTextos: () => ({
    app: { marca: "Estrutural Vidros", subtitulo: "CRM · Atendimento" },
    estados: {
      carregando: "Carregando...",
      erroGenerico: "Erro.",
      tentarNovamente: "Tentar novamente",
    },
    menu: {
      grupoMenu: "Menu",
      grupoGestao: "Gestão",
      retrair: "Retrair menu lateral",
      reabrir: "Reabrir menu lateral",
      fixar: "Fixar menu aberto",
      desafixar: "Desafixar menu lateral",
      contagemPendentes: "Atendimentos, {quantidade} pendentes",
      mais: "Mais",
      maisTitulo: "Mais opções",
      fecharMais: "Fechar menu",
      itens: {
        atendimentos: "Atendimentos",
        dashboard: "Dashboard",
        agenda: "Agenda de Contatos",
        tags: "Tags",
        mensagensRapidas: "Mensagens Rápidas",
        templatesWhatsApp: "Templates WhatsApp",
        mensagensProgramadas: "Mensagens Programadas",
        lembretes: "Lembretes",
        chatInterno: "Chat interno",
        equipe: "Equipe",
        automacao: "Automação",
        feedbacks: "Feedbacks",
        administracao: "Administração",
      },
    },
    administracao: {},
    rodape: {
      trocarConta: "Trocar conta",
      trocarSenha: "Trocar senha",
      sair: "Sair",
      presenca: { rotulo: "Status de presença", online: "Online", ausente: "Ausente", offline: "Offline" },
    },
    configuracoes: { abrir: "Abrir configurações" },
    novidades: textosNovidades.titulo ? { titulo: textosNovidades.titulo } : undefined,
  }),
}));

vi.mock("./novidades-dialog", () => ({
  NovidadesDialog: ({ aberto }: { aberto: boolean }) =>
    aberto ? <div data-testid="novidades-dialog">dialog</div> : null,
}));

import { Sidebar } from "./sidebar";

function renderSidebar() {
  const cliente = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  function SidebarControlada() {
    const expansao = useExpansaoDaSidebar();
    return (
      <div
        data-testid="sidebar-slot"
        data-slot="sidebar-slot"
        style={estiloDaLarguraDoSlot(expansao.fixada)}
      >
        <Sidebar
          retraida={!expansao.expandida}
          fixada={expansao.fixada}
          onAlternar={expansao.alternarFixacao}
          onPonteiroEntrar={expansao.aoPonteiroEntrar}
          onPonteiroSair={expansao.aoPonteiroSair}
          onFocoDentro={expansao.aoFocoDentro}
          onFocoFora={expansao.aoFocoFora}
        />
      </div>
    );
  }
  return render(
    <QueryClientProvider client={cliente}>
      <SidebarControlada />
    </QueryClientProvider>,
  );
}

describe("sidebar", () => {
  afterEach(() => {
    vi.useRealTimers();
  });

  beforeEach(() => {
    authMock.papel = "ATENDENTE";
    textosNovidades.titulo = undefined;
    fetchMock.mockReset();
    fetchMock.mockResolvedValue(
      new Response(JSON.stringify(["dashboard", "banco_arquivos"]), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      }),
    );
    contagemMock.mockReturnValue({ data: { PENDENTES: 7 } });
    vi.stubGlobal("fetch", fetchMock);
  });

  it("mostra a marca, os grupos MENU/GESTÃO na ordem do protótipo, e some com o que a flag desliga", async () => {
    renderSidebar();

    expect(screen.getByText("Estrutural Vidros")).toBeInTheDocument();
    expect(await screen.findByText("Menu")).toBeInTheDocument();
    expect(screen.queryByText("Gestão")).not.toBeInTheDocument();

    // dashboard/banco_arquivos vieram habilitados no mock de features; campanhas/horarios/relatorios não.
    expect(await screen.findByText("Agenda de Contatos")).toBeInTheDocument();
    expect(screen.queryByText("Dashboard")).not.toBeInTheDocument();
    expect(screen.queryByText("Campanhas")).not.toBeInTheDocument();
  });

  it("mantém o conteúdo da barra lateral sem overflow horizontal", () => {
    renderSidebar();

    expect(screen.getByRole("complementary")).toHaveClass("overflow-x-hidden");
  });

  it("esconde Equipe para quem não é GESTOR/ADMINISTRADOR", async () => {
    renderSidebar();

    await screen.findByText("Agenda de Contatos");
    expect(screen.queryByText("Equipe")).not.toBeInTheDocument();
  });

  it("esconde Automação para ATENDENTE", async () => {
    renderSidebar();

    await screen.findByText("Agenda de Contatos");
    expect(screen.queryByText("Automação")).not.toBeInTheDocument();
  });

  it("mostra o chat interno quando a flag está habilitada", async () => {
    fetchMock.mockResolvedValueOnce(
      new Response(JSON.stringify(["chat_interno"]), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      }),
    );

    renderSidebar();

    const chat = await screen.findByRole("link", { name: "Chat interno" });
    expect(chat).toHaveAttribute("href", "/chat-interno");
  });

  it("oferece Feedbacks para qualquer papel autenticado", async () => {
    renderSidebar();

    expect(await screen.findByRole("link", { name: "Feedbacks" })).toHaveAttribute(
      "href",
      "/feedbacks",
    );
  });

  it("mostra Administração somente ao ADMINISTRADOR, sem selo adicional", async () => {
    authMock.papel = "GESTOR";
    const telaGestor = renderSidebar();
    await screen.findByText("Feedbacks");
    expect(screen.queryByText("Administração")).not.toBeInTheDocument();
    telaGestor.unmount();

    authMock.papel = "SUBGESTOR";
    const telaSub = renderSidebar();
    await screen.findByText("Feedbacks");
    expect(screen.queryByText("Administração")).not.toBeInTheDocument();
    telaSub.unmount();

    authMock.papel = "ATENDENTE";
    const telaAtendente = renderSidebar();
    await screen.findByText("Feedbacks");
    expect(screen.queryByText("Administração")).not.toBeInTheDocument();
    telaAtendente.unmount();

    authMock.papel = "ADMINISTRADOR";
    renderSidebar();
    const administracao = await screen.findByRole("link", { name: "Administração" });
    expect(administracao).toHaveAttribute("href", "/administracao");
    expect(administracao).toHaveAttribute("title", "Administração");
    expect(screen.queryByText("Acesso restrito")).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "Fixar menu aberto" }));
    expect(screen.getByRole("link", { name: "Administração" })).toHaveAttribute("title", "Administração");
    expect(screen.queryByText("Acesso restrito")).not.toBeInTheDocument();
  });

  it("oferece engrenagem de configurações separada do popup de presença", async () => {
    renderSidebar();

    expect(await screen.findByRole("link", { name: "Abrir configurações" })).toHaveAttribute(
      "href",
      "/configuracoes",
    );
    expect(screen.getByRole("button", { name: "Status de presença: Online" })).toBeInTheDocument();
  });

  it("inicia retraida e preserva links, badge, presença, configurações e logout nos dois estados", async () => {
    renderSidebar();

    const sidebar = screen.getByRole("complementary");
    const slot = screen.getByTestId("sidebar-slot");
    const fixar = await screen.findByRole("button", { name: "Fixar menu aberto" });
    expect(slot).toHaveStyle({ width: `${EXPANSAO_DA_SIDEBAR.larguraRetraidaPx}px` });
    expect(fixar).toHaveAttribute("aria-pressed", "false");
    expect(fixar).toHaveAttribute("aria-expanded", "false");
    expect(screen.getByText("Estrutural Vidros")).toHaveStyle({ opacity: 0 });
    expect(screen.getByRole("link", { name: "Atendimentos, 7 pendentes" })).toHaveAttribute(
      "href",
      "/atendimentos",
    );
    expect(screen.getByRole("link", { name: "Agenda de Contatos" })).toHaveAttribute(
      "title",
      "Agenda de Contatos",
    );
    expect(screen.getByRole("link", { name: "Abrir configurações" })).toBeInTheDocument();

    vi.useFakeTimers();
    fireEvent.mouseEnter(sidebar);
    expect(sidebar).toHaveAttribute("data-state", "collapsed");
    act(() => vi.advanceTimersByTime(EXPANSAO_DA_SIDEBAR.intencaoAbrirMs));
    expect(sidebar).toHaveAttribute("data-state", "expanded");
    expect(slot).toHaveStyle({ width: `${EXPANSAO_DA_SIDEBAR.larguraRetraidaPx}px` });
    expect(screen.getByText("Estrutural Vidros")).toBeVisible();

    fireEvent.click(fixar);
    expect(sidebar).toHaveAttribute("data-fixada", "true");
    expect(screen.getByRole("button", { name: "Desafixar menu lateral" })).toHaveAttribute(
      "aria-pressed",
      "true",
    );

    fireEvent.click(screen.getByRole("button", { name: "Status de presença: Online" }));
    expect(screen.getByRole("button", { name: "Sair" })).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Status de presença: Online" }));

    fireEvent.mouseLeave(sidebar);
    act(() => vi.advanceTimersByTime(EXPANSAO_DA_SIDEBAR.atrasoFecharMs));
    expect(slot).toHaveStyle({ width: `${EXPANSAO_DA_SIDEBAR.larguraExpandidaPx}px` });

    fireEvent.click(screen.getByRole("button", { name: "Desafixar menu lateral" }));
    expect(slot).toHaveStyle({ width: `${EXPANSAO_DA_SIDEBAR.larguraRetraidaPx}px` });
    expect(screen.getByRole("button", { name: "Fixar menu aberto" })).toHaveAttribute(
      "aria-pressed",
      "false",
    );
  });

  it("hover temporario sobrepoe em absolute e o slot permanece 76px", async () => {
    renderSidebar();

    const sidebar = screen.getByRole("complementary");
    const slot = screen.getByTestId("sidebar-slot");
    await screen.findByRole("button", { name: "Fixar menu aberto" });

    vi.useFakeTimers();
    fireEvent.mouseEnter(sidebar);
    act(() => vi.advanceTimersByTime(EXPANSAO_DA_SIDEBAR.intencaoAbrirMs));

    expect(sidebar).toHaveAttribute("data-state", "expanded");
    expect(sidebar).toHaveAttribute("data-sobreposta", "true");
    expect(slot).toHaveStyle({ width: `${EXPANSAO_DA_SIDEBAR.larguraRetraidaPx}px` });
    expect(sidebar.className).toMatch(/\babsolute\b/);
    expect(sidebar.className).toMatch(/inset-y-0/);
    expect(sidebar.className).toMatch(/\bz-40\b/);
  });

  it("mostra Dashboard para ADMINISTRADOR quando a feature está habilitada", async () => {
    authMock.papel = "ADMINISTRADOR";

    renderSidebar();

    expect(await screen.findByText("Dashboard")).toBeInTheDocument();
  });

  it("abre o popup de presença e troca o status, sem select nativo", async () => {
    renderSidebar();

    fireEvent.click(await screen.findByRole("button", { name: "Status de presença: Online" }));

    const opcaoAusente = await screen.findByText("Ausente");
    fireEvent.click(opcaoAusente);

    await waitFor(() => expect(atualizarPresencaMock).toHaveBeenCalledWith("AUSENTE"));
  });

  it("mantem o menu central navegavel quando o endpoint de features responde 500", async () => {
    fetchMock.mockResolvedValueOnce(
      new Response(JSON.stringify({ title: "Falha nas features" }), {
        status: 500,
        headers: { "Content-Type": "application/problem+json" },
      }),
    );

    renderSidebar();

    expect(await screen.findByRole("alert")).toHaveTextContent("Erro.");
    const agenda = screen.getByRole("link", { name: "Agenda de Contatos" });
    expect(agenda).toHaveAttribute("href", "/agenda");
    expect(screen.getByRole("link", { name: /Atendimentos/ })).toHaveAttribute(
      "href",
      "/atendimentos",
    );
    expect(screen.queryByText("Dashboard")).not.toBeInTheDocument();
  });

  it("mostra no menu somente a contagem de pendentes", async () => {
    renderSidebar();

    expect(await screen.findByRole("link", { name: /Atendimentos/ })).toHaveTextContent("7");
  });

  it("não mostra o botão de novidades quando o título está ausente nos textos", async () => {
    renderSidebar();

    await screen.findByText("Agenda de Contatos");
    expect(screen.queryByRole("button", { name: ROTULO_NOVIDADES_TESTE })).not.toBeInTheDocument();
    expect(screen.queryByTestId("novidades-dialog")).not.toBeInTheDocument();
  });

  it("mostra o botão de novidades e abre o dialog quando o título existe", async () => {
    textosNovidades.titulo = ROTULO_NOVIDADES_TESTE;
    renderSidebar();

    const botao = await screen.findByRole("button", { name: ROTULO_NOVIDADES_TESTE });
    expect(botao).toHaveAttribute("title", ROTULO_NOVIDADES_TESTE);
    fireEvent.click(botao);
    expect(screen.getByTestId("novidades-dialog")).toBeInTheDocument();
  });

  it("omite o badge quando a contagem falha", async () => {
    contagemMock.mockReturnValue({ data: undefined, isError: true });

    renderSidebar();

    const link = await screen.findByRole("link", { name: "Atendimentos" });
    expect(link).not.toHaveTextContent("7");
  });

});
