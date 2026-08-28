import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

const cancelarMutate = vi.fn();

const MENSAGENS = {
  mensagens: [
    {
      id: "p1",
      leadId: "l1",
      leadNome: "Marcos Vinícius",
      atendenteId: "u1",
      atendenteNome: "Ana Beatriz",
      conteudo: "Segue o orçamento revisado, qualquer dúvida me chama!",
      dataEnvio: "2026-01-10T08:05:00Z",
      status: "AGENDADA",
    },
    {
      id: "p2",
      leadId: "l2",
      leadNome: "Camila Nunes",
      atendenteId: "u1",
      atendenteNome: "Ana Beatriz",
      conteudo: "Já confirmamos a instalação para sexta-feira.",
      dataEnvio: "2026-01-05T09:00:00Z",
      status: "ENVIADA",
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
    mensagensProgramadas: {
      titulo: "Mensagens programadas",
      descricao: "Mensagens que a Automação enviará no horário escolhido.",
      nova: "Programar mensagem",
      carregando: "Carregando mensagens...",
      vazio: "Nenhuma mensagem neste período.",
      erro: "Não foi possível carregar as mensagens programadas.",
      editar: "Editar",
      cancelar: "Cancelar",
      status: { agendada: "Agendada", enviada: "Enviada", cancelada: "Cancelada" },
      filtros: { inicio: "De", fim: "Até", status: "Status", todos: "Todos" },
      colunas: {
        lead: "Lead",
        conteudo: "Mensagem",
        data: "Envio",
        status: "Status",
        atendente: "Atendente",
        acoes: "Ações",
      },
      paginacao: { anterior: "Anterior", proxima: "Próxima" },
      formulario: {
        tituloCriar: "Programar mensagem",
        tituloEditar: "Editar mensagem programada",
        lead: "Lead",
        selecionarLead: "Selecione um lead",
        dataEnvio: "Data e hora do envio",
        conteudo: "Mensagem",
        salvar: "Salvar",
        cancelar: "Voltar",
        erro: "Não foi possível salvar a mensagem programada.",
      },
    },
  }),
}));

vi.mock("@/lib/suporte/api", () => ({
  listarMensagensProgramadas: () => Promise.resolve(MENSAGENS),
  cancelarMensagemProgramada: (...args: unknown[]) => {
    cancelarMutate(...args);
    return Promise.resolve();
  },
  criarMensagemProgramada: vi.fn(),
  editarMensagemProgramada: vi.fn(),
}));

vi.mock("@/lib/atendimento/api", () => ({ listarAtendimentos: vi.fn(() => Promise.resolve([])) }));

import { QueryClient, QueryClientProvider } from "@tanstack/react-query";

import { PaginaMensagensProgramadas } from "./pagina-mensagens-programadas";

function renderComQuery() {
  const cliente = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={cliente}>
      <PaginaMensagensProgramadas />
    </QueryClientProvider>,
  );
}

describe("pagina de mensagens programadas", () => {
  it("lista as mensagens como cards, com lead, status e atendente", async () => {
    renderComQuery();

    expect(await screen.findByText("Marcos Vinícius")).toBeInTheDocument();
    expect(screen.getByText("Camila Nunes")).toBeInTheDocument();
    expect(screen.getAllByText("Agendada").length).toBeGreaterThan(0);
    expect(screen.getAllByText("Enviada").length).toBeGreaterThan(0);
    expect(screen.getAllByText("Ana Beatriz").length).toBeGreaterThan(0);
    expect(screen.getByText("Marcos Vinícius").closest("tr")).toHaveClass(
      "border-primary/40",
      "bg-primary/15",
    );
    expect(screen.getByText("Camila Nunes").closest("tr")).toHaveClass(
      "border-border",
    );
    expect(screen.getByText("Camila Nunes").closest("tr")).not.toHaveClass("bg-primary/15");
  });

  it("so mostra editar/cancelar para mensagens agendadas", async () => {
    renderComQuery();

    await screen.findByText("Marcos Vinícius");
    expect(screen.getByRole("button", { name: "Cancelar Marcos Vinícius" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Cancelar Camila Nunes" })).not.toBeInTheDocument();
  });

  it("cancela uma mensagem agendada", async () => {
    renderComQuery();

    await screen.findByText("Marcos Vinícius");
    fireEvent.click(screen.getByRole("button", { name: "Cancelar Marcos Vinícius" }));

    await waitFor(() => expect(cancelarMutate).toHaveBeenCalledWith("p1", expect.anything()));
  });

  it("abre o formulário pela rota própria sem substituir a página", async () => {
    renderComQuery();

    fireEvent.click(screen.getByRole("button", { name: "Programar mensagem" }));

    expect(await screen.findByRole("dialog")).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Programar mensagem" })).toBeInTheDocument();
  });
});
