import { fireEvent, render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

const consultar = vi.hoisted(() => vi.fn());
const refetch = vi.hoisted(() => vi.fn());
const fetchNextPage = vi.hoisted(() => vi.fn());

vi.mock("@/lib/feedbacks/use-feedbacks", () => ({
  useFeedbacksAdministrativos: (tipo: unknown) => consultar(tipo),
}));

vi.mock("@/lib/config/textos-provider", () => ({
  useTextos: () => ({
    estados: { tentarNovamente: "Tentar novamente" },
    feedbacks: {
      tipos: { sugestao: "Sugestão", erro: "Erro" },
      areas: {
        geral: "Nenhuma aba específica",
        atendimentos: "Atendimentos",
        agenda: "Agenda",
        dashboard: "Dashboard",
        equipe: "Equipe",
        automacao: "Automação",
        mensagensProgramadas: "Mensagens programadas",
        lembretes: "Lembretes",
        tags: "Tags",
        configuracoes: "Configurações",
      },
    },
    administracao: {
      feedbacks: {
        titulo: "Feedbacks",
        descricao: "Recebidos",
        filtro: "Filtrar feedbacks",
        todos: "Todos",
        sugestoes: "Sugestões",
        erros: "Erros",
        carregando: "Carregando...",
        erro: "Falha ao carregar.",
        vazio: "Nenhum feedback.",
        carregarMais: "Carregar mais",
        carregandoMais: "Carregando...",
        autorPapel: "{papel}",
        tipoArea: "{tipo} · {area}",
        data: "Enviado em {data}",
      },
    },
  }),
}));

import { PaginaFeedbacksAdministracao } from "./pagina-feedbacks-administracao";

const pagina = {
  itens: [
    {
      id: "feedback-1",
      autorId: "usuario-1",
      autorNome: "Ana Beatriz",
      autorPapel: "ATENDENTE",
      autorFotoUrl: null,
      tipo: "SUGESTAO",
      areaChave: "AGENDA",
      descricao: "Melhorar os filtros",
      criadoEm: "2026-08-27T10:00:00Z",
    },
  ],
  proximoCriadoEm: "2026-08-27T10:00:00Z",
  proximoId: "feedback-1",
};

describe("PaginaFeedbacksAdministracao", () => {
  beforeEach(() => {
    consultar.mockReset();
    refetch.mockReset();
    fetchNextPage.mockReset();
    consultar.mockReturnValue({
      data: { pages: [pagina] },
      isLoading: false,
      isError: false,
      hasNextPage: true,
      isFetchingNextPage: false,
      refetch,
      fetchNextPage,
    });
  });

  it("mostra autor real, altera o filtro da consulta e pagina", () => {
    render(<PaginaFeedbacksAdministracao />);

    expect(screen.getByText("Ana Beatriz")).toBeInTheDocument();
    expect(screen.getByText("Melhorar os filtros")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Erros" }));
    expect(consultar).toHaveBeenLastCalledWith("ERRO");
    fireEvent.click(screen.getByRole("button", { name: "Carregar mais" }));
    expect(fetchNextPage).toHaveBeenCalledOnce();
  });

  it("renderiza vazio real e tenta novamente depois de erro", () => {
    consultar.mockReturnValueOnce({
      data: { pages: [{ itens: [] }] },
      isLoading: false,
      isError: false,
      hasNextPage: false,
      isFetchingNextPage: false,
      refetch,
      fetchNextPage,
    });
    const { rerender } = render(<PaginaFeedbacksAdministracao />);
    expect(screen.getByText("Nenhum feedback.")).toBeInTheDocument();
    expect(screen.getByText("Nenhum feedback.").closest("div")).toHaveClass("border-dashed");

    consultar.mockReturnValue({
      data: undefined,
      isLoading: false,
      isError: true,
      hasNextPage: false,
      isFetchingNextPage: false,
      refetch,
      fetchNextPage,
    });
    rerender(<PaginaFeedbacksAdministracao />);
    fireEvent.click(screen.getByRole("button", { name: "Tentar novamente" }));
    expect(refetch).toHaveBeenCalledOnce();
  });
});
