import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { describe, expect, it, vi } from "vitest";

vi.mock("next/navigation", () => ({
  usePathname: () => "/atendimentos",
}));

vi.mock("@/lib/auth/auth-store", () => ({
  useAuthStore: (seletor: (estado: { papel: string; email: string }) => unknown) =>
    seletor({ papel: "ATENDENTE", email: "ana@dev.local" }),
}));

vi.mock("@/lib/api/http-client", () => ({
  apiFetch: () => Promise.resolve(["dashboard", "banco_arquivos"]),
}));

const atualizarPresencaMock = vi.fn((_status: string) => Promise.resolve({ status: "AUSENTE" }));

vi.mock("@/lib/equipe/api", () => ({
  obterPresenca: () => Promise.resolve({ status: "ONLINE" }),
  atualizarPresenca: (status: string) => atualizarPresencaMock(status),
}));

vi.mock("@/lib/config/textos-provider", () => ({
  useTextos: () => ({
    app: { marca: "Estrutural Vidros", subtitulo: "CRM · Atendimento" },
    estados: { carregando: "Carregando...", erroGenerico: "Erro." },
    menu: {
      grupoMenu: "Menu",
      grupoGestao: "Gestão",
      itens: {
        atendimentos: "Atendimentos",
        agenda: "Agenda de Contatos",
        tags: "Tags",
        mensagensRapidas: "Mensagens Rápidas",
        mensagensProgramadas: "Mensagens Programadas",
        lembretes: "Lembretes",
        equipe: "Equipe",
        automacao: "Automação",
      },
    },
    rodape: {
      trocarConta: "Trocar conta",
      sair: "Sair",
      presenca: { rotulo: "Status de presença", online: "Online", ausente: "Ausente", offline: "Offline" },
    },
  }),
}));

import { Sidebar } from "./sidebar";

function renderSidebar() {
  const cliente = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={cliente}>
      <Sidebar />
    </QueryClientProvider>,
  );
}

describe("sidebar", () => {
  it("mostra a marca, os grupos MENU/GESTÃO na ordem do protótipo, e some com o que a flag desliga", async () => {
    renderSidebar();

    expect(screen.getByText("Estrutural Vidros")).toBeInTheDocument();
    expect(await screen.findByText("Menu")).toBeInTheDocument();
    expect(screen.getByText("Gestão")).toBeInTheDocument();

    // dashboard/banco_arquivos vieram habilitados no mock de features; campanhas/horarios/relatorios não.
    expect(await screen.findByText("Agenda de Contatos")).toBeInTheDocument();
    expect(screen.queryByText("Campanhas")).not.toBeInTheDocument();
  });

  it("esconde Equipe para quem não é GESTOR/ADMINISTRADOR", async () => {
    renderSidebar();

    await screen.findByText("Agenda de Contatos");
    expect(screen.queryByText("Equipe")).not.toBeInTheDocument();
  });

  it("abre o popup de presença e troca o status, sem select nativo", async () => {
    renderSidebar();

    const botaoConta = await screen.findByText("ana@dev.local");
    fireEvent.click(botaoConta.closest("button")!);

    const opcaoAusente = await screen.findByText("Ausente");
    fireEvent.click(opcaoAusente);

    await waitFor(() => expect(atualizarPresencaMock).toHaveBeenCalledWith("AUSENTE"));
  });
});
