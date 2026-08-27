import { render, screen, fireEvent } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { NovidadesDialog } from "./novidades-dialog";

vi.mock("@/lib/config/textos-provider", () => ({
  useTextos: () => ({
    novidades: {
      titulo: "Novidades & Em Breve",
      subtituloNovidades: "Conheça as principais novidades",
      subtituloEmBreve: "Confira o que vem por aí",
      abas: {
        novidades: "Novidades",
        embreve: "Em breve"
      },
      novoTag: "NOVO",
      itensNovidades: [
        {
          titulo: "Item Novo 1",
          descricao: "Descricao 1",
          data: "2026-07-22",
          novo: true
        },
        {
          titulo: "Item Antigo 1",
          descricao: "Descricao 2",
          data: "2026-07-13",
          novo: false
        },
        {
          titulo: "Item Antigo 2",
          descricao: "Descricao 3",
          data: "2026-07-13",
          novo: false
        }
      ],
      itensEmBreve: [
        {
          icone: "brain",
          titulo: "Em breve 1",
          descricao: "Descricao em breve 1",
          status: "Pesquisa",
          tom: "info",
          previsao: "Q3 2026"
        }
      ]
    }
  })
}));

describe("NovidadesDialog", () => {
  it("agrupa itens por data e mostra a tag NOVO apenas onde configurado", () => {
    render(<NovidadesDialog aberto={true} onFechar={vi.fn()} />);

    expect(
      screen.getByRole("heading", {
        level: 3,
        name: "22 de julho de 2026",
      }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("heading", {
        level: 3,
        name: "13 de julho de 2026",
      }),
    ).toBeInTheDocument();

    // Testa títulos dos itens
    expect(screen.getByText("Item Novo 1")).toBeInTheDocument();
    expect(screen.getByText("Item Antigo 1")).toBeInTheDocument();
    expect(screen.getByText("Item Antigo 2")).toBeInTheDocument();

    // Testa a tag NOVO
    const badgesNovo = screen.getAllByText("NOVO");
    expect(badgesNovo).toHaveLength(1); // Somente o Item Novo 1 tem a tag
  });

  it("renderiza a aba Em Breve corretamente com PillDeStatus", () => {
    render(<NovidadesDialog aberto={true} onFechar={vi.fn()} />);

    // Troca para a aba Em Breve
    const abaEmBreve = screen.getByRole("tab", { name: "Em breve" });
    fireEvent.click(abaEmBreve);

    expect(screen.getByText("Em breve 1")).toBeInTheDocument();
    expect(screen.getByText("Pesquisa")).toBeInTheDocument(); // Texto do PillDeStatus
  });
});
