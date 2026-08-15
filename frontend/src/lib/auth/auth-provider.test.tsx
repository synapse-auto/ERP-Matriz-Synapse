import { StrictMode } from "react";
import { render, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

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
});
