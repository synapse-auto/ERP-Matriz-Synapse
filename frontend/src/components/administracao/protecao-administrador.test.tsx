import { render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

const autenticacao = vi.hoisted(() => ({ papel: "ATENDENTE", status: "autenticado" }));

vi.mock("@/lib/auth/auth-store", () => ({
  useAuthStore: (seletor: (estado: typeof autenticacao) => unknown) => seletor(autenticacao),
}));

vi.mock("@/lib/config/textos-provider", () => ({
  useTextos: () => ({
    estados: { carregando: "Carregando..." },
    administracao: {
      semPermissaoTitulo: "Acesso não autorizado",
      semPermissaoDescricao: "Somente administradores.",
      voltar: "Voltar para Atendimentos",
    },
  }),
}));

import { ProtecaoAdministrador } from "./protecao-administrador";

describe("ProtecaoAdministrador", () => {
  beforeEach(() => {
    autenticacao.papel = "ATENDENTE";
    autenticacao.status = "autenticado";
  });

  it("impede o acesso direto de não administrador", () => {
    render(
      <ProtecaoAdministrador>
        <p>Conteúdo secreto</p>
      </ProtecaoAdministrador>,
    );

    expect(screen.getByRole("heading", { name: "Acesso não autorizado" })).toBeInTheDocument();
    expect(screen.queryByText("Conteúdo secreto")).not.toBeInTheDocument();
  });

  it("renderiza o shell para administrador", () => {
    autenticacao.papel = "ADMINISTRADOR";
    render(
      <ProtecaoAdministrador>
        <p>Conteúdo secreto</p>
      </ProtecaoAdministrador>,
    );

    expect(screen.getByText("Conteúdo secreto")).toBeInTheDocument();
  });
});
