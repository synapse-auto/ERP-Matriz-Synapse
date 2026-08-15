import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, renderHook, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import * as api from "./api";
import type { DadosDoHistorico } from "./cache-mensagens";
import type { ConexaoTempoReal } from "./tempo-real";
import { useEnviarMensagem } from "./use-enviar-mensagem";
import { useMensagens } from "./use-mensagens";
import type { MensagemResposta } from "./types";

vi.mock("./api", () => ({
  enviarMensagem: vi.fn(),
  paginaMensagens: vi.fn(),
  mensagensDesde: vi.fn(),
}));

function criarWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false, staleTime: Number.POSITIVE_INFINITY },
      mutations: { retry: false },
    },
  });
  function Wrapper({ children }: { children: React.ReactNode }) {
    return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>;
  }
  return { queryClient, Wrapper };
}

function prepararHistorico(
  queryClient: QueryClient,
  atendimentoId: string,
  mensagens: MensagemResposta[] = [],
) {
  queryClient.setQueryData<DadosDoHistorico>(["mensagens", atendimentoId], {
    pages: [{ mensagens, proximoCursor: null }],
    pageParams: [null],
  });
}

function mensagensDoHistorico(queryClient: QueryClient, atendimentoId: string) {
  return queryClient.getQueryData<DadosDoHistorico>(["mensagens", atendimentoId])?.pages[0]
    .mensagens;
}

describe("useEnviarMensagem", () => {
  beforeEach(() => {
    vi.resetAllMocks();
  });

  it("sucesso: insere no cache a mensagem real devolvida pelo backend, com status PENDENTE", async () => {
    vi.mocked(api.enviarMensagem).mockResolvedValue({
      atendimentoId: "at-1",
      mensagemId: "msg-1",
      statusEntrega: "PENDENTE",
      enviadoEm: "2026-01-01T00:00:00Z",
      transferiuOLead: false,
    });
    const { queryClient, Wrapper } = criarWrapper();
    prepararHistorico(queryClient, "at-1");
    const { result } = renderHook(() => useEnviarMensagem(), { wrapper: Wrapper });

    result.current.mutate({ atendimentoId: "at-1", leadId: "lead-1", conteudo: "olá" });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    const mensagens = mensagensDoHistorico(queryClient, "at-1");
    expect(mensagens).toHaveLength(1);
    expect(mensagens?.[0].id).toBe("msg-1");
    expect(mensagens?.[0].statusEntrega).toBe("PENDENTE");
  });

  it("falha de rede: a mensagem otimista transita para FALHOU, sem duplicar entrada", async () => {
    vi.mocked(api.enviarMensagem).mockRejectedValue(new Error("falha de rede"));
    const { queryClient, Wrapper } = criarWrapper();
    prepararHistorico(queryClient, "at-2");
    const { result } = renderHook(() => useEnviarMensagem(), { wrapper: Wrapper });

    result.current.mutate({ atendimentoId: "at-2", leadId: "lead-2", conteudo: "olá" });

    await waitFor(() => expect(result.current.isError).toBe(true));

    const mensagens = mensagensDoHistorico(queryClient, "at-2");
    expect(mensagens).toHaveLength(1);
    expect(mensagens?.[0].statusEntrega).toBe("FALHOU");
  });

  it("a otimista PENDENTE aparece no resultado consumido pela tela via useMensagens", async () => {
    vi.mocked(api.enviarMensagem).mockImplementation(() => new Promise(() => {}));
    const { queryClient, Wrapper } = criarWrapper();
    prepararHistorico(queryClient, "at-3");
    const conexao = {
      abrirConversa: vi.fn(),
      fecharConversa: vi.fn(),
    } as unknown as ConexaoTempoReal;

    const { result } = renderHook(
      () => ({
        envio: useEnviarMensagem(),
        historico: useMensagens("at-3", conexao, "desconectado"),
      }),
      { wrapper: Wrapper },
    );

    act(() =>
      result.current.envio.mutate({
        atendimentoId: "at-3",
        leadId: "lead-3",
        conteudo: "mensagem visível",
      }),
    );

    await waitFor(() => {
      expect(result.current.historico.data).toHaveLength(1);
      expect(result.current.historico.data[0]).toMatchObject({
        conteudo: "mensagem visível",
        statusEntrega: "PENDENTE",
      });
    });
  });
});
