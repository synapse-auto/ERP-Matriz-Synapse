import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

const criarMutate = vi.fn();
const atualizarMutate = vi.fn();
const removerMutate = vi.fn();

const TAGS = [
  { id: "tag-1", nome: "Prioridade", cor: "var(--chart-1)", icone: "Flag" },
  { id: "tag-2", nome: "Revenda", cor: "var(--chart-2)", icone: "Store" },
];

vi.mock("@/lib/config/textos-provider", () => ({
  useTextos: () => ({
    tags: {
      titulo: "Tags",
      descricao: "Etiquetas para classificar e segmentar leads.",
      busca: "Buscar tag…",
      nova: "Nova tag",
      carregando: "Carregando tags...",
      vazio: "Nenhuma tag cadastrada ainda.",
      semResultados: "Nenhuma tag encontrada para a busca.",
      erro: "Não foi possível carregar as tags.",
      editar: "Editar",
      remover: "Remover",
      colunas: { tag: "Tag", acoes: "Ações" },
      formulario: {
        criarTitulo: "Nova tag",
        editarTitulo: "Editar tag",
        nome: "Nome da tag",
        nomePlaceholder: "Ex.: Prioridade",
        cor: "Cor",
        icone: "Ícone",
        salvar: "Salvar tag",
        cancelar: "Cancelar",
        erro: "Não foi possível salvar a tag. Verifique se o nome já está em uso.",
      },
    },
  }),
}));

vi.mock("@/lib/tags/use-tags", () => ({
  useTags: () => ({ data: TAGS, isLoading: false, isError: false }),
  useCriarTag: () => ({ mutate: criarMutate, isPending: false, isError: false }),
  useAtualizarTag: () => ({ mutate: atualizarMutate, isPending: false, isError: false }),
  useRemoverTag: () => ({ mutate: removerMutate, isPending: false, isError: false }),
}));

import { PaginaTags } from "./pagina-tags";

describe("pagina de tags", () => {
  it("lista as tags existentes e filtra pela busca", () => {
    render(<PaginaTags />);

    expect(screen.getByText("Prioridade")).toBeInTheDocument();
    expect(screen.getByText("Revenda")).toBeInTheDocument();

    fireEvent.change(screen.getByPlaceholderText("Buscar tag…"), {
      target: { value: "priori" },
    });

    expect(screen.getByText("Prioridade")).toBeInTheDocument();
    expect(screen.queryByText("Revenda")).not.toBeInTheDocument();
  });

  it("cria uma tag nova com nome, cor e ícone escolhidos", () => {
    render(<PaginaTags />);

    fireEvent.click(screen.getByRole("button", { name: "Nova tag" }));
    fireEvent.change(screen.getByPlaceholderText("Ex.: Prioridade"), {
      target: { value: "Obra" },
    });
    fireEvent.click(screen.getByLabelText("var(--chart-3)"));
    fireEvent.click(screen.getByLabelText("Crown"));
    fireEvent.click(screen.getByRole("button", { name: "Salvar tag" }));

    expect(criarMutate).toHaveBeenCalledWith(
      { nome: "Obra", cor: "var(--chart-3)", icone: "Crown" },
      expect.objectContaining({ onSuccess: expect.any(Function) }),
    );
  });

  it("remove uma tag existente", () => {
    render(<PaginaTags />);

    fireEvent.click(screen.getAllByRole("button", { name: "Remover" })[0]);

    expect(removerMutate).toHaveBeenCalledWith("tag-1");
  });
});
