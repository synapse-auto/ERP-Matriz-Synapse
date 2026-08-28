import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

vi.mock("next/navigation", () => ({ usePathname: () => "/administracao/feedbacks" }));

vi.mock("@/lib/config/textos-provider", () => ({
  useTextos: () => ({
    administracao: {
      titulo: "Administração",
      descricao: "Área administrativa",
      estadoSistema: "Estado não verificado",
      navegacao: "Áreas da Administração",
      abas: { visaoGeral: "Visão geral", acessos: "Acessos", feedbacks: "Feedbacks" },
      abasDescricoes: {
        visaoGeral: "Usuários e recursos habilitados",
        acessos: "Gerenciar logins",
        feedbacks: "Sugestões e erros",
      },
    },
  }),
}));

import { LayoutAdministracao } from "./layout-administracao";

describe("LayoutAdministracao", () => {
  it("mantém navegação interna acessível e marca a rota atual", () => {
    render(
      <LayoutAdministracao>
        <p>Conteúdo</p>
      </LayoutAdministracao>,
    );

    expect(screen.getByRole("navigation", { name: "Áreas da Administração" })).toBeInTheDocument();
    expect(screen.getByRole("main")).toHaveClass("bg-[var(--fundo-canvas)]", "overflow-x-hidden");
    expect(screen.getByRole("navigation", { name: "Áreas da Administração" })).toHaveClass(
      "sm:w-[266px]",
    );
    const feedbacks = screen.getByRole("link", { name: /Feedbacks/ });
    expect(feedbacks).toHaveAttribute("aria-current", "page");
    expect(feedbacks).toHaveClass("bg-cor-ia/10", "text-cor-ia");
    expect(screen.getByRole("link", { name: /Acessos/ })).toHaveAttribute("href", "/administracao/acessos");
    const areaForaDoEscopo = new RegExp(["tutoriais", "documentação"].join(".*"), "i");
    expect(screen.queryByText(areaForaDoEscopo)).not.toBeInTheDocument();
  });

  it("exibe somente Administração, sem selo de acesso restrito", () => {
    render(
      <LayoutAdministracao>
        <p>Conteúdo</p>
      </LayoutAdministracao>,
    );

    expect(screen.getByRole("heading", { name: "Administração" })).toBeInTheDocument();
    expect(screen.queryByText("Acesso restrito")).not.toBeInTheDocument();
    expect(screen.getByText("Estado não verificado")).toBeInTheDocument();
  });
});
