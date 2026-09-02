import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

const criarMutate = vi.fn();
const editarMutate = vi.fn();
const desativarMutate = vi.fn();
const gerarSenhaMutate = vi.fn();
const disponibilidadeMutate = vi.fn();

const USUARIOS = [
  {
    id: "u1",
    nome: "Ana Beatriz",
    email: "ana@estruturalvidros.com.br",
    papel: "ATENDENTE",
    statusPresenca: "ONLINE",
    ativo: true,
    disponivelParaIa: true,
  },
  {
    id: "u2",
    nome: "Bruno Costa",
    email: "bruno@estruturalvidros.com.br",
    papel: "SUBGESTOR",
    statusPresenca: "OFFLINE",
    ativo: false,
    disponivelParaIa: false,
  },
  {
    id: "u3",
    nome: "Gil Gestor",
    email: "gil@estruturalvidros.com.br",
    papel: "GESTOR",
    statusPresenca: "ONLINE",
    ativo: true,
    disponivelParaIa: false,
  },
];

const AVALIACOES = {
  mediaGeral: 9.2,
  total: 40,
  porAtendente: [{ atendenteId: "u1", atendenteNome: "Ana Beatriz", media: 9.6, total: 20 }],
};

const DESEMPENHO = {
  porAtendente: [
    { atendenteId: "u1", atendenteNome: "Ana Beatriz", atendimentos: 142, vendas: 18 },
    { atendenteId: "u2", atendenteNome: "Bruno Costa", atendimentos: 24, vendas: 2 },
  ],
};

vi.mock("@/lib/config/textos-provider", () => ({
  useTextos: () => ({
    equipe: {
      titulo: "Equipe",
      descricao: "Atendentes, subgestores, presença e avaliações.",
      novo: "Novo usuário",
      carregando: "Carregando equipe...",
      erro: "Não foi possível carregar a equipe.",
      inativo: "Inativo",
      editar: "Editar",
      desativar: "Desativar",
      colunas: {
        usuario: "Usuário",
        funcao: "Função",
        presenca: "Presença",
        avaliacao: "Avaliação",
        atendimentos: "ATEND.",
        vendas: "VENDAS",
        acoes: "Ações",
      },
      dashboard: {
        equipeLabel: "Equipe",
        onlineLabel: "{online} online · {ativos} ativos",
        avaliacaoMedia: "Avaliação média",
        rankingAvaliacao: "Ranking · avaliação (0–10)",
        rankingVendas: "Ranking · vendas fechadas",
      },
      grade: { titulo: "USUÁRIOS" },
      papeis: { atendente: "Atendente", subgestor: "Subgestor" },
      presenca: { online: "Online", ausente: "Ausente", offline: "Offline" },
      disponibilidadeIa: { rotulo: "Disponibilidade IA", disponivel: "Disponível", indisponivel: "Fora do rodízio", naoAplicavel: "Não aplicável" },
      avaliacoes: { media: "Média geral", total: "Total de avaliações", semDados: "Sem avaliações" },
      formulario: {
        criarTitulo: "Criar usuário",
        editarTitulo: "Editar usuário",
        nome: "Nome",
        email: "E-mail",
        senha: "Senha inicial",
        papel: "Papel",
        atendente: "Atendente",
        subgestor: "Subgestor",
        salvar: "Salvar",
        cancelar: "Cancelar",
        erro: "Não foi possível salvar o usuário.",
      },
      senhaProvisoria: {
        acao: "Gerar senha provisória",
        dialogoTitulo: "Senha provisória gerada",
        dialogoDescricao: "Repasse esta senha para {nome}. Ela não será mostrada novamente.",
        copiar: "Copiar",
        copiada: "Copiada!",
        fechar: "Fechar",
        erro: "Não foi possível gerar a senha provisória.",
      },
      desativacao: {
        titulo: "Desativar usuário",
        descricao: "Deseja realmente desativar {nome}?",
        confirmar: "Desativar",
        cancelar: "Cancelar",
      },
    },
  }),
}));

vi.mock("@/lib/equipe/use-equipe", () => ({
  useEquipe: () => ({ data: USUARIOS, isLoading: false, isError: false }),
  useAvaliacoesEquipe: () => ({ data: AVALIACOES, isLoading: false, isError: false }),
  useDesempenhoEquipe: () => ({ data: DESEMPENHO, isLoading: false, isError: false }),
  useCriarUsuario: () => ({ mutate: criarMutate, isPending: false, isError: false }),
  useEditarUsuario: () => ({ mutate: editarMutate, isPending: false, isError: false }),
  useDesativarUsuario: () => ({ mutate: desativarMutate, isPending: false, isError: false }),
  useGerarSenhaProvisoria: () => ({ mutate: gerarSenhaMutate, isPending: false, isError: false }),
  useAtualizarDisponibilidadeParaIa: () => ({ mutate: disponibilidadeMutate, isPending: false, isError: false }),
}));

import { PaginaEquipe } from "./pagina-equipe";

describe("pagina de equipe", () => {
  it("lista os usuários como cards, com papel, presença e avaliação", () => {
    render(<PaginaEquipe />);

    expect(screen.getAllByText("Ana Beatriz").length).toBeGreaterThan(0);
    expect(screen.getAllByText("Bruno Costa").length).toBeGreaterThan(0);
    expect(screen.getByText("Online")).toBeInTheDocument();
    expect(screen.getByText("Offline")).toBeInTheDocument();
    expect(screen.getByText("Inativo")).toBeInTheDocument();
    expect(screen.getByText("9.6 (20)")).toBeInTheDocument();
    expect(screen.getByText("Sem avaliações")).toBeInTheDocument();
    expect(screen.getByRole("columnheader", { name: "ATEND." })).toBeInTheDocument();
    expect(screen.getByRole("columnheader", { name: "VENDAS" })).toBeInTheDocument();
    expect(screen.getByText("142")).toBeInTheDocument();
    expect(screen.getAllByText("18")).toHaveLength(2);
  });

  it("mostra o mini-dashboard com total, avaliação média e ranking", () => {
    render(<PaginaEquipe />);

    expect(screen.getByText("1 online · 1 ativos")).toBeInTheDocument();
    expect(screen.getByText("9.2")).toBeInTheDocument();
    expect(screen.getByText("Ranking · avaliação (0–10)")).toBeInTheDocument();
    expect(screen.getByText("Ranking · vendas fechadas")).toBeInTheDocument();
  });

  it("abre dialogo de confirmacao ao tentar desativar, e so desativa ao confirmar", () => {
    render(<PaginaEquipe />);

    // 1. Clica no botão da tabela
    fireEvent.click(screen.getByRole("button", { name: "Desativar Ana Beatriz" }));

    // 2. Garante que a mutação não foi chamada ainda
    expect(desativarMutate).not.toHaveBeenCalled();

    // 3. Verifica se o diálogo abriu e tenta cancelar
    const botaoCancelar = screen.getByRole("button", { name: "Cancelar" });
    fireEvent.click(botaoCancelar);
    expect(desativarMutate).not.toHaveBeenCalled(); // Mutação não chamada

    // 4. Abre de novo e confirma
    fireEvent.click(screen.getByRole("button", { name: "Desativar Ana Beatriz" }));
    const botaoConfirmar = screen.getAllByRole("button", { name: "Desativar" }).find((btn) => 
      btn.getAttribute("aria-label") !== "Desativar Ana Beatriz"
    );
    fireEvent.click(botaoConfirmar!);

    // 5. Garante que a mutação foi chamada com o ID correto
    expect(desativarMutate).toHaveBeenCalledWith("u1", expect.any(Object));
  });

  it("nao mostra o botao de desativar para quem ja esta inativo", () => {
    render(<PaginaEquipe />);

    expect(screen.queryByRole("button", { name: "Desativar Bruno Costa" })).not.toBeInTheDocument();
  });

  it("mostra o toggle de IA para subgestor e dispara a mesma mutacao", () => {
    render(<PaginaEquipe />);

    const toggleSub = screen.getByRole("switch", { name: "Disponibilidade IA Bruno Costa" });
    fireEvent.click(toggleSub);
    expect(disponibilidadeMutate).toHaveBeenCalledWith({ id: "u2", disponivelParaIa: true });

    expect(screen.getByRole("switch", { name: "Disponibilidade IA Ana Beatriz" })).toBeInTheDocument();
    expect(screen.queryByText("Gil Gestor")).not.toBeInTheDocument();
    expect(screen.queryByRole("switch", { name: /Gil Gestor/ })).not.toBeInTheDocument();
  });
});
