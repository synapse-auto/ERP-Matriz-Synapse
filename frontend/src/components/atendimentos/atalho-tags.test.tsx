import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

const vincular = vi.fn();
const desvincular = vi.fn();

vi.mock("@/lib/config/textos-provider", () => ({
  useTextos: () => ({
    painelLead: {
      tags: {
        titulo: "Tags",
        botao: "Tag",
        selecionar: "Selecionar tag",
        adicionar: "Adicionar tag",
        remover: "Remover tag {nome}",
        erroReversao: "Estado anterior restaurado",
      },
    },
  }),
}));

vi.mock("@/lib/lead/use-painel-lead", () => ({
  useTagsDoLead: () => ({
    data: [
      { id: "tag-atual", nome: "Atual", cor: "var(--primary)", icone: null },
    ],
  }),
  useTodasAsTags: () => ({
    data: [
      { id: "tag-atual", nome: "Atual", cor: "var(--primary)", icone: null },
      { id: "tag-nova", nome: "Nova", cor: "var(--secondary)", icone: null },
    ],
  }),
  useVincularTag: () => ({ mutate: vincular }),
  useDesvincularTag: () => ({ mutate: desvincular }),
}));

import { AtalhoTags } from "./atalho-tags";

describe("atalho de tags no cabecalho", () => {
  it("vincula uma tag disponivel sem abrir a ficha lateral", async () => {
    render(<AtalhoTags leadId="lead-1" />);

    fireEvent.click(screen.getByLabelText("Tags"));
    fireEvent.click(await screen.findByLabelText("Adicionar tag Nova"));

    expect(vincular).toHaveBeenCalledWith(
      { tag: expect.objectContaining({ id: "tag-nova" }) },
      expect.objectContaining({ onError: expect.any(Function) }),
    );
    expect(desvincular).not.toHaveBeenCalled();
  });

  it("mostra um unico simbolo de adicao no modo painel", () => {
    render(<AtalhoTags leadId="lead-1" modo="painel" />);

    const botao = screen.getByLabelText("Tags");
    expect(botao).toHaveTextContent("Tag");
    expect(botao.textContent).not.toContain("+ Tag");
  });
});
