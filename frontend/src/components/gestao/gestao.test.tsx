import { fireEvent, render, screen, within } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { ErroDeApi } from "@/lib/api/errors";
import { GestaoTextosSchema } from "@/lib/config/schema-gestao";
import type { Catalogo, MinhasPermissoes, Perfil, PermissoesDeUsuario, ResumoDeUsuario } from "@/lib/gestao/types";

const TEXTOS = GestaoTextosSchema.parse(undefined);

vi.mock("@/lib/config/textos-provider", () => ({ useTextos: () => ({ gestao: TEXTOS, equipe: {} }) }));
vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: vi.fn(), replace: vi.fn() }),
  useSearchParams: () => new URLSearchParams(),
}));
vi.mock("./aba-equipe", () => ({ AbaEquipe: () => <div>aba equipe</div> }));

const salvarPerfil = { mutate: vi.fn(), reset: vi.fn(), isPending: false, error: null as unknown };
const salvarExcecoes = { mutate: vi.fn(), reset: vi.fn(), isPending: false, error: null as unknown };
const restaurar = { mutate: vi.fn(), reset: vi.fn(), isPending: false, error: null as unknown };
let minhas: MinhasPermissoes;
let perfis: Perfil[];

const CATALOGO: Catalogo = {
  modulos: [
    {
      id: "atendimentos", nivelMinimoPermitido: "VER", flag: null,
      nivelMaximoPorPapel: { ATENDENTE: "GERENCIAR", SUBGESTOR: "GERENCIAR", GESTOR: "GERENCIAR", ADMINISTRADOR: "GERENCIAR" },
      nivelPadraoPorPapel: { ATENDENTE: "GERENCIAR", SUBGESTOR: "GERENCIAR", GESTOR: "GERENCIAR", ADMINISTRADOR: "GERENCIAR" },
    },
    {
      id: "tags", nivelMinimoPermitido: "VER", flag: null,
      nivelMaximoPorPapel: { ATENDENTE: "EDITAR", SUBGESTOR: "GERENCIAR", GESTOR: "GERENCIAR", ADMINISTRADOR: "GERENCIAR" },
      nivelPadraoPorPapel: { ATENDENTE: "EDITAR", SUBGESTOR: "GERENCIAR", GESTOR: "GERENCIAR", ADMINISTRADOR: "GERENCIAR" },
    },
  ],
  capacidades: [
    { id: "atendimentos.ver", modulo: "atendimentos", nivelMinimo: "VER", tipo: "ALCANCE_ESTRUTURAL", sensivel: false, teto: ["ATENDENTE", "SUBGESTOR", "GESTOR", "ADMINISTRADOR"], delegavel: false, dependencias: [], alcancePorPapel: { ATENDENTE: "MEUS", SUBGESTOR: "TODOS", GESTOR: "TODOS", ADMINISTRADOR: "TODOS" } },
    { id: "atendimentos.finalizar_lote", modulo: "atendimentos", nivelMinimo: "GERENCIAR", tipo: "ACAO", sensivel: true, teto: ["ATENDENTE", "SUBGESTOR", "GESTOR", "ADMINISTRADOR"], delegavel: false, dependencias: [], alcancePorPapel: {} },
    { id: "tags.aplicar", modulo: "tags", nivelMinimo: "EDITAR", tipo: "ACAO", sensivel: false, teto: ["ATENDENTE", "SUBGESTOR", "GESTOR", "ADMINISTRADOR"], delegavel: true, dependencias: [], alcancePorPapel: {} },
    { id: "tags.criar", modulo: "tags", nivelMinimo: "GERENCIAR", tipo: "ACAO", sensivel: false, teto: ["SUBGESTOR", "GESTOR", "ADMINISTRADOR"], delegavel: false, dependencias: [], alcancePorPapel: {} },
  ],
};

function perfil(papel: Perfil["papel"], fixo = false, editavel = !fixo): Perfil {
  const noTeto = (id: string) => CATALOGO.capacidades.find((c) => c.id === id)!.teto.includes(papel);
  return {
    papel, fixo, revisao: 3, usuarios: papel === "ATENDENTE" ? 6 : 1, permitidas: 2, total: 2, editavel,
    modulos: CATALOGO.modulos.map((m) => ({ id: m.id, doPerfil: m.nivelPadraoPorPapel[papel], excecao: null, efetivo: m.nivelPadraoPorPapel[papel], minimo: m.nivelMinimoPermitido, maximo: m.nivelMaximoPorPapel[papel] })),
    capacidades: CATALOGO.capacidades.map((c) => ({
      id: c.id, doPerfil: c.tipo === "ACAO" && noTeto(c.id) ? true : null, excecao: null, permitido: noTeto(c.id),
      motivo: noTeto(c.id) ? "PERMITIDO" : "TETO_DO_PAPEL", origem: "PERFIL", alcance: c.alcancePorPapel[papel] ?? null, alteravel: true,
    })),
  };
}

const EQUIPE: ResumoDeUsuario[] = [
  { id: "g", nome: "Gil Gestor", email: "g@x", papel: "GESTOR", ativo: true, fotoUrl: null, excecoes: 0, editavel: false },
  { id: "a", nome: "Ana Atendente", email: "a@x", papel: "ATENDENTE", ativo: true, fotoUrl: null, excecoes: 1, editavel: true },
];

function detalheDaAna(): PermissoesDeUsuario {
  const p = perfil("ATENDENTE");
  return {
    usuario: EQUIPE[1], revisao: 5, revisaoDoPerfil: 3, fixo: false, modulos: p.modulos,
    capacidades: p.capacidades.map((c) => (c.id === "tags.aplicar" ? { ...c, excecao: false, permitido: false, motivo: "DESLIGADO", origem: "EXCECAO" } : c)),
  };
}

vi.mock("@/lib/gestao/use-gestao", () => ({
  CHAVE_GESTAO: ["gestao"],
  useMinhasPermissoes: () => ({ isLoading: false, isError: false, data: minhas, refetch: vi.fn() }),
  useCatalogo: () => ({ isLoading: false, isError: false, data: CATALOGO, refetch: vi.fn() }),
  usePerfis: () => ({ isLoading: false, isError: false, data: perfis, refetch: vi.fn() }),
  usePermissoesDaEquipe: () => ({ isLoading: false, isError: false, data: EQUIPE, refetch: vi.fn() }),
  usePermissoesDeUsuario: () => ({ isLoading: false, isError: false, data: detalheDaAna(), refetch: vi.fn() }),
  useSalvarPerfil: () => salvarPerfil,
  useSalvarExcecoes: () => salvarExcecoes,
  useRestaurarPadrao: () => restaurar,
}));
vi.mock("@/lib/equipe/use-equipe", () => ({ useEquipe: () => ({ data: [] }) }));

import { AbaExcecoes } from "./aba-excecoes";
import { AbaPermissoes } from "./aba-permissoes";
import { PaginaGestao } from "./pagina-gestao";

function minhasDe(papel: MinhasPermissoes["papel"], acessaGestao: boolean): MinhasPermissoes {
  return { usuarioId: "eu", papel, revisao: 1, acessaGestao, editaPerfis: papel === "GESTOR", editaExcecoes: papel === "GESTOR", capacidades: {} };
}

/** SUBGESTOR com `equipe.perfis`: o backend devolve só o perfil ATENDENTE como editável. */
function subgestorQueEditaPerfis(permitidas: string[]): void {
  minhas = {
    ...minhasDe("SUBGESTOR", true),
    editaPerfis: true,
    capacidades: Object.fromEntries(permitidas.map((id) => [id, { permitido: true, motivo: "PERMITIDO" as const, alcance: null }])),
  };
  perfis = [perfil("GESTOR", true), perfil("SUBGESTOR", false, false), perfil("ATENDENTE", false, true)];
}

beforeEach(() => {
  vi.clearAllMocks();
  salvarPerfil.error = null;
  salvarExcecoes.error = null;
  minhas = minhasDe("GESTOR", true);
  perfis = [perfil("GESTOR", true), perfil("SUBGESTOR"), perfil("ATENDENTE")];
});

describe("Gestão", () => {
  it("ATENDENTE não acessa: a página mostra o bloqueio vindo do backend, sem abas", () => {
    minhas = minhasDe("ATENDENTE", false);
    render(<PaginaGestao />);
    expect(screen.getByRole("alert")).toHaveTextContent(TEXTOS.semAcesso);
    expect(screen.queryByRole("tab")).not.toBeInTheDocument();
  });

  it("selo reflete o papel real: Administrador não vira 'somente gestor'", () => {
    minhas = minhasDe("ADMINISTRADOR", true);
    render(<PaginaGestao />);
    expect(screen.getByText(TEXTOS.selo.administrador)).toBeInTheDocument();
    expect(screen.getAllByRole("tab")).toHaveLength(3);
  });

  it("Permissões: estrutural aparece travado em Meus; fora do teto aparece bloqueado com motivo", () => {
    render(<AbaPermissoes textos={TEXTOS} minhas={minhas} papelInicial="ATENDENTE" onSujoChange={vi.fn()} />);
    expect(screen.getByText(TEXTOS.permissoes.motivos.ESTRUTURAL_MEUS)).toBeInTheDocument();
    const criar = screen.getByRole("switch", { name: TEXTOS.capacidades["tags.criar"] });
    expect(criar).toHaveAttribute("aria-disabled", "true");
    expect(screen.getByText(TEXTOS.permissoes.motivos.TETO_DO_PAPEL)).toBeInTheDocument();
  });

  it("Permissões: mudar o nível gera rascunho; alteração sensível pede confirmação antes de salvar", () => {
    const onSujo = vi.fn();
    render(<AbaPermissoes textos={TEXTOS} minhas={minhas} papelInicial="SUBGESTOR" onSujoChange={onSujo} />);
    const niveis = screen.getByRole("radiogroup", { name: `Nível de ${TEXTOS.modulos.atendimentos.rotulo}` });
    fireEvent.click(within(niveis).getByRole("radio", { name: TEXTOS.permissoes.niveis.EDITAR }));
    expect(onSujo).toHaveBeenCalledWith(true);
    expect(screen.getByText(TEXTOS.barra.pendentes.replace("{n}", "2"))).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: TEXTOS.barra.salvar }));
    expect(salvarPerfil.mutate).not.toHaveBeenCalled();
    fireEvent.click(screen.getByRole("button", { name: TEXTOS.barra.sensivelConfirmar }));
    expect(salvarPerfil.mutate).toHaveBeenCalledWith(
      expect.objectContaining({ papel: "SUBGESTOR", revisao: 3 }),
      expect.anything(),
    );
  });

  it("Permissões: conflito 409 mantém o rascunho e oferece recarregar", () => {
    salvarPerfil.error = new ErroDeApi(409, { status: 409 }, "conflito");
    render(<AbaPermissoes textos={TEXTOS} minhas={minhas} papelInicial="SUBGESTOR" onSujoChange={vi.fn()} />);
    expect(screen.getByRole("alert")).toHaveTextContent(TEXTOS.barra.conflito);
    expect(screen.getByRole("button", { name: TEXTOS.barra.recarregar })).toBeInTheDocument();
  });

  it("Permissões: SUBGESTOR que edita perfis abre no de ATENDENTE, sem nível e sem cópia; só o delegável que ele tem", () => {
    subgestorQueEditaPerfis(["tags.aplicar"]);
    render(<AbaPermissoes textos={TEXTOS} minhas={minhas} onSujoChange={vi.fn()} />);

    expect(screen.getByRole("radio", { name: new RegExp(TEXTOS.papeis.ATENDENTE) })).toHaveAttribute("aria-checked", "true");
    expect(screen.getByText(TEXTOS.excecoes.nivelNaoDelegavel)).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: new RegExp(TEXTOS.permissoes.copiar) })).not.toBeInTheDocument();
    const niveis = screen.getByRole("radiogroup", { name: `Nível de ${TEXTOS.modulos.tags.rotulo}` });
    within(niveis).getAllByRole("radio").forEach((radio) => expect(radio).toBeDisabled());

    const lote = screen.getByRole("switch", { name: TEXTOS.capacidades["atendimentos.finalizar_lote"] });
    expect(lote).toHaveAttribute("aria-disabled", "true");
    expect(lote).toHaveAccessibleDescription(TEXTOS.excecoes.naoDelegavel);

    const aplicar = screen.getByRole("switch", { name: TEXTOS.capacidades["tags.aplicar"] });
    expect(aplicar).not.toHaveAttribute("aria-disabled", "true");
    fireEvent.click(aplicar);
    expect(screen.getByText(TEXTOS.barra.pendente)).toBeInTheDocument();
  });

  it("Permissões: SUBGESTOR delegado não liga no perfil o que ele mesmo não tem", () => {
    subgestorQueEditaPerfis([]);
    const atendente = perfis[2];
    perfis[2] = {
      ...atendente,
      capacidades: atendente.capacidades.map((c) => (c.id === "tags.aplicar" ? { ...c, doPerfil: false, permitido: false, motivo: "DESLIGADO" } : c)),
    };
    render(<AbaPermissoes textos={TEXTOS} minhas={minhas} onSujoChange={vi.fn()} />);

    const aplicar = screen.getByRole("switch", { name: TEXTOS.capacidades["tags.aplicar"] });
    expect(aplicar).toHaveAttribute("aria-disabled", "true");
    expect(aplicar).toHaveAccessibleDescription(TEXTOS.excecoes.semPermissaoPropria);
  });

  it("Permissões: SUBGESTOR no próprio perfil lê o motivo certo, não 'somente leitura' genérico", () => {
    subgestorQueEditaPerfis([]);
    render(<AbaPermissoes textos={TEXTOS} minhas={minhas} papelInicial="SUBGESTOR" onSujoChange={vi.fn()} />);
    expect(screen.getByText(TEXTOS.excecoes.proprio)).toBeInTheDocument();
    expect(screen.queryByText(TEXTOS.permissoes.somenteLeitura)).not.toBeInTheDocument();
    expect(screen.getByRole("switch", { name: TEXTOS.capacidades["tags.aplicar"] })).toHaveAttribute("aria-disabled", "true");
  });

  it("Exceções: linha personalizada tem selo e restauração individual; voltar ao padrão vira rascunho", () => {
    render(<AbaExcecoes textos={TEXTOS} minhas={minhas} usuarioInicial="a" onSujoChange={vi.fn()} />);
    expect(screen.getAllByText(TEXTOS.excecoes.personalizado).length).toBeGreaterThan(0);
    const restaurarTags = screen.getByRole("button", { name: `Restaurar o padrão de ${TEXTOS.capacidades["tags.aplicar"]}` });
    fireEvent.click(restaurarTags);
    expect(screen.getByText(TEXTOS.barra.pendente)).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: TEXTOS.barra.salvar }));
    expect(restaurar.mutate).toHaveBeenCalledWith({ id: "a", revisao: 5 }, expect.anything());
  });

  it("Exceções: copiar de GESTOR aparece desabilitado com o motivo, nunca como operação irrestrita", () => {
    render(<AbaExcecoes textos={TEXTOS} minhas={minhas} usuarioInicial="a" onSujoChange={vi.fn()} />);
    fireEvent.click(screen.getByRole("combobox", { name: TEXTOS.excecoes.copiarDe }));
    const opcao = screen.getByRole("option", { name: TEXTOS.excecoes.copiaIndisponivel.replace("{nome}", "Gil Gestor") });
    expect(opcao).toHaveAttribute("aria-disabled", "true");
  });
});
