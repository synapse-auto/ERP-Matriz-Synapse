import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { renderHook, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import * as api from "./api";
import { useEnviarMensagem } from "./use-enviar-mensagem";
import type { MensagemResposta } from "./types";

vi.mock("./api", () => ({
  enviarMensagem: vi.fn(),
}));

function criarWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  function Wrapper({ children }: { children: React.ReactNode }) {
    return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>;
  }
  return { queryClient, Wrapper };
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
    const { result } = renderHook(() => useEnviarMensagem(), { wrapper: Wrapper });

    result.current.mutate({ atendimentoId: "at-1", leadId: "lead-1", conteudo: "olá" });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    const mensagens = queryClient.getQueryData<MensagemResposta[]>(["mensagens", "at-1"]);
    expect(mensagens).toHaveLength(1);
    expect(mensagens?.[0].id).toBe("msg-1");
    expect(mensagens?.[0].statusEntrega).toBe("PENDENTE");
  });

  it("falha de rede: a mensagem otimista transita para FALHOU, sem duplicar entrada", async () => {
    vi.mocked(api.enviarMensagem).mockRejectedValue(new Error("falha de rede"));
    const { queryClient, Wrapper } = criarWrapper();
    const { result } = renderHook(() => useEnviarMensagem(), { wrapper: Wrapper });

    result.current.mutate({ atendimentoId: "at-2", leadId: "lead-2", conteudo: "olá" });

    await waitFor(() => expect(result.current.isError).toBe(true));

    const mensagens = queryClient.getQueryData<MensagemResposta[]>(["mensagens", "at-2"]);
    expect(mensagens).toHaveLength(1);
    expect(mensagens?.[0].statusEntrega).toBe("FALHOU");
  });
});
