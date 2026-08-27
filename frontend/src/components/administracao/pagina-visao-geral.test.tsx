import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

vi.mock("@tanstack/react-query", () => ({
  useQuery: () => ({ data: ["dashboard", "chat"], isLoading: false, isError: false, refetch: vi.fn() }),
}));

vi.mock("@/lib/equipe/use-equipe", () => ({
  useEquipe: () => ({
    data: [
      { id: "u1", ativo: true, statusPresenca: "ONLINE" },
      { id: "u2", ativo: false, statusPresenca: "OFFLINE" },
    ],
    isLoading: false,
    isError: false,
    refetch: vi.fn(),
  }),
}));

vi.mock("@/lib/config/textos-provider", () => ({
  useTextos: () => ({
    administracao: {
      visaoGeral: {
        titulo: "Visão geral",
        descricao: "Resumo",
        carregando: "Carregando",
        erro: "Erro",
        usuarios: "Usuários cadastrados",
        ativos: "Usuários ativos",
        online: "Usuários online",
        recursos: "Recursos habilitados",
      },
    },
  }),
}));

import { PaginaVisaoGeralAdministracao } from "./pagina-visao-geral";

describe("PaginaVisaoGeralAdministracao", () => {
  it("diferencia os ícones de cada indicador com tokens semânticos", () => {
    render(<PaginaVisaoGeralAdministracao />);

    expect(screen.getByText("Usuários cadastrados").nextElementSibling).toHaveClass("text-cor-ia");
    expect(screen.getByText("Usuários ativos").nextElementSibling).toHaveClass("text-cor-sucesso");
    expect(screen.getByText("Usuários online").nextElementSibling).toHaveClass("text-cor-info");
    expect(screen.getByText("Recursos habilitados").nextElementSibling).toHaveClass("text-cor-atencao");
  });
});
