import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

const criarMutate = vi.fn();
const atualizarMutate = vi.fn();
const removerMutate = vi.fn();

const TAGS = [
  { id: "tag-1", nome: "Prioridade", cor: "var(--chart-1)", icone: "Flag" },
  { id: "tag-2", nome: "Revenda", cor: "var(--chart-2)", icone: "Store" },
];

const AGREGACAO = {
  totalLeadsVisiveis: 20,
  leadsComTag: 15,
  percentualTagueados: 75,
  tagMaisUsada: {
    id: "tag-1",
    nome: "Prioridade",
    cor: "var(--chart-1)",
    icone: "Flag",
    quantidade: 12,
  },
  porTag: [
    { id: "tag-1", nome: "Prioridade", cor: "var(--chart-1)", icone: "Flag", quantidade: 12 },
    { id: "tag-2", nome: "Revenda", cor: "var(--chart-2)", icone: "Store", quantidade: 3 },
  ],
};

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
      dashboard: {
        totalTags: "Total de tags",
        tagMaisUsada: "Tag mais usada",
        leadsVinculados: "{n} leads vinculados",
        leadsTagueados: "Leads tagueados",
        leadsDeTotal: "{comTag} de {total} leads",
        semDados: "Sem leads visíveis ainda",
      },
      grade: {
        titulo: "TODAS AS TAGS",
        leadsEPercentual: "{n} leads · {pct}% da base",
        previa: "Prévia da etiqueta",
      },
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
  useAgregacaoDeTags: () => ({ data: AGREGACAO, isLoading: false, isError: false }),
  useCriarTag: () => ({ mutate: criarMutate, isPending: false, isError: false }),
  useAtualizarTag: () => ({ mutate: atualizarMutate, isPending: false, isError: false }),
  useRemoverTag: () => ({ mutate: removerMutate, isPending: false, isError: false }),
}));

import { PaginaTags } from "./pagina-tags";

describe("pagina de tags", () => {
  it("lista as tags existentes e filtra pela busca", () => {
    render(<PaginaTags />);

    expect(screen.getAllByText("Prioridade").length).toBeGreaterThan(0);
    expect(screen.getAllByText("Revenda").length).toBeGreaterThan(0);

    fireEvent.change(screen.getByPlaceholderText("Buscar tag…"), {
      target: { value: "priori" },
    });

    expect(screen.getAllByText("Prioridade").length).toBeGreaterThan(0);
    expect(screen.queryByText("Revenda")).not.toBeInTheDocument();
  });

  it("mostra o mini-dashboard com total de tags, tag mais usada e % tagueados", () => {
    render(<PaginaTags />);

    expect(screen.getByText("Total de tags")).toBeInTheDocument();
    expect(screen.getByText("2")).toBeInTheDocument();
    expect(screen.getByText("Tag mais usada")).toBeInTheDocument();
    expect(screen.getByText("12 leads vinculados")).toBeInTheDocument();
    expect(screen.getByText("75%")).toBeInTheDocument();
    expect(screen.getByText("15 de 20 leads")).toBeInTheDocument();
  });

  it("mostra a contagem de leads por tag vinda da agregação", () => {
    render(<PaginaTags />);

    expect(screen.getByText("12 leads · 60% da base")).toBeInTheDocument();
    expect(screen.getByText("3 leads · 15% da base")).toBeInTheDocument();
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

    fireEvent.click(screen.getByRole("button", { name: "Remover Prioridade" }));

    expect(removerMutate).toHaveBeenCalledWith("tag-1");
  });
});
