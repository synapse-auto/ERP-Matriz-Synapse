import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { describe, expect, it, vi } from "vitest";

const mutate = vi.hoisted(() => vi.fn());
const papelAtual = vi.hoisted(() => ({ papel: "ATENDENTE" }));
const flags = vi.hoisted(() => ["dashboard"] as string[]);

vi.mock("@/lib/feedbacks/use-feedbacks", () => ({
  useEnviarFeedback: () => ({ mutate, isPending: false, isError: false }),
}));

vi.mock("@/lib/auth/auth-store", () => ({
  useAuthStore: (seletor: (estado: { papel: string }) => unknown) => seletor(papelAtual),
}));

vi.mock("@/lib/api/http-client", () => ({
  apiFetch: vi.fn(async () => flags),
}));

vi.mock("@/lib/config/textos-provider", () => ({
  useTextos: () => ({
    feedbacks: {
      titulo: "Envie seu feedback",
      descricao: "Ajude a melhorar",
      tipo: "Tipo de feedback",
      tipos: { sugestao: "Sugestão", erro: "Erro" },
      area: "Área do sistema",
      areaPlaceholder: "Selecione uma área",
      areas: {
        geral: "Nenhuma aba específica",
        atendimentos: "Atendimentos",
        agenda: "Agenda",
        dashboard: "Dashboard",
        equipe: "Equipe",
        automacao: "Automação",
        mensagensProgramadas: "Mensagens programadas",
        lembretes: "Lembretes",
        tags: "Tags",
        configuracoes: "Configurações",
      },
      descricaoCampo: "Descrição",
      descricaoPlaceholder: "Descreva",
      limite: "{atual} de {maximo} caracteres",
      enviar: "Enviar feedback",
      enviando: "Enviando...",
      sucesso: "Feedback enviado.",
      erro: "Falha preservada.",
      obrigatorio: "Campos obrigatórios.",
    },
  }),
}));

import { apiFetch } from "@/lib/api/http-client";
import { PaginaFeedbacks } from "./pagina-feedbacks";

function renderizar(papel = "ATENDENTE") {
  papelAtual.papel = papel;
  const cliente = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={cliente}>
      <PaginaFeedbacks />
    </QueryClientProvider>,
  );
}

async function abrirAreas() {
  await waitFor(() => expect(apiFetch).toHaveBeenCalled());
  fireEvent.click(screen.getByRole("combobox"));
  await waitFor(() => expect(screen.getByRole("listbox")).toBeInTheDocument());
}

function preencher() {
  fireEvent.change(screen.getByPlaceholderText("Descreva"), {
    target: { value: "O filtro não abriu" },
  });
}

describe("PaginaFeedbacks", () => {
  it("preserva o formulário até o servidor confirmar e limpa somente após sucesso", async () => {
    let sucesso: (() => void) | undefined;
    mutate.mockImplementation((_dados, opcoes) => {
      sucesso = opcoes.onSuccess;
    });
    renderizar();
    preencher();

    fireEvent.click(screen.getByRole("button", { name: "Enviar feedback" }));
    expect(screen.getByPlaceholderText("Descreva")).toHaveValue("O filtro não abriu");
    expect(mutate).toHaveBeenCalledWith(
      { tipo: "SUGESTAO", areaChave: "GERAL", descricao: "O filtro não abriu" },
      expect.objectContaining({ onSuccess: expect.any(Function) }),
    );

    act(() => sucesso?.());
    expect(screen.getByPlaceholderText("Descreva")).toHaveValue("");
    expect(screen.getByText("Feedback enviado.")).toBeInTheDocument();
  });

  it("mantém os dados quando não recebe confirmação e não oferece anexo fantasma", async () => {
    mutate.mockImplementation(() => undefined);
    renderizar();
    preencher();

    fireEvent.click(screen.getByRole("button", { name: "Enviar feedback" }));

    expect(screen.getByPlaceholderText("Descreva")).toHaveValue("O filtro não abriu");
    expect(screen.queryByRole("button", { name: /anexo/i })).not.toBeInTheDocument();
    expect(screen.queryByLabelText(/arquivo/i)).not.toBeInTheDocument();
  });

  it("esconde do atendente as áreas que ele não alcança no menu, mesmo com a flag ligada", async () => {
    renderizar("ATENDENTE");
    await abrirAreas();

    expect(screen.getByRole("option", { name: "Atendimentos" })).toBeInTheDocument();
    expect(screen.getByRole("option", { name: "Configurações" })).toBeInTheDocument();
    expect(screen.queryByRole("option", { name: "Dashboard" })).not.toBeInTheDocument();
    expect(screen.queryByRole("option", { name: "Equipe" })).not.toBeInTheDocument();
    expect(screen.queryByRole("option", { name: "Automação" })).not.toBeInTheDocument();
  });

  it("mostra Dashboard e Equipe para o gestor quando a feature está habilitada", async () => {
    renderizar("GESTOR");
    await abrirAreas();

    expect(screen.getByRole("option", { name: "Dashboard" })).toBeInTheDocument();
    expect(screen.getByRole("option", { name: "Equipe" })).toBeInTheDocument();
    expect(screen.getByRole("option", { name: "Automação" })).toBeInTheDocument();
  });
});
