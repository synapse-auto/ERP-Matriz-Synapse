import { StrictMode, useEffect } from "react";
import { render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { apiFetch } from "@/lib/api/http-client";

import { useAuthStore } from "./auth-store";

let caminho = "/login";
const substituir = vi.fn();

vi.mock("next/navigation", () => ({
  usePathname: () => caminho,
  useRouter: () => ({ replace: substituir }),
}));

import { AuthProvider } from "./auth-provider";

describe("AuthProvider", () => {
  beforeEach(() => {
    caminho = "/login";
    substituir.mockReset();
    vi.unstubAllGlobals();
    useAuthStore.setState({
      accessToken: null,
      expiraEm: null,
      email: null,
      papel: null,
      status: "carregando",
    });
  });

  it("nao chama refresh na tela de login sem sessao", async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal("fetch", fetchMock);

    render(
      <AuthProvider>
        <span>Login</span>
      </AuthProvider>,
    );

    await waitFor(() => expect(fetchMock).not.toHaveBeenCalled());
    expect(substituir).not.toHaveBeenCalled();
  });

  it("deduplica a hidratacao mesmo com efeitos duplicados do StrictMode", async () => {
    caminho = "/agenda";
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      json: async () => ({ accessToken: "token-de-teste", expiraEmSegundos: 900 }),
    });
    vi.stubGlobal("fetch", fetchMock);

    render(
      <StrictMode>
        <AuthProvider>
          <span>Agenda</span>
        </AuthProvider>
      </StrictMode>,
    );

    await waitFor(() => expect(useAuthStore.getState().status).toBe("autenticado"));
    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(fetchMock).toHaveBeenCalledWith("/api/auth/refresh", { method: "POST" });
  });

  it("hidrata a sessao ao sair de login para uma rota protegida", async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      json: async () => ({ accessToken: "token-apos-navegacao", expiraEmSegundos: 900 }),
    });
    vi.stubGlobal("fetch", fetchMock);
    const { rerender } = render(
      <AuthProvider>
        <span>Conteudo</span>
      </AuthProvider>,
    );
    expect(fetchMock).not.toHaveBeenCalled();

    caminho = "/agenda";
    rerender(
      <AuthProvider>
        <span>Conteudo</span>
      </AuthProvider>,
    );

    await waitFor(() => expect(useAuthStore.getState().status).toBe("autenticado"));
    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(fetchMock).toHaveBeenCalledWith("/api/auth/refresh", { method: "POST" });
  });

  it("so monta consultas protegidas depois de restaurar o token em memoria", async () => {
    caminho = "/agenda";
    let concluirRefresh!: (resposta: {
      ok: boolean;
      json: () => Promise<{ accessToken: string; expiraEmSegundos: number }>;
    }) => void;
    const refreshPendente = new Promise<{
      ok: boolean;
      json: () => Promise<{ accessToken: string; expiraEmSegundos: number }>;
    }>((resolver) => {
      concluirRefresh = resolver;
    });
    const fetchMock = vi.fn((entrada: string | URL | Request, opcoes?: RequestInit) => {
      const caminhoDaRequisicao = String(entrada);
      if (caminhoDaRequisicao === "/api/auth/refresh") {
        return refreshPendente;
      }

      const token = new Headers(opcoes?.headers).get("Authorization");
      return Promise.resolve({
        ok: token === "Bearer token-restaurado",
        status: token === "Bearer token-restaurado" ? 200 : 401,
        json: async () => (caminhoDaRequisicao.endsWith("/me") ? { nome: "Admin" } : []),
      });
    });
    vi.stubGlobal("fetch", fetchMock);

    function ConsultasDoShell() {
      useEffect(() => {
        void Promise.all([
          apiFetch<string[]>("/api/v1/config/features"),
          apiFetch<{ nome: string }>("/api/v1/me"),
        ]);
      }, []);
      return <span>Shell montado</span>;
    }

    render(
      <AuthProvider>
        <ConsultasDoShell />
      </AuthProvider>,
    );

    expect(screen.queryByText("Shell montado")).not.toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(fetchMock).toHaveBeenLastCalledWith("/api/auth/refresh", { method: "POST" });

    concluirRefresh({
      ok: true,
      json: async () => ({ accessToken: "token-restaurado", expiraEmSegundos: 900 }),
    });

    await screen.findByText("Shell montado");
    await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(3));
    for (const [, opcoes] of fetchMock.mock.calls.slice(1)) {
      expect(new Headers(opcoes?.headers).get("Authorization")).toBe("Bearer token-restaurado");
    }
  });
});
