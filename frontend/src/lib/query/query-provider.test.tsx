import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { useMutation, useQuery, useQueryClient, onlineManager } from "@tanstack/react-query";
import { afterEach, describe, expect, it, vi } from "vitest";
import { ErroDeApi } from "@/lib/api/errors";
import { QueryProvider } from "./query-provider";
import { useAuthStore } from "@/lib/auth/auth-store";
import type { QueryClient } from "@tanstack/react-query";

afterEach(() => {
  onlineManager.setOnline(true);
  useAuthStore.setState({ usuarioId: null, papel: null, accessToken: null, expiraEm: null });
});

describe("política de recuperação", () => {
  it("outro usuario ou papel recebe cache vazio e resposta antiga nao preenche a nova sessao", () => {
    let atual: QueryClient;
    function Capturar() { atual=useQueryClient(); return null; }
    useAuthStore.setState({usuarioId:"adm",papel:"ADMINISTRADOR"});
    render(<QueryProvider><Capturar/></QueryProvider>);
    const antigo=atual!;
    antigo.setQueryData(["equipe"],[{id:"conta-teste",usuarioTeste:true}]);
    act(()=>useAuthStore.setState({usuarioId:"gestor",papel:"GESTOR"}));
    expect(atual!).not.toBe(antigo);
    expect(atual!.getQueryData(["equipe"])).toBeUndefined();
    antigo.setQueryData(["equipe"],[{id:"resposta-atrasada"}]);
    expect(atual!.getQueryData(["equipe"])).toBeUndefined();
    const gestor=atual!;
    act(()=>useAuthStore.setState({papel:"ATENDENTE"}));
    expect(atual!).not.toBe(gestor);
  });
  it("refresh do token no mesmo usuario e papel preserva o cache autorizado", () => {
    let atual: QueryClient;
    function Capturar() { atual = useQueryClient(); return null; }
    useAuthStore.setState({ usuarioId: "convidado-b", papel: "ATENDENTE" });
    render(<QueryProvider><Capturar /></QueryProvider>);
    const anterior = atual!;
    anterior.setQueryData(["lead", "lead-compartilhado"], "Ficha autorizada");
    act(() => useAuthStore.setState({ accessToken: "refresh-sintetico", expiraEm: 123 }));
    expect(atual!).toBe(anterior);
    expect(atual!.getQueryData(["lead", "lead-compartilhado"])).toBe("Ficha autorizada");
  });

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
