import { fireEvent, render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

type AuthEstado = { papel: string; usuarioId: string };

const estado = vi.hoisted(() => ({
  papel: "GESTOR",
  usuarioId: "gestor-1",
  destinos: [
    { id: "ana-1", nome: "Ana Atendente" },
    { id: "bruno-1", nome: "Bruno Atendente" },
  ],
  erro: null as Error | null,
  transferir: vi.fn(),
}));

vi.mock("@tanstack/react-query", () => ({
  useQuery: () => ({ data: estado.destinos, isLoading: false, isError: false }),
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
      transferir: {
        titulo: "Transferir atendimento",
        descricao: "Escolha o destino",
        devolverParaIa: "Devolver para IA",
        assumirParaMim: "Assumir para mim",
        cancelar: "Cancelar",
        erro: "Não foi possível transferir",
      },
    },
  }),
}));

import { DialogoTransferir } from "./dialogo-transferir";

describe("DialogoTransferir", () => {
  beforeEach(() => {
    estado.papel = "GESTOR";
    estado.usuarioId = "gestor-1";
    estado.destinos = [
      { id: "ana-1", nome: "Ana Atendente" },
      { id: "bruno-1", nome: "Bruno Atendente" },
    ];
    estado.erro = null;
    estado.transferir.mockReset();
  });

  it("nao oferece assumir para mim a gestor", () => {
    render(<DialogoTransferir atendimentoId="atendimento-1" aberto onFechar={vi.fn()} />);

    expect(screen.queryByRole("button", { name: "Assumir para mim" })).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Ana Atendente" })).toBeInTheDocument();
  });

  it("oferece assumir para mim a subgestora autenticada", () => {
    estado.papel = "SUBGESTOR";
    estado.usuarioId = "michele-1";
    estado.destinos = [{ id: "michele-1", nome: "Michele Subgestora" }];

    render(<DialogoTransferir atendimentoId="atendimento-1" aberto onFechar={vi.fn()} />);

    fireEvent.click(screen.getByRole("button", { name: "Assumir para mim" }));
    expect(estado.transferir).toHaveBeenCalledWith(
      { atendimentoId: "atendimento-1", paraAtendenteId: "michele-1" },
      expect.objectContaining({ onSuccess: expect.any(Function) }),
    );
  });

  it("oferece assumir para mim somente a atendente autenticada", () => {
    estado.papel = "ATENDENTE";
    estado.usuarioId = "ana-1";
    estado.destinos = [];

    render(<DialogoTransferir atendimentoId="atendimento-1" aberto onFechar={vi.fn()} />);

    fireEvent.click(screen.getByRole("button", { name: "Assumir para mim" }));
    expect(estado.transferir).toHaveBeenCalledWith(
      { atendimentoId: "atendimento-1", paraAtendenteId: "ana-1" },
      expect.objectContaining({ onSuccess: expect.any(Function) }),
    );
  });

  it("lista colegas para atendente e mantem devolver para a IA", () => {
    estado.papel = "ATENDENTE";
    estado.usuarioId = "ana-1";

    render(<DialogoTransferir atendimentoId="atendimento-1" aberto onFechar={vi.fn()} />);

    expect(screen.getByRole("button", { name: "Devolver para IA" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Assumir para mim" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Bruno Atendente" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Ana Atendente" })).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "Bruno Atendente" }));
    expect(estado.transferir).toHaveBeenCalledWith(
      { atendimentoId: "atendimento-1", paraAtendenteId: "bruno-1" },
      expect.objectContaining({ onSuccess: expect.any(Function) }),
    );
  });

  it("exibe o detalhe RFC 7807 da recusa do backend", () => {
    estado.erro = new Error("destino 00000000-0000-0000-0000-000000000001 recusado: inativo");

    render(<DialogoTransferir atendimentoId="atendimento-1" aberto onFechar={vi.fn()} />);

    expect(screen.getByText(/destino .* recusado: inativo/)).toBeInTheDocument();
  });
});
