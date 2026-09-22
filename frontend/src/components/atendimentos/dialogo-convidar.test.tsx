import { fireEvent, render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

const estado = vi.hoisted(() => ({
  destinos: [
    { id: "ana-1", nome: "Ana Atendente", papel: "ATENDENTE" as const },
    { id: "michele-1", nome: "Michele Subgestora", papel: "SUBGESTOR" as const },
  ] as Array<{ id: string; nome: string; papel?: "ATENDENTE" | "SUBGESTOR" }>,
  convidar: vi.fn(),
}));

vi.mock("@tanstack/react-query", () => ({
  useQuery: () => ({ data: estado.destinos, isLoading: false, isError: false }),
  useMutation: () => ({ mutate: estado.convidar, isPending: false, isError: false }),
}));
vi.mock("@/lib/atendimento/api", () => ({
  listarDestinosDeTransferencia: vi.fn(),
  convidarParaAtendimento: vi.fn(),
}));
vi.mock("@/lib/auth/auth-store", () => ({
  useAuthStore: (seletor: (estadoAtual: { usuarioId: string }) => unknown) => seletor({ usuarioId: "gestor-1" }),
}));
vi.mock("@/lib/config/textos-provider", () => ({
  useTextos: () => ({
    atendimentos: {
      cabecalho: {
        outros: "Outros",
        voltar: "Voltar",
        convidarTitulo: "Convidar para atendimento",
        convidarDescricao: "Escolha um atendente",
        convidarCarregando: "Carregando",
        convidarVazio: "Nenhum atendente",
        convidarErro: "Erro",
      },
      transferir: { cancelar: "Cancelar" },
    },
  }),
}));

import { DialogoConvidar } from "./dialogo-convidar";

describe("DialogoConvidar", () => {
  beforeEach(() => {
    estado.destinos = [
      { id: "ana-1", nome: "Ana Atendente", papel: "ATENDENTE" },
      { id: "michele-1", nome: "Michele Subgestora", papel: "SUBGESTOR" },
    ];
    estado.convidar.mockReset();
  });

  it("mantem atendentes na lista principal e agrupa subgestor em Outros", () => {
    render(<DialogoConvidar atendimentoId="atendimento-1" participantes={[]} aberto onFechar={vi.fn()} />);

    expect(screen.getByRole("button", { name: "Ana Atendente" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Outros" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Michele Subgestora" })).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "Outros" }));
    fireEvent.click(screen.getByRole("button", { name: "Michele Subgestora" }));

    expect(estado.convidar).toHaveBeenCalledWith("michele-1");
  });

  it("nao mostra Outros sem subgestor retornado e preserva destino legado", () => {
    estado.destinos = [{ id: "ana-1", nome: "Ana Atendente", papel: undefined }];

    render(<DialogoConvidar atendimentoId="atendimento-1" participantes={[]} aberto onFechar={vi.fn()} />);

    expect(screen.getByRole("button", { name: "Ana Atendente" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Outros" })).not.toBeInTheDocument();
  });
});
