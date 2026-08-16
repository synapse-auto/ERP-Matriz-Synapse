import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

vi.mock("@/lib/config/textos-provider", () => ({
  useTextos: () => ({
    estados: {
      erroGenerico: "Não foi possível carregar.",
      tentarNovamente: "Tentar novamente",
    },
  }),
}));

import { ErroDeCarregamento } from "./erro-de-carregamento";

describe("ErroDeCarregamento", () => {
  it("mantem a falha local recuperavel por uma nova tentativa", () => {
    const tentarNovamente = vi.fn();

    render(<ErroDeCarregamento onTentarNovamente={tentarNovamente} />);
    fireEvent.click(screen.getByRole("button", { name: "Tentar novamente" }));

    expect(screen.getByRole("alert")).toHaveTextContent("Não foi possível carregar.");
    expect(tentarNovamente).toHaveBeenCalledTimes(1);
  });
});
