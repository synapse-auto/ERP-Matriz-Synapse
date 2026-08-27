import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

vi.mock("@/components/administracao/pagina-visao-geral", () => ({
  PaginaVisaoGeralAdministracao: () => <div data-testid="visao-geral-administracao" />,
}));

import AdministracaoPage from "./page";

describe("AdministracaoPage", () => {
  it("renderiza a visão geral administrativa real", () => {
    render(<AdministracaoPage />);
    expect(screen.getByTestId("visao-geral-administracao")).toBeInTheDocument();
  });
});
