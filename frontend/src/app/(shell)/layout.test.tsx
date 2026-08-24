import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

vi.mock("next/headers", () => ({
  cookies: async () => ({ has: () => true }),
}));

vi.mock("next/navigation", () => ({ redirect: vi.fn() }));

vi.mock("@/components/shell/sidebar", () => ({
  Sidebar: () => <aside>Menu</aside>,
}));

vi.mock("@/components/auth/sinalizador-shell-pronto", () => ({
  SinalizadorShellPronto: () => null,
}));

vi.mock("@/lib/config/fetch-config", () => ({
  buscarTema: async () => ({}),
  temaParaCssVariaveis: () => ":root{--primary:#123456}",
}));

import ShellLayout from "./layout";

describe("superfície compartilhada do shell", () => {
  it("mantém a superfície sem moldura e o scroll interno do shell", async () => {
    render(await ShellLayout({ children: <div>Conteúdo</div> }));

    const superficie = screen.getByText("Conteúdo").closest("main");
    expect(superficie).toHaveClass("overflow-y-auto");
    expect(superficie).not.toHaveClass("bg-card", "rounded-lg", "shadow-sm");
    expect(superficie?.parentElement).not.toHaveClass("p-5");
    expect(superficie?.closest('[data-slot="page-canvas"]')).toHaveClass(
      "bg-[var(--fundo-canvas)]",
      "overflow-hidden",
    );
  });

  it("injeta o tema da instância também na rota de troca de senha do shell", async () => {
    render(await ShellLayout({ children: <div>Trocar senha</div> }));

    expect(document.querySelector("style")).toHaveTextContent("--primary:#123456");
  });
});
