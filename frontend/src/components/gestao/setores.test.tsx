import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { fireEvent, render, screen, within } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { GestaoTextosSchema } from "@/lib/config/schema-gestao";
import { SetoresTextosSchema } from "@/lib/config/schema-setores";
import type { VisaoDeSetores } from "@/lib/gestao/setores";
import type { MinhasPermissoes } from "@/lib/gestao/types";

const TEXTOS_GESTAO = GestaoTextosSchema.parse(undefined);
const TEXTOS_SETORES = SetoresTextosSchema.parse(undefined);

vi.mock("@/lib/config/textos-provider", () => ({
  useTextos: () => ({ gestao: TEXTOS_GESTAO, setores: TEXTOS_SETORES, equipe: {} }),
}));

let funcionalidades: string[] = [];
vi.mock("@/lib/config/use-funcionalidades", () => ({ useFuncionalidadesHabilitadas: () => ({ data: funcionalidades }) }));

const consultaDeSetores = vi.fn();
const criar = { mutate: vi.fn(), isPending: false, isError: false, error: null as unknown };
const atualizar = { mutate: vi.fn(), isPending: false, isError: false, error: null as unknown };
const atribuir = { mutate: vi.fn(), isPending: false, isError: false, error: null as unknown };
let visao: VisaoDeSetores;
vi.mock("@/lib/gestao/setores", async (original) => ({
  ...(await original<typeof import("@/lib/gestao/setores")>()),
  useSetores: (habilitado: boolean) => {
    consultaDeSetores(habilitado);
    return { data: habilitado ? visao : undefined };
  },
  useCriarSetor: () => criar,
  useAtualizarSetor: () => atualizar,
  useAtribuirSetor: () => atribuir,
}));

vi.mock("@/lib/gestao/use-gestao", () => ({
  CHAVE_GESTAO: ["gestao"],
  usePerfis: () => ({ isLoading: false, isError: false, data: [], refetch: vi.fn() }),
  usePermissoesDaEquipe: () => ({ isLoading: false, isError: false, data: [], refetch: vi.fn() }),
}));

vi.mock("@/lib/equipe/use-equipe", () => ({
  useEquipe: () => ({
    isLoading: false,
    isError: false,
    refetch: vi.fn(),
    data: [
      { id: "ana", nome: "Ana Atendente", email: "ana@x", papel: "ATENDENTE", ativo: true, fotoUrl: null, statusPresenca: "ONLINE", disponivelParaIa: true },
    ],
  }),
  useAvaliacoesEquipe: () => ({ data: undefined }),
  useDesempenhoEquipe: () => ({ data: undefined }),
  useDesativarUsuario: () => ({ mutate: vi.fn(), isPending: false, isError: false }),
  useGerarSenhaProvisoria: () => ({ mutate: vi.fn(), isError: false }),
  useAtualizarDisponibilidadeParaIa: () => ({ mutate: vi.fn(), isPending: false }),
}));

import { AbaEquipe } from "./aba-equipe";

function minhasDe(papel: MinhasPermissoes["papel"], capacidades: string[]): MinhasPermissoes {
  return {
    usuarioId: "eu",
    papel,
    revisao: 1,
    acessaGestao: true,
    editaPerfis: false,
    editaExcecoes: false,
    capacidades: Object.fromEntries(capacidades.map((id) => [id, { permitido: true, motivo: "PERMITIDO" as const, alcance: null }])),
  };
}

function renderizar(minhas: MinhasPermissoes) {
  return render(
    <QueryClientProvider client={new QueryClient()}>
      <AbaEquipe textos={TEXTOS_GESTAO} minhas={minhas} novoAberto={false} onFecharNovo={vi.fn()} onAbrirPerfil={vi.fn()} />
    </QueryClientProvider>,
  );
}

beforeEach(() => {
  vi.clearAllMocks();
  funcionalidades = [];
  visao = {
    setores: [
      { id: "s1", nome: "Vendas", recebeDaTriagem: true, ativo: true, quantidadeDeUsuarios: 1 },
      { id: "s2", nome: "Diretoria", recebeDaTriagem: false, ativo: true, quantidadeDeUsuarios: 0 },
    ],
    atribuicoes: [{ usuarioId: "ana", setorId: "s1" }],
  };
});

describe("Setores na aba Equipe", () => {
  it("com a capacidade desligada não há painel, coluna nem consulta de setores", () => {
    renderizar(minhasDe("GESTOR", ["equipe.editar"]));

    expect(screen.queryByRole("region", { name: TEXTOS_SETORES.titulo })).not.toBeInTheDocument();
    expect(screen.queryByText(TEXTOS_SETORES.colunaSetor)).not.toBeInTheDocument();
    expect(consultaDeSetores).toHaveBeenCalledWith(false);
    expect(consultaDeSetores).not.toHaveBeenCalledWith(true);
  });

  it("ligada, gestor vê os setores, a coluna e cria um setor novo", () => {
    funcionalidades = ["setores"];
    renderizar(minhasDe("GESTOR", ["equipe.editar"]));

    const painel = screen.getByRole("region", { name: TEXTOS_SETORES.titulo });
    expect(within(painel).getByText("Vendas")).toBeInTheDocument();
    expect(within(painel).getByText("Diretoria")).toBeInTheDocument();
    expect(screen.getByText(TEXTOS_SETORES.colunaSetor)).toBeInTheDocument();

    fireEvent.change(within(painel).getByLabelText(TEXTOS_SETORES.nome), { target: { value: "  Posvenda " } });
    fireEvent.click(within(painel).getByRole("button", { name: TEXTOS_SETORES.criar }));

    expect(criar.mutate).toHaveBeenCalledWith("Posvenda", expect.anything());
  });

  it("ligada, gestor liga/desliga 'recebe da triagem' preservando nome e situação", () => {
    funcionalidades = ["setores"];
    renderizar(minhasDe("GESTOR", ["equipe.editar"]));

    fireEvent.click(screen.getByRole("switch", { name: "Receber da triagem: Diretoria" }));

    expect(atualizar.mutate).toHaveBeenCalledWith(
      { id: "s2", dados: { nome: "Diretoria", recebeDaTriagem: true, ativo: true } },
      expect.anything(),
    );
  });

  it("ligada, o setor do integrante aparece e a escolha chama a atribuição", () => {
    funcionalidades = ["setores"];
    renderizar(minhasDe("GESTOR", ["equipe.editar"]));

    expect(screen.getByRole("combobox", { name: "Setor de Ana Atendente" })).toHaveTextContent("Vendas");
  });

  it("sem equipe.editar o painel é só leitura e não oferece criar nem atribuir", () => {
    funcionalidades = ["setores"];
    renderizar(minhasDe("SUBGESTOR", ["equipe.ver"]));

    const painel = screen.getByRole("region", { name: TEXTOS_SETORES.titulo });
    expect(within(painel).getByText("Vendas")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: TEXTOS_SETORES.criar })).not.toBeInTheDocument();
    expect(screen.getByText(TEXTOS_SETORES.somenteGestao)).toBeInTheDocument();
    expect(screen.queryByRole("combobox", { name: "Setor de Ana Atendente" })).not.toBeInTheDocument();
  });
});
