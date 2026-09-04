import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { renderHook, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import type { ItemInbox } from "./types";

vi.mock("./api", () => ({
  listarInboxUnificada: vi.fn(),
  listarAtendimentos: vi.fn(),
  contarAtendimentosPorVisao: vi.fn(),
}));

import * as api from "./api";
import { useAtendimentos } from "./use-atendimentos";

const cliente = (id: string, hora: string): ItemInbox => ({
  tipo: "CLIENTE",
  atendimentoId: id,
  leadId: `lead-${id}`,
  leadNome: id,
  leadFotoUrl: null,
  leadEmpresa: null,
  canalTipo: "WHATSAPP",
  etapaId: null,
  etapaNome: null,
  etapaCor: null,
  status: "EM_ATENDIMENTO",
  atendenteId: null,
  atendenteNome: null,
  ultimaMensagemPreview: id,
  ultimaMensagemRemetenteTipo: null,
  ultimaMensagemEm: hora,
  ultimaMensagemDoLeadEm: null,
  naoLidas: 0,
});

function wrapper(cache: QueryClient) {
  return function Wrapper({ children }: { children: ReactNode }) {
    return <QueryClientProvider client={cache}>{children}</QueryClientProvider>;
  };
}

describe("useAtendimentos — paginação da inbox", () => {
  beforeEach(() => vi.clearAllMocks());

  it("consome o cursor e concatena páginas sem reordenar nem duplicar", async () => {
    vi.mocked(api.listarInboxUnificada)
      .mockResolvedValueOnce({ itens: [cliente("um", "2026-08-26T12:00:00Z")], proximoCursor: "cursor-1" })
      .mockResolvedValueOnce({ itens: [cliente("dois", "2026-08-26T11:00:00Z")], proximoCursor: null });
    const cache = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    const { result } = renderHook(() => useAtendimentos("TODOS"), { wrapper: wrapper(cache) });

    await waitFor(() => expect(result.current.data).toHaveLength(1));
    expect(result.current.hasNextPage).toBe(true);
    await result.current.fetchNextPage();
    await waitFor(() => expect(result.current.data).toHaveLength(2));

    expect(result.current.data?.map((item) => item.atendimentoId)).toEqual(["um", "dois"]);
    expect(api.listarInboxUnificada).toHaveBeenNthCalledWith(2, "TODOS", "cursor-1");
  });

  it("usa a inbox paginada também para FINALIZADOS", async () => {
    vi.mocked(api.listarInboxUnificada).mockResolvedValue({
      itens: [cliente("fim", "2026-08-26T12:00:00Z")],
      proximoCursor: null,
    });
    vi.mocked(api.listarAtendimentos).mockResolvedValue([]);
    const cache = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    const { result } = renderHook(() => useAtendimentos("FINALIZADOS"), { wrapper: wrapper(cache) });

    await waitFor(() => expect(result.current.data).toHaveLength(1));
    expect(api.listarInboxUnificada).toHaveBeenCalledWith("FINALIZADOS", null);
    expect(api.listarAtendimentos).not.toHaveBeenCalled();
  });

  it("usa a inbox unificada para ATIVOS, onde o chat interno também participa", async () => {
    vi.mocked(api.listarInboxUnificada).mockResolvedValue({
      itens: [cliente("ativo", "2026-08-26T12:00:00Z")],
      proximoCursor: null,
    });
    vi.mocked(api.listarAtendimentos).mockResolvedValue([]);
    const cache = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    const { result } = renderHook(() => useAtendimentos("ATIVOS"), { wrapper: wrapper(cache) });

    await waitFor(() => expect(result.current.data).toHaveLength(1));
    expect(api.listarInboxUnificada).toHaveBeenCalledWith("ATIVOS", null);
    expect(api.listarAtendimentos).not.toHaveBeenCalled();
  });

  it("mantém a inbox íntegra após invalidação sem misturar o cache da lista legada", async () => {
    vi.mocked(api.listarInboxUnificada).mockResolvedValue({
      itens: [cliente("um", "2026-08-26T12:00:00Z")],
      proximoCursor: null,
    });
    vi.mocked(api.listarAtendimentos).mockResolvedValue([]);
    const cache = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    const { result } = renderHook(() => useAtendimentos("TODOS"), { wrapper: wrapper(cache) });

    await waitFor(() => expect(result.current.data?.[0]?.tipo).toBe("CLIENTE"));
    await cache.invalidateQueries({ queryKey: ["atendimentos"] });
    await waitFor(() => expect(api.listarInboxUnificada).toHaveBeenCalledTimes(2));

    expect(result.current.data?.[0]?.tipo).toBe("CLIENTE");
    expect(api.listarAtendimentos).not.toHaveBeenCalled();
  });

  it("descarta entrada ausente sem derrubar toda a lista", async () => {
    vi.mocked(api.listarInboxUnificada).mockResolvedValue({
      itens: [undefined, cliente("valido", "2026-08-26T12:00:00Z")],
      proximoCursor: null,
    } as unknown as Awaited<ReturnType<typeof api.listarInboxUnificada>>);
    const cache = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    const { result } = renderHook(() => useAtendimentos("TODOS"), { wrapper: wrapper(cache) });

    await waitFor(() => expect(result.current.data).toHaveLength(1));
    expect(result.current.data?.[0]?.atendimentoId).toBe("valido");
  });
});
