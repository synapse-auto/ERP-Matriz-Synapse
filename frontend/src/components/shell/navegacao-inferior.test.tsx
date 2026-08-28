import { fireEvent, render, screen } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { beforeEach, describe, expect, it, vi } from "vitest";

const authMock = vi.hoisted(() => ({
  papel: "ATENDENTE",
  accessToken: "token-de-teste",
  limparSessao: vi.fn(),
}));
const fetchMock = vi.hoisted(() => vi.fn());
const contagemMock = vi.hoisted(() => vi.fn());

vi.mock("next/navigation", () => ({
  usePathname: () => "/atendimentos",
}));

vi.mock("@/lib/auth/auth-store", () => ({
  useAuthStore: Object.assign(
    (seletor: (estado: typeof authMock) => unknown) => seletor(authMock),
    { getState: () => authMock },
  ),
}));

vi.mock("@/lib/equipe/api", () => ({
  atualizarPresenca: vi.fn(),
}));

vi.mock("@/lib/equipe/use-equipe", () => ({
  useMeuUsuario: () => ({ data: { id: "ana", nome: "Ana Beatriz", papel: "ATENDENTE", presenca: "ONLINE" } }),
}));

vi.mock("@/lib/atendimento/use-atendimentos", () => ({
  useContagemDeAtendimentos: () => contagemMock(),
}));

vi.mock("@/lib/config/textos-provider", () => ({
  useTextos: () => ({
    menu: {
      grupoMenu: "Menu",
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
        feedbacks: "Feedbacks",
        equipe: "Equipe",
        automacao: "Automação",
        administracao: "Administração",
      },
    },
    rodape: {
      trocarSenha: "Trocar senha",
      sair: "Sair",
      presenca: { rotulo: "Presença", online: "Online", ausente: "Ausente", offline: "Offline" },
    },
    configuracoes: { abrir: "Abrir configurações" },
  }),
}));

vi.mock("./novidades-dialog", () => ({
  NovidadesDialog: () => null,
}));

import { NavegacaoInferior } from "./navegacao-inferior";

function renderizar() {
  const cliente = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={cliente}>
      <NavegacaoInferior />
    </QueryClientProvider>,
  );
}

describe("NavegacaoInferior", () => {
  beforeEach(() => {
    authMock.papel = "ATENDENTE";
    fetchMock.mockReset();
    fetchMock.mockResolvedValue(
      new Response(JSON.stringify(["dashboard"]), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      }),
    );
    contagemMock.mockReturnValue({ data: { PENDENTES: 6 } });
    vi.stubGlobal("fetch", fetchMock);
  });

  it("não oferece Dashboard ao atendente, mesmo com a flag ligada", async () => {
    renderizar();

    expect(await screen.findByRole("link", { name: /Atendimentos/ })).toHaveAttribute(
      "href",
      "/atendimentos",
    );
    expect(screen.getByRole("link", { name: "Agenda de Contatos" })).toHaveAttribute(
      "href",
      "/agenda",
    );
    expect(screen.queryByRole("link", { name: "Dashboard" })).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Mais" })).toBeInTheDocument();
  });

  it("mostra Dashboard para o gestor quando a feature está habilitada", async () => {
    authMock.papel = "GESTOR";
    renderizar();

    expect(await screen.findByRole("link", { name: "Dashboard" })).toHaveAttribute(
      "href",
      "/dashboard",
    );
  });

  it("abre o menu Mais com as rotas que não cabem na barra", async () => {
    renderizar();

    fireEvent.click(await screen.findByRole("button", { name: "Mais" }));

    expect(screen.getByRole("link", { name: "Tags" })).toHaveAttribute("href", "/tags");
    expect(screen.getByRole("link", { name: "Lembretes" })).toHaveAttribute("href", "/lembretes");
    expect(screen.getByRole("link", { name: "Abrir configurações" })).toHaveAttribute(
      "href",
      "/configuracoes",
    );
    expect(screen.queryByRole("link", { name: "Equipe" })).not.toBeInTheDocument();
  });
});
