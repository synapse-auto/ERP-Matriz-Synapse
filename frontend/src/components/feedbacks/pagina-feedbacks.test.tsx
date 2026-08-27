import { act, fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

const mutate = vi.hoisted(() => vi.fn());

vi.mock("@/lib/feedbacks/use-feedbacks", () => ({
  useEnviarFeedback: () => ({ mutate, isPending: false, isError: false }),
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

import { PaginaFeedbacks } from "./pagina-feedbacks";

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
    render(<PaginaFeedbacks />);
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
    render(<PaginaFeedbacks />);
    preencher();

    fireEvent.click(screen.getByRole("button", { name: "Enviar feedback" }));

    expect(screen.getByPlaceholderText("Descreva")).toHaveValue("O filtro não abriu");
    expect(screen.queryByRole("button", { name: /anexo/i })).not.toBeInTheDocument();
    expect(screen.queryByLabelText(/arquivo/i)).not.toBeInTheDocument();
  });
});
