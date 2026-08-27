import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

vi.mock("next/navigation", () => ({ usePathname: () => "/administracao/feedbacks" }));

vi.mock("@/lib/config/textos-provider", () => ({
  useTextos: () => ({
    administracao: {
      titulo: "Administração",
      descricao: "Área administrativa",
      restrito: "Acesso restrito",
      estadoSistema: "Estado não verificado",
      navegacao: "Áreas da Administração",
      abas: { visaoGeral: "Visão geral", acessos: "Acessos", feedbacks: "Feedbacks" },
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
    expect(screen.getByRole("link", { name: "Feedbacks" })).toHaveAttribute("aria-current", "page");
    expect(screen.getByRole("link", { name: "Acessos" })).toHaveAttribute(
      "href",
      "/administracao/acessos",
    );
    const areaForaDoEscopo = new RegExp(["tutoriais", "documentação"].join(".*"), "i");
    expect(screen.queryByText(areaForaDoEscopo)).not.toBeInTheDocument();
  });
});
