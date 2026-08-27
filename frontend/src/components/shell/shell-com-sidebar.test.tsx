import { act, render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

const estadoSidebar = vi.hoisted(() => ({ retraida: false }));

vi.mock("@/components/shell/sidebar", () => ({
  Sidebar: ({ retraida }: { retraida: boolean }) => {
    estadoSidebar.retraida = retraida;
    return <aside data-testid="sidebar" data-state={retraida ? "collapsed" : "expanded"} />;
  },
}));

vi.mock("@/components/auth/sinalizador-shell-pronto", () => ({
  SinalizadorShellPronto: () => null,
}));

import { ShellComSidebar } from "./shell-com-sidebar";

describe("ShellComSidebar", () => {
  let telaEstreita = false;
  let notificarMudanca: (() => void) | undefined;

  beforeEach(() => {
    telaEstreita = false;
    notificarMudanca = undefined;
    Object.defineProperty(window, "matchMedia", {
      configurable: true,
      value: vi.fn(() => ({
        matches: telaEstreita,
        addEventListener: (_evento: string, listener: () => void) => {
          notificarMudanca = listener;
        },
        removeEventListener: vi.fn(),
      })),
    });
  });

  it("mantem a sidebar expandida em viewport largo", () => {
    render(
      <ShellComSidebar>
        <p>Conteúdo</p>
      </ShellComSidebar>,
    );

    expect(screen.getByTestId("sidebar")).toHaveAttribute("data-state", "expanded");
  });

  it("retrai a sidebar quando o viewport fica estreito", () => {
    render(
      <ShellComSidebar>
        <p>Conteúdo</p>
      </ShellComSidebar>,
    );

    telaEstreita = true;
    act(() => notificarMudanca?.());

    expect(screen.getByTestId("sidebar")).toHaveAttribute("data-state", "collapsed");
    expect(estadoSidebar.retraida).toBe(true);
  });
});
