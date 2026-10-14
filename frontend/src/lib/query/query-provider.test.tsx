import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { useMutation, useQueryClient, onlineManager } from "@tanstack/react-query";
import { afterEach, describe, expect, it, vi } from "vitest";
import { ErroDeApi } from "@/lib/api/errors";
import { QueryProvider } from "./query-provider";

afterEach(() => onlineManager.setOnline(true));

describe("política de recuperação", () => {
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
