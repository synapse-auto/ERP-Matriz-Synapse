import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { useMutation, useQuery, useQueryClient, onlineManager } from "@tanstack/react-query";
import { afterEach, describe, expect, it, vi } from "vitest";
import { ErroDeApi } from "@/lib/api/errors";
import { QueryProvider } from "./query-provider";
import { useAuthStore } from "@/lib/auth/auth-store";

afterEach(() => {
  onlineManager.setOnline(true);
  useAuthStore.setState({ usuarioId: null, papel: null });
});

describe("política de recuperação", () => {
  it("terceiro não reaproveita ficha autorizada no cache da conta anterior", async () => {
    useAuthStore.setState({ usuarioId: "convidado-b", papel: "ATENDENTE" });
    const ler = vi.fn(async () => {
      if (useAuthStore.getState().usuarioId !== "convidado-b") throw new ErroDeApi(404, null, "indisponível");
      return "Ficha privada do convite";
    });
    function Ficha() {
      const consulta = useQuery({ queryKey: ["lead", "lead-compartilhado"], queryFn: ler, retry: false });
      return <div>{consulta.data}{consulta.isError && <span>Sem acesso</span>}</div>;
    }
    render(<QueryProvider><Ficha /></QueryProvider>);
    await screen.findByText("Ficha privada do convite");
    await act(async () => useAuthStore.setState({ usuarioId: "terceiro-c" }));
    expect(screen.queryByText("Ficha privada do convite")).not.toBeInTheDocument();
    await screen.findByText("Sem acesso");
    expect(ler).toHaveBeenCalledTimes(2);
  });

  it("comando offline falha uma vez e não reenvia ao reconectar", async () => {
    onlineManager.setOnline(false);
    const enviar = vi.fn().mockRejectedValue(new TypeError());
    function Acao() {
      const mutation = useMutation({ mutationFn: enviar });
      return <><button onClick={() => mutation.mutate(undefined)}>Enviar</button>{mutation.isError && <span>Falhou</span>}</>;
    }
    render(<QueryProvider><Acao /></QueryProvider>);
    fireEvent.click(screen.getByRole("button"));
    await screen.findByText("Falhou");
    onlineManager.setOnline(true);
    await waitFor(() => expect(enviar).toHaveBeenCalledTimes(1));
  });
  it("401/403 não recebem retry e demais leituras mantêm somente uma tentativa extra", () => {
    function Politica() {
      const retry = useQueryClient().getDefaultOptions().queries!.retry as (vezes: number, erro: unknown) => boolean;
      expect(retry(0, new ErroDeApi(401, null, ""))).toBe(false);
      expect(retry(0, new ErroDeApi(403, null, ""))).toBe(false);
      expect(retry(0, new ErroDeApi(503, null, ""))).toBe(true);
      expect(retry(1, new ErroDeApi(503, null, ""))).toBe(false);
      return null;
    }
    render(<QueryProvider><Politica /></QueryProvider>);
  });
});
