import { renderHook, waitFor, act } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import type { ReactNode } from "react";
import { describe, expect, it, vi } from "vitest";

const finalizarAtendimento = vi.hoisted(() => vi.fn());

vi.mock("./api", () => ({
  contarAtendimentosFinalizaveis: vi.fn(),
  finalizarAtendimento,
  finalizarAtendimentosVisiveis: vi.fn(),
  transferirAtendimento: vi.fn(),
}));

import { useFinalizarAtendimento } from "./use-transferir-finalizar";

function wrapper({ children }: { children: ReactNode }) {
  return (
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      {children}
    </QueryClientProvider>
  );
}

describe("useFinalizarAtendimento", () => {
  it("propaga o resumo somente depois da finalização aceita", async () => {
    const resumo = { id: "atendimento-1", status: "FINALIZADO" as const, atendenteId: "usuario-1" };
    finalizarAtendimento.mockResolvedValueOnce(resumo);
    const onAtendimentoFinalizado = vi.fn();
    const { result } = renderHook(
      () => useFinalizarAtendimento(onAtendimentoFinalizado),
      { wrapper },
    );

    act(() => result.current.mutate("atendimento-1"));

    await waitFor(() => expect(onAtendimentoFinalizado).toHaveBeenCalledWith(resumo));
    expect(finalizarAtendimento).toHaveBeenCalledWith("atendimento-1");
  });
});
