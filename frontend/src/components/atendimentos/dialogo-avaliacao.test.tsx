import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

const registrar = vi.fn();

vi.mock("@/lib/atendimento/use-avaliacao", () => ({
  useAvaliacaoDoAtendimento: () => ({ data: null, isLoading: false }),
  useRegistrarAvaliacao: () => ({ mutate: registrar, isPending: false, isError: false }),
}));

vi.mock("@/lib/config/textos-provider", () => ({
  useTextos: () => ({
    atendimentos: {
      avaliacao: {
        titulo: "Avaliação do atendimento",
        descricao: "Registre a nota de 1 a 5.",
        registrar: "Avaliar",
        confirmar: "Salvar avaliação",
        cancelar: "Agora não",
        jaRegistrada: "Avaliação registrada: {nota}/5",
        sucesso: "Avaliação registrada.",
        erro: "Erro",
        nota: "Nota {nota}",
      },
    },
  }),
}));

import { DialogoAvaliacao } from "./dialogo-avaliacao";

describe("DialogoAvaliacao", () => {
  it("grava a nota escolhida na escala 1 a 5", () => {
    render(<DialogoAvaliacao atendimentoId="atendimento-1" aberto onFechar={vi.fn()} />);

    fireEvent.click(screen.getByRole("button", { name: "Nota 4" }));
    fireEvent.click(screen.getByRole("button", { name: "Salvar avaliação" }));

    expect(registrar).toHaveBeenCalledWith(4, expect.any(Object));
  });
});
