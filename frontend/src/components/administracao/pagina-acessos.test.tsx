import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

vi.mock("@/components/equipe/pagina-equipe", () => ({
  Formulario: () => null,
  SenhaProvisoriaDialog: () => null,
}));

vi.mock("@/lib/equipe/use-equipe", () => ({
  useEquipe: () => ({
    data: [
      {
        id: "admin-1",
        nome: "Marina Admin",
        email: "marina@example.com",
        papel: "ADMINISTRADOR",
        statusPresenca: "ONLINE",
        ativo: true,
        fotoUrl: null,
      },
    ],
    isLoading: false,
    isError: false,
    refetch: vi.fn(),
  }),
  useDesativarUsuario: () => ({ mutate: vi.fn(), isPending: false }),
  useGerarSenhaProvisoria: () => ({ mutate: vi.fn(), isError: false }),
}));

vi.mock("@/lib/config/textos-provider", () => ({
  useTextos: () => ({
    administracao: {
      acessos: {
        titulo: "Acessos",
        descricao: "Usuários reais",
        carregando: "Carregando...",
        erro: "Erro",
        vazio: "Vazio",
        novo: "Novo usuário",
        usuario: "Usuário",
        papel: "Papel",
        presenca: "Presença",
        situacao: "Situação",
        acoes: "Ações",
        ativo: "Ativo",
        inativo: "Inativo",
        editar: "Editar",
        senha: "Gerar senha provisória",
        desativar: "Desativar",
        papeis: {
          ATENDENTE: "Atendente",
          SUBGESTOR: "Subgestor",
          GESTOR: "Gestor",
          ADMINISTRADOR: "Administrador",
        },
        presencas: { ONLINE: "Online", AUSENTE: "Ausente", OFFLINE: "Offline" },
      },
    },
    equipe: {
      desativacao: {
        titulo: "Desativar usuário",
        descricao: "Desativar {nome}",
        cancelar: "Cancelar",
        confirmar: "Desativar",
      },
    },
  }),
}));

import { PaginaAcessosAdministracao } from "./pagina-acessos";

describe("PaginaAcessosAdministracao", () => {
  it("usa os usuários reais sem oferecer impersonação nem último acesso inventado", () => {
    render(<PaginaAcessosAdministracao />);

    expect(screen.getByText("Marina Admin")).toBeInTheDocument();
    expect(screen.getByText("marina@example.com")).toBeInTheDocument();
    const acaoForaDoEscopo = new RegExp(["entrar", "como"].join(" "), "i");
    expect(screen.queryByText(acaoForaDoEscopo)).not.toBeInTheDocument();
    expect(screen.queryByText(/último acesso/i)).not.toBeInTheDocument();
  });
});
