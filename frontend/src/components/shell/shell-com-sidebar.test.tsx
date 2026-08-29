"use client";

import { act, fireEvent, render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

const estadoSidebar = vi.hoisted(() => ({
  retraida: true,
  fixada: false,
  onAlternar: undefined as undefined | (() => void),
  onPonteiroEntrar: undefined as undefined | (() => void),
  onPonteiroSair: undefined as undefined | (() => void),
  onFocoDentro: undefined as undefined | (() => void),
  onFocoFora: undefined as undefined | (() => void),
}));

vi.mock("@/components/shell/sidebar", () => ({
  Sidebar: ({
    retraida,
    fixada,
    onAlternar,
    onPonteiroEntrar,
    onPonteiroSair,
    onFocoDentro,
    onFocoFora,
  }: {
    retraida: boolean;
    fixada?: boolean;
    onAlternar: () => void;
    onPonteiroEntrar?: () => void;
    onPonteiroSair?: () => void;
    onFocoDentro?: () => void;
    onFocoFora?: () => void;
  }) => {
    estadoSidebar.retraida = retraida;
    estadoSidebar.fixada = Boolean(fixada);
    estadoSidebar.onAlternar = onAlternar;
    estadoSidebar.onPonteiroEntrar = onPonteiroEntrar;
    estadoSidebar.onPonteiroSair = onPonteiroSair;
    estadoSidebar.onFocoDentro = onFocoDentro;
    estadoSidebar.onFocoFora = onFocoFora;
    return (
      <aside
        data-testid="sidebar"
        data-state={retraida ? "collapsed" : "expanded"}
        data-fixada={fixada ? "true" : "false"}
        onMouseEnter={onPonteiroEntrar}
        onMouseLeave={onPonteiroSair}
      >
        <button
          type="button"
          aria-pressed={Boolean(fixada)}
          onClick={onAlternar}
        >
          pin
        </button>
        <span>Atendimentos</span>
      </aside>
    );
  },
}));

vi.mock("@/components/shell/navegacao-inferior", () => ({
  NavegacaoInferior: () => <nav data-testid="navegacao-inferior" />,
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
    estadoSidebar.retraida = true;
    estadoSidebar.fixada = false;
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

  it("inicia a sidebar retraida em viewport largo", () => {
    render(
      <ShellComSidebar>
        <p>Conteúdo</p>
      </ShellComSidebar>,
    );

    expect(screen.getByTestId("sidebar")).toHaveAttribute("data-state", "collapsed");
    expect(screen.getByTestId("sidebar")).toHaveAttribute("data-fixada", "false");
    expect(screen.queryByTestId("navegacao-inferior")).not.toBeInTheDocument();
  });

  it("expande ao passar o mouse e retrai ao sair, sem fixar", () => {
    render(
      <ShellComSidebar>
        <p>Conteúdo</p>
      </ShellComSidebar>,
    );

    fireEvent.mouseEnter(screen.getByTestId("sidebar"));
    expect(screen.getByTestId("sidebar")).toHaveAttribute("data-state", "expanded");
    expect(screen.getByTestId("sidebar")).toHaveAttribute("data-fixada", "false");

    fireEvent.mouseLeave(screen.getByTestId("sidebar"));
    expect(screen.getByTestId("sidebar")).toHaveAttribute("data-state", "collapsed");
  });

  it("expande com foco de teclado e retrai ao perder o foco", () => {
    render(
      <ShellComSidebar>
        <p>Conteúdo</p>
      </ShellComSidebar>,
    );

    act(() => estadoSidebar.onFocoDentro?.());
    expect(screen.getByTestId("sidebar")).toHaveAttribute("data-state", "expanded");

    act(() => estadoSidebar.onFocoFora?.());
    expect(screen.getByTestId("sidebar")).toHaveAttribute("data-state", "collapsed");
  });

  it("o botao de pin mantem a barra aberta depois de sair o ponteiro", () => {
    render(
      <ShellComSidebar>
        <p>Conteúdo</p>
      </ShellComSidebar>,
    );

    fireEvent.click(screen.getByRole("button", { name: "pin" }));
    expect(screen.getByTestId("sidebar")).toHaveAttribute("data-state", "expanded");
    expect(screen.getByTestId("sidebar")).toHaveAttribute("data-fixada", "true");
    expect(screen.getByRole("button", { name: "pin" })).toHaveAttribute("aria-pressed", "true");

    fireEvent.mouseLeave(screen.getByTestId("sidebar"));
    expect(screen.getByTestId("sidebar")).toHaveAttribute("data-state", "expanded");

    fireEvent.click(screen.getByRole("button", { name: "pin" }));
    expect(screen.getByTestId("sidebar")).toHaveAttribute("data-fixada", "false");
    expect(screen.getByTestId("sidebar")).toHaveAttribute("data-state", "collapsed");
  });

  it("esconde a sidebar e mostra a barra inferior no viewport estreito", () => {
    render(
      <ShellComSidebar>
        <p>Conteúdo</p>
      </ShellComSidebar>,
    );

    telaEstreita = true;
    act(() => notificarMudanca?.());

    expect(screen.queryByTestId("sidebar")).not.toBeInTheDocument();
    expect(screen.getByTestId("navegacao-inferior")).toBeInTheDocument();
  });
});
