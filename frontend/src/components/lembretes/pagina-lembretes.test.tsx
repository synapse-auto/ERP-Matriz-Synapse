import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

const atualizarMutate = vi.fn();
const removerMutate = vi.fn();
const listarLembretesMock = vi.fn();

const LEMBRETES = {
  lembretes: [
    {
      id: "r1",
      leadId: "l1",
      leadNome: "Marcos Vinícius",
      atendenteId: "u1",
      atendenteNome: "Ana Beatriz",
      texto: "Retomar atendimento transferido",
      dataHora: "2026-01-10T08:05:00Z",
      origemAutomatica: true,
      status: "PENDENTE",
    },
    {
      id: "r2",
      leadId: "l2",
      leadNome: null,
      atendenteId: "u1",
      atendenteNome: "Ana Beatriz",
      texto: "Revisar tabela de preços",
      dataHora: "2026-01-11T09:00:00Z",
      origemAutomatica: false,
      status: "CONCLUIDO",
    },
  ],
  pagina: 0,
  temMais: false,
};

vi.mock("@/lib/auth/auth-store", () => ({
  useAuthStore: (selector: (s: { papel: string }) => unknown) => selector({ papel: "GESTOR" }),
}));

vi.mock("@/lib/config/textos-provider", () => ({
  useTextos: () => ({
    lembretes: {
      titulo: "Lembretes",
      descricao: "Compromissos pessoais ligados aos seus leads.",
      novo: "Novo lembrete",
      carregando: "Carregando lembretes...",
      vazio: "Nenhum lembrete neste período.",
      erro: "Não foi possível carregar os lembretes.",
      automatico: "Automático",
      concluir: "Concluir",
      remover: "Remover",
      status: { pendente: "Pendente", concluido: "Concluído" },
      filtros: { inicio: "De", fim: "Até", status: "Status", todos: "Todos" },
      semVinculo: "Sem vínculo",
      paginacao: { anterior: "Anterior", proxima: "Próxima" },
      formulario: {
        titulo: "Criar lembrete",
        lead: "Lead",
        selecionarLead: "Selecione um lead",
        dataHora: "Data e hora",
        texto: "Texto",
        salvar: "Salvar",
        cancelar: "Cancelar",
        erro: "Não foi possível criar o lembrete.",
      },
    },
  }),
}));

vi.mock("@/lib/suporte/api", () => ({
  listarLembretes: (...args: unknown[]) => {
    listarLembretesMock(...args);
    return Promise.resolve(LEMBRETES);
  },
  atualizarLembrete: (...args: unknown[]) => {
    atualizarMutate(...args);
    return Promise.resolve();
  },
  removerLembrete: (...args: unknown[]) => {
    removerMutate(...args);
    return Promise.resolve();
  },
  criarLembrete: vi.fn(),
}));

vi.mock("@/lib/atendimento/api", () => ({ listarAtendimentos: vi.fn(() => Promise.resolve([])) }));

import { QueryClient, QueryClientProvider } from "@tanstack/react-query";

import { PaginaLembretes } from "./pagina-lembretes";

function renderComQuery() {
  const cliente = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={cliente}>
      <PaginaLembretes />
    </QueryClientProvider>,
  );
}

describe("pagina de lembretes", () => {
  it("lista os lembretes como cards, com lead, status e badge de automático", async () => {
    renderComQuery();

    expect(await screen.findByText("Retomar atendimento transferido")).toBeInTheDocument();
    expect(screen.getByText("Marcos Vinícius")).toBeInTheDocument();
    expect(screen.getByText("Automático")).toBeInTheDocument();
    expect(screen.getAllByText("Pendente").length).toBeGreaterThan(0);
    expect(screen.getAllByText("Concluído").length).toBeGreaterThan(0);
    expect(screen.getByText("Sem vínculo")).toBeInTheDocument();
  });

  it("conclui um lembrete pendente", async () => {
    renderComQuery();

    await screen.findByText("Retomar atendimento transferido");
    const botoesConcluir = screen.getAllByRole("button", { name: "Concluir" });
    fireEvent.click(botoesConcluir[0]);

    await waitFor(() => expect(atualizarMutate).toHaveBeenCalled());
  });

  it("remove um lembrete", async () => {
    renderComQuery();

    await screen.findByText("Retomar atendimento transferido");
    fireEvent.click(
      screen.getByRole("button", { name: "Remover Retomar atendimento transferido" }),
    );

    await waitFor(() => expect(removerMutate).toHaveBeenCalledWith("r1", expect.anything()));
  });
});
