import { render, screen } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { describe, expect, it, vi } from "vitest";

vi.mock("@/lib/lead/use-url-assinada-da-midia", () => ({
  useUrlAssinadaDaMidia: () => ({ data: { url: "/api/v1/leads/x/midias/y/download" } }),
}));

import { MidiaComUrlAssinada } from "./midia-com-url-assinada";

describe("MidiaComUrlAssinada", () => {
  it("não coloca caminho /api/ protegido em src", () => {
    const cliente = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    render(
      <QueryClientProvider client={cliente}>
        <MidiaComUrlAssinada
          leadId="lead-1"
          mensagemId="msg-1"
          tipo="IMAGEM"
          alt="Foto"
          rotuloAudio="Áudio"
        />
      </QueryClientProvider>,
    );

    expect(screen.queryByRole("img")).toBeNull();
    expect(document.querySelector("[src*='/api/']")).toBeNull();
  });
});
