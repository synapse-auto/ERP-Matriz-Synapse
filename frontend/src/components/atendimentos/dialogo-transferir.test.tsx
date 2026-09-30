import { fireEvent, render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { ErroDeApi } from "@/lib/api/errors";
import type { StatusAtendimento } from "@/lib/atendimento/types";
import { definirCapacidadesDeTeste } from "@/test/capacidades-de-teste";

type DestinoMock = { id: string; nome: string; papel?: "ATENDENTE" | "SUBGESTOR" };
type AuthEstado = {
  papel: string;
  usuarioId: string;
};

const estado = vi.hoisted(() => ({
  papel: "GESTOR",
  usuarioId: "gestor-1",
  destinos: {
    data: undefined as DestinoMock[] | undefined,
    isPending: false,
    isError: false,
    isSuccess: true,
    refetch: vi.fn(),
  },
  habilitada: undefined as boolean | undefined,
  erro: null as Error | null,
  transferir: vi.fn(),
}));

vi.mock("@tanstack/react-query", () => ({
  useQuery: (opcoes: { enabled?: boolean }) => {
    estado.habilitada = opcoes.enabled;
    return estado.destinos;
  },
}));

vi.mock("@/lib/atendimento/api", () => ({ listarDestinosDeTransferencia: vi.fn() }));
vi.mock("@/lib/atendimento/use-transferir-finalizar", () => ({
  useTransferirAtendimento: () => ({
    mutate: estado.transferir,
    isPending: false,
    isError: estado.erro !== null,
    error: estado.erro,
  }),
}));
vi.mock("@/lib/auth/auth-store", () => ({
  useAuthStore: (seletor: (estadoAtual: AuthEstado) => unknown) => seletor(estado),
}));
vi.mock("@/lib/config/textos-provider", () => ({
  useTextos: () => ({
    atendimentos: {
      cabecalho: { outros: "Outros", voltar: "Voltar" },
      transferir: {
        titulo: "Transferir atendimento",
        descricao: "Escolha o destino",
        descricaoPotencial: "Potenciais são distribuídos pela IA ou pela gestão.",
        devolverParaIa: "Devolver para IA",
        assumirParaMim: "Assumir para mim",
        cancelar: "Cancelar",
        erro: "Não foi possível transferir",
        carregandoDestinos: "Carregando atendentes...",
        erroDestinos: "Não foi possível carregar os atendentes.",
        tentarNovamente: "Tentar novamente",
        semDestinos: "Nenhum atendente disponível.",
        erroPermissao: "Seu perfil não permite esta transferência.",
        erroDestino: "Este atendente não pode receber.",
        erroFinalizado: "Este atendimento já foi finalizado.",
        erroIndisponivel: "Esta conversa não está mais disponível.",
      },
    },
  }),
}));

import { DialogoTransferir } from "./dialogo-transferir";

const COLEGAS: DestinoMock[] = [
  { id: "ana-1", nome: "Ana Atendente" },
  { id: "bruno-1", nome: "Bruno Atendente" },
];

function abrir({
  status = "EM_ATENDIMENTO",
  responsavelId = "ana-1",
}: { status?: StatusAtendimento; responsavelId?: string | null } = {}) {
  return render(
    <DialogoTransferir
      atendimentoId="atendimento-1"
      status={status}
      responsavelId={responsavelId}
      aberto
      onFechar={vi.fn()}
    />,
  );
}

function comoAtendente(usuarioId = "ana-1") {
  estado.papel = "ATENDENTE";
  estado.usuarioId = usuarioId;
  definirCapacidadesDeTeste({ alcancaTodos: false });
}

describe("DialogoTransferir", () => {
  beforeEach(() => {
    estado.papel = "GESTOR";
    estado.usuarioId = "gestor-1";
    estado.destinos = { data: COLEGAS, isPending: false, isError: false, isSuccess: true, refetch: vi.fn() };
    estado.habilitada = undefined;
    estado.erro = null;
    estado.transferir.mockReset();
  });

  it("atendente transfere a própria conversa para um colega, sem se oferecer a si mesma", () => {
    comoAtendente();
    abrir();

    expect(screen.getByRole("button", { name: "Devolver para IA" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Assumir para mim" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Ana Atendente" })).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "Bruno Atendente" }));
    expect(estado.transferir).toHaveBeenCalledWith(
      { atendimentoId: "atendimento-1", paraAtendenteId: "bruno-1", destinoNome: "Bruno Atendente" },
      expect.objectContaining({ onSuccess: expect.any(Function) }),
    );
  });

  it("em um Potencial, atendente só assume para si: nenhum colega que o backend recusaria", () => {
    comoAtendente();
    abrir({ status: "EM_IA", responsavelId: null });

    expect(screen.getByText("Potenciais são distribuídos pela IA ou pela gestão.")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Bruno Atendente" })).not.toBeInTheDocument();
    // Já está com a IA: devolver não mudaria nada.
    expect(screen.queryByRole("button", { name: "Devolver para IA" })).not.toBeInTheDocument();
    expect(estado.habilitada).toBe(false);

    fireEvent.click(screen.getByRole("button", { name: "Assumir para mim" }));
    expect(estado.transferir).toHaveBeenCalledWith(
      { atendimentoId: "atendimento-1", paraAtendenteId: "ana-1", destinoNome: null },
      expect.objectContaining({ onSuccess: expect.any(Function) }),
    );
  });

  it("gestão distribui um Potencial para qualquer colega", () => {
    definirCapacidadesDeTeste({ alcancaTodos: true });
    abrir({ status: "EM_IA", responsavelId: null });

    expect(screen.getByRole("button", { name: "Ana Atendente" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Bruno Atendente" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Assumir para mim" })).not.toBeInTheDocument();
  });

  it("não oferece o responsável atual como destino", () => {
    abrir({ responsavelId: "ana-1" });

    expect(screen.queryByRole("button", { name: "Ana Atendente" })).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Bruno Atendente" })).toBeInTheDocument();
  });

  it("subgestora que não é a responsável pode assumir para si", () => {
    estado.papel = "SUBGESTOR";
    estado.usuarioId = "michele-1";
    abrir({ responsavelId: "ana-1" });

    fireEvent.click(screen.getByRole("button", { name: "Assumir para mim" }));
    expect(estado.transferir).toHaveBeenCalledWith(
      { atendimentoId: "atendimento-1", paraAtendenteId: "michele-1", destinoNome: null },
      expect.objectContaining({ onSuccess: expect.any(Function) }),
    );
  });

  it("agrupa subgestora em Outros e usa seu UUID ao selecionar", () => {
    estado.destinos.data = [
      { id: "bruno-1", nome: "Bruno Atendente", papel: "ATENDENTE" },
      { id: "michele-1", nome: "Michele Subgestora", papel: "SUBGESTOR" },
    ];
    abrir();

    expect(screen.queryByRole("button", { name: "Michele Subgestora" })).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Outros" }));
    fireEvent.click(screen.getByRole("button", { name: "Michele Subgestora" }));
    expect(estado.transferir).toHaveBeenCalledWith(
      { atendimentoId: "atendimento-1", paraAtendenteId: "michele-1", destinoNome: "Michele Subgestora" },
      expect.objectContaining({ onSuccess: expect.any(Function) }),
    );
  });

  it("mantem destino sem papel na lista principal para clientes antigos", () => {
    estado.destinos.data = [{ id: "legado-1", nome: "Destino legado" }];
    abrir();

    expect(screen.getByRole("button", { name: "Destino legado" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Outros" })).not.toBeInTheDocument();
  });

  it("mostra carregamento, erro com nova tentativa e lista vazia dos destinos", () => {
    estado.destinos = { data: undefined, isPending: true, isError: false, isSuccess: false, refetch: vi.fn() };
    const { rerender } = abrir();
    expect(screen.getByRole("status")).toHaveTextContent("Carregando atendentes...");

    estado.destinos = { data: undefined, isPending: false, isError: true, isSuccess: false, refetch: vi.fn() };
    rerender(
      <DialogoTransferir atendimentoId="atendimento-1" status="EM_ATENDIMENTO" responsavelId="ana-1" aberto onFechar={vi.fn()} />,
    );
    expect(screen.getByRole("alert")).toHaveTextContent("Não foi possível carregar os atendentes.");
    fireEvent.click(screen.getByRole("button", { name: "Tentar novamente" }));
    expect(estado.destinos.refetch).toHaveBeenCalledOnce();

    estado.destinos = { data: [{ id: "ana-1", nome: "Ana Atendente" }], isPending: false, isError: false, isSuccess: true, refetch: vi.fn() };
    rerender(
      <DialogoTransferir atendimentoId="atendimento-1" status="EM_ATENDIMENTO" responsavelId="ana-1" aberto onFechar={vi.fn()} />,
    );
    expect(screen.getByText("Nenhum atendente disponível.")).toBeInTheDocument();
  });

  it.each([
    [403, "Seu perfil não permite esta transferência."],
    [422, "Este atendente não pode receber."],
    [409, "Este atendimento já foi finalizado."],
    [404, "Esta conversa não está mais disponível."],
    [500, "Não foi possível transferir"],
  ])("recusa HTTP %i aparece em linguagem de operação, nunca o detalhe técnico", (status, texto) => {
    estado.erro = new ErroDeApi(status, { detail: "atendente nao pode escolher o destino de um potencial" }, "x");
    abrir();

    expect(screen.getByRole("alert")).toHaveTextContent(texto);
    expect(screen.queryByText(/nao pode escolher/)).not.toBeInTheDocument();
  });

  it("sem transferir, oferece só a devolução para a IA (nem assumir nem colegas)", () => {
    comoAtendente();
    definirCapacidadesDeTeste({ negadas: ["atendimentos.transferir"], alcancaTodos: false });
    abrir({ responsavelId: "bruno-1" });

    expect(screen.getByRole("button", { name: "Devolver para IA" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Assumir para mim" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Bruno Atendente" })).not.toBeInTheDocument();
    expect(estado.habilitada).toBe(false);
  });

  it("sem devolver para a IA, mantém os destinos e retira a devolução", () => {
    comoAtendente();
    definirCapacidadesDeTeste({ negadas: ["atendimentos.devolver_ia"], alcancaTodos: false });
    abrir();

    expect(screen.queryByRole("button", { name: "Devolver para IA" })).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Bruno Atendente" })).toBeInTheDocument();
  });

  it("sem transferir nem devolver, o diálogo não abre", () => {
    definirCapacidadesDeTeste({ negadas: ["atendimentos.transferir", "atendimentos.devolver_ia"] });
    abrir();

    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });
});
