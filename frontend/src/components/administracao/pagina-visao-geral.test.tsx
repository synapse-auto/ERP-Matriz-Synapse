import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

const equipe = vi.hoisted(() => ({
  data: [
    { id: "u1", ativo: true, statusPresenca: "ONLINE" },
    { id: "u2", ativo: false, statusPresenca: "OFFLINE" },
  ] as Array<{ id: string; ativo: boolean; statusPresenca: string }>,
  isLoading: false,
  isError: false,
  refetch: vi.fn(),
}));

const features = vi.hoisted(() => ({
  data: ["dashboard", "chat"] as string[] | undefined,
  isLoading: false,
  isError: false,
  refetch: vi.fn(),
}));

vi.mock("@tanstack/react-query", () => ({
  useQuery: () => features,
}));

vi.mock("@/lib/equipe/use-equipe", () => ({
  useEquipe: () => equipe,
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
    equipe.data = [
      { id: "u1", ativo: true, statusPresenca: "ONLINE" },
      { id: "u2", ativo: false, statusPresenca: "OFFLINE" },
    ];
    features.data = ["dashboard", "chat"];
    render(<PaginaVisaoGeralAdministracao />);

    expect(screen.getByText("Usuários cadastrados").closest("article")?.querySelector(".text-cor-ia")).toBeTruthy();
    expect(screen.getByText("Usuários ativos").closest("article")?.querySelector(".text-cor-sucesso")).toBeTruthy();
    expect(screen.getByText("Usuários online").closest("article")?.querySelector(".text-cor-info")).toBeTruthy();
    expect(screen.getByText("Recursos habilitados").closest("article")?.querySelector(".text-cor-atencao")).toBeTruthy();
    expect(screen.getByText("Usuários cadastrados").previousElementSibling).toHaveTextContent("2");
    expect(screen.getByText("Usuários ativos").previousElementSibling).toHaveTextContent("1");
    expect(screen.getByText("Usuários cadastrados").closest("article")).toHaveClass("rounded-xl", "shadow-sm");
  });

  it("mostra zeros reais quando a fonte não devolve usuários nem recursos", () => {
    equipe.data = [];
    features.data = [];
    render(<PaginaVisaoGeralAdministracao />);

    expect(screen.getAllByText("0")).toHaveLength(4);
    expect(screen.queryByText("operacional")).not.toBeInTheDocument();
  });
});
