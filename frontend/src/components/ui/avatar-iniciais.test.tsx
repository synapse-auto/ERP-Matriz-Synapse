import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

const apiFetchBlob = vi.fn();
const URLOriginal = URL;

vi.mock("@/lib/api/http-client", () => ({
  apiFetchBlob: (...argumentos: unknown[]) => apiFetchBlob(...argumentos),
}));

import { AvatarIniciais } from "./avatar-iniciais";

function renderizar(fotoUrl?: string | null) {
  const cliente = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return render(
    <QueryClientProvider client={cliente}>
      <AvatarIniciais
        id="lead-1"
        nome="Maria Silva"
        fotoUrl={fotoUrl}
        fotoAlt="Maria Silva"
      />
    </QueryClientProvider>,
  );
}

describe("AvatarIniciais", () => {
  beforeEach(() => {
    apiFetchBlob.mockReset();
    class URLComBlob extends URLOriginal {
      static createObjectURL = vi.fn(() => "blob:foto-autenticada");
      static revokeObjectURL = vi.fn();
    }
    vi.stubGlobal("URL", URLComBlob);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("carrega caminho relativo pelo cliente autenticado", async () => {
    apiFetchBlob.mockResolvedValue(new Blob(["png"], { type: "image/png" }));

    renderizar("/api/v1/leads/lead-1/foto");

    expect(screen.getByText("MS")).toBeInTheDocument();
    await waitFor(() => {
      expect(screen.getByRole("img", { name: "Maria Silva" })).toHaveAttribute(
        "src",
        "blob:foto-autenticada",
      );
    });
    expect(apiFetchBlob).toHaveBeenCalledWith("/api/v1/leads/lead-1/foto");
  });

  it("mantem URL http externa direta sem enviar o token do CRM", () => {
    renderizar("https://cdn.example/foto.webp");

    expect(screen.getByRole("img", { name: "Maria Silva" })).toHaveAttribute(
      "src",
      "https://cdn.example/foto.webp",
    );
    expect(apiFetchBlob).not.toHaveBeenCalled();
  });

  it("cai nas iniciais quando nao ha foto ou o esquema e inseguro", () => {
    const { rerender } = renderizar(null);
    expect(screen.getByText("MS")).toBeInTheDocument();

    const cliente = new QueryClient();
    rerender(
      <QueryClientProvider client={cliente}>
        <AvatarIniciais
          id="lead-1"
          nome="Maria Silva"
          fotoUrl="javascript:alert(1)"
          fotoAlt="Maria Silva"
        />
      </QueryClientProvider>,
    );
    expect(screen.getByText("MS")).toBeInTheDocument();
    expect(screen.queryByRole("img")).not.toBeInTheDocument();
    expect(apiFetchBlob).not.toHaveBeenCalled();
  });
});
