import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { useState } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";

const authMock = vi.hoisted(() => ({
  papel: "ATENDENTE",
  accessToken: "token-de-teste",
  limparSessao: vi.fn(),
  definirSessao: vi.fn(),
}));
const fetchMock = vi.hoisted(() => vi.fn());
const contagemMock = vi.hoisted(() => vi.fn());

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
      contagemPendentes: "Atendimentos, {quantidade} pendentes",
      itens: {
        atendimentos: "Atendimentos",
        dashboard: "Dashboard",
        agenda: "Agenda de Contatos",
        tags: "Tags",
        mensagensRapidas: "Mensagens Rápidas",
        mensagensProgramadas: "Mensagens Programadas",
        lembretes: "Lembretes",
        equipe: "Equipe",
        automacao: "Automação",
        feedbacks: "Feedbacks",
        administracao: "Administração",
      },
    },
    administracao: { restrito: "Acesso restrito" },
    rodape: {
      trocarConta: "Trocar conta",
      trocarSenha: "Trocar senha",
      sair: "Sair",
      presenca: { rotulo: "Status de presença", online: "Online", ausente: "Ausente", offline: "Offline" },
    },
    configuracoes: { abrir: "Abrir configurações" },
  }),
}));

import { Sidebar } from "./sidebar";

function renderSidebar() {
  const cliente = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  function SidebarControlada() {
    const [retraida, setRetraida] = useState(false);
    return <Sidebar retraida={retraida} onAlternar={() => setRetraida((atual) => !atual)} />;
  }
  return render(
    <QueryClientProvider client={cliente}>
      <SidebarControlada />
    </QueryClientProvider>,
  );
}

describe("sidebar", () => {
  beforeEach(() => {
    authMock.papel = "ATENDENTE";
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

  it("oferece Feedbacks para qualquer papel autenticado", async () => {
    renderSidebar();

    expect(await screen.findByRole("link", { name: "Feedbacks" })).toHaveAttribute(
      "href",
      "/feedbacks",
    );
  });

  it("mostra Administração somente ao ADMINISTRADOR", async () => {
    authMock.papel = "GESTOR";
    const tela = renderSidebar();
    await screen.findByText("Feedbacks");
    expect(screen.queryByText("Administração")).not.toBeInTheDocument();

    tela.unmount();
    authMock.papel = "ADMINISTRADOR";
    renderSidebar();
    expect(await screen.findByRole("link", { name: /Administração/ })).toHaveAttribute(
      "href",
      "/administracao",
    );
    expect(screen.getByText("Acesso restrito")).toHaveClass("text-cor-ia");
  });

  it("oferece engrenagem de configurações separada do popup de presença", async () => {
    renderSidebar();

    expect(await screen.findByRole("link", { name: "Abrir configurações" })).toHaveAttribute(
      "href",
      "/configuracoes",
    );
    expect(screen.getByRole("button", { name: "Status de presença: Online" })).toBeInTheDocument();
  });

  it("retrai e reabre preservando links, badge, presença, configurações e logout", async () => {
    renderSidebar();

    const sidebar = screen.getByRole("complementary");
    const retrair = await screen.findByRole("button", { name: "Retrair menu lateral" });
    expect(sidebar).toHaveClass("w-[260px]");
    expect(retrair).toHaveAttribute("aria-expanded", "true");
    expect(screen.getByText("Estrutural Vidros")).toBeVisible();

    fireEvent.click(retrair);

    expect(sidebar).toHaveClass("w-[76px]");
    const reabrir = screen.getByRole("button", { name: "Reabrir menu lateral" });
    expect(reabrir).toHaveAttribute("aria-expanded", "false");
    expect(screen.getByRole("link", { name: "Atendimentos, 7 pendentes" })).toHaveAttribute(
      "href",
      "/atendimentos",
    );
    expect(screen.getByRole("link", { name: "Agenda de Contatos" })).toHaveAttribute(
      "title",
      "Agenda de Contatos",
    );
    expect(screen.getByRole("link", { name: "Abrir configurações" })).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "Status de presença: Online" }));
    expect(await screen.findByRole("button", { name: "Sair" })).toBeInTheDocument();

    fireEvent.click(reabrir);
    expect(sidebar).toHaveClass("w-[260px]");
    expect(screen.getByText("Estrutural Vidros")).toBeVisible();
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

  it("omite o badge quando a contagem falha", async () => {
    contagemMock.mockReturnValue({ data: undefined, isError: true });

    renderSidebar();

    const link = await screen.findByRole("link", { name: "Atendimentos" });
    expect(link).not.toHaveTextContent("7");
  });

});
