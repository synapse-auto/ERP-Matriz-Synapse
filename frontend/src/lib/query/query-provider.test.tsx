import { render, screen, waitFor, act } from "@testing-library/react";
import { useQuery, useQueryClient, type QueryClient } from "@tanstack/react-query";
import { afterEach, describe, expect, it, vi } from "vitest";

import { chaveTemplatesWhatsApp } from "@/lib/atendimento/consulta-templates-whatsapp";
import { useAuthStore } from "@/lib/auth/auth-store";

import { deveLimparCacheAoTrocarSessao, QueryProvider } from "./query-provider";

describe("deveLimparCacheAoTrocarSessao", () => {
  it.each([
    [null, "a", false],
    ["a", "a", false],
    ["a", null, true],
    ["a", "b", true],
  ])("de %s para %s limpa? %s", (anterior, atual, esperado) => {
    expect(deveLimparCacheAoTrocarSessao(anterior, atual)).toBe(esperado);
  });
});

describe("QueryProvider — troca de sessão", () => {
  afterEach(() => {
    useAuthStore.setState({ usuarioId: null });
  });

  it("descarta o cache da sessão anterior ao trocar de usuário, e só então", async () => {
    let cliente: QueryClient | undefined;
    function Sonda() {
      cliente = useQueryClient();
      const consulta = useQuery({
        queryKey: chaveTemplatesWhatsApp("admin-1"),
        queryFn: () => Promise.resolve(["aviso_interno_cliente"]),
        staleTime: Infinity,
      });
      return <p>{consulta.data?.join(",") ?? "carregando"}</p>;
    }
    useAuthStore.setState({ usuarioId: "admin-1" });
    render(
      <QueryProvider>
        <Sonda />
      </QueryProvider>,
    );
    expect(await screen.findByText("aviso_interno_cliente")).toBeInTheDocument();

    // Renovar o token do mesmo usuário não mexe no cache.
    act(() => useAuthStore.setState({ usuarioId: "admin-1", accessToken: "renovado" }));
    expect(cliente?.getQueryData(chaveTemplatesWhatsApp("admin-1"))).toEqual(["aviso_interno_cliente"]);

    const limpar = vi.spyOn(cliente as QueryClient, "clear");
    act(() => useAuthStore.setState({ usuarioId: "atendente-1" }));
    await waitFor(() => expect(limpar).toHaveBeenCalledTimes(1));
    expect(cliente?.getQueryCache().findAll({ queryKey: chaveTemplatesWhatsApp("admin-1") })).toHaveLength(0);
  });
});
