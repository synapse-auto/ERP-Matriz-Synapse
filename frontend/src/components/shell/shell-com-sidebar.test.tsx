"use client";

import { readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

import { act, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { EXPANSAO_DA_SIDEBAR } from "./expansao-da-sidebar";

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

function slotDaSidebar() {
  return document.querySelector("[data-slot='sidebar-slot']") as HTMLElement;
}

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

  afterEach(() => {
    vi.useRealTimers();
  });

  it("inicia a sidebar retraida em viewport largo", () => {
    render(
      <ShellComSidebar>
        <p>Conteúdo</p>
      </ShellComSidebar>,
    );

    expect(screen.getByTestId("sidebar")).toHaveAttribute("data-state", "collapsed");
    expect(screen.getByTestId("sidebar")).toHaveAttribute("data-fixada", "false");
    expect(slotDaSidebar()).toHaveStyle({ width: `${EXPANSAO_DA_SIDEBAR.larguraRetraidaPx}px` });
    expect(screen.queryByTestId("navegacao-inferior")).not.toBeInTheDocument();
  });

  it("hover so abre depois da intencao e reserva 260px no layout, sem sobreposicao", () => {
    vi.useFakeTimers();
    render(
      <ShellComSidebar>
        <p>Conteúdo</p>
      </ShellComSidebar>,
    );

    fireEvent.mouseEnter(screen.getByTestId("sidebar"));
    expect(screen.getByTestId("sidebar")).toHaveAttribute("data-state", "collapsed");
    expect(slotDaSidebar()).toHaveStyle({ width: `${EXPANSAO_DA_SIDEBAR.larguraRetraidaPx}px` });

    act(() => vi.advanceTimersByTime(EXPANSAO_DA_SIDEBAR.intencaoAbrirMs));
    expect(screen.getByTestId("sidebar")).toHaveAttribute("data-state", "expanded");
    expect(screen.getByTestId("sidebar")).toHaveAttribute("data-fixada", "false");
    expect(slotDaSidebar()).toHaveStyle({ width: `${EXPANSAO_DA_SIDEBAR.larguraExpandidaPx}px` });
    expect(slotDaSidebar()).toHaveAttribute("data-expandida", "true");
    expect(slotDaSidebar().className).not.toMatch(/\babsolute\b/);
    expect(screen.getByText("Conteúdo")).toBeVisible();
  });

  it("mouse leave recolhe depois do atraso, sem fixar", () => {
    vi.useFakeTimers();
    render(
      <ShellComSidebar>
        <p>Conteúdo</p>
      </ShellComSidebar>,
    );

    fireEvent.mouseEnter(screen.getByTestId("sidebar"));
    act(() => vi.advanceTimersByTime(EXPANSAO_DA_SIDEBAR.intencaoAbrirMs));
    fireEvent.mouseLeave(screen.getByTestId("sidebar"));
    expect(screen.getByTestId("sidebar")).toHaveAttribute("data-state", "expanded");

    act(() => vi.advanceTimersByTime(EXPANSAO_DA_SIDEBAR.atrasoFecharMs));
    expect(screen.getByTestId("sidebar")).toHaveAttribute("data-state", "collapsed");
    expect(slotDaSidebar()).toHaveStyle({ width: `${EXPANSAO_DA_SIDEBAR.larguraRetraidaPx}px` });
  });

  it("expande com foco de teclado sem atraso e retrai ao perder o foco", () => {
    vi.useFakeTimers();
    render(
      <ShellComSidebar>
        <p>Conteúdo</p>
      </ShellComSidebar>,
    );

    act(() => estadoSidebar.onFocoDentro?.());
    expect(screen.getByTestId("sidebar")).toHaveAttribute("data-state", "expanded");
    expect(slotDaSidebar()).toHaveStyle({ width: `${EXPANSAO_DA_SIDEBAR.larguraExpandidaPx}px` });

    act(() => estadoSidebar.onFocoFora?.());
    expect(screen.getByTestId("sidebar")).toHaveAttribute("data-state", "collapsed");
  });

  it("o botao de pin mantem a barra aberta depois de sair o ponteiro", () => {
    vi.useFakeTimers();
    render(
      <ShellComSidebar>
        <p>Conteúdo</p>
      </ShellComSidebar>,
    );

    fireEvent.click(screen.getByRole("button", { name: "pin" }));
    expect(screen.getByTestId("sidebar")).toHaveAttribute("data-state", "expanded");
    expect(screen.getByTestId("sidebar")).toHaveAttribute("data-fixada", "true");
    expect(screen.getByRole("button", { name: "pin" })).toHaveAttribute("aria-pressed", "true");
    expect(slotDaSidebar()).toHaveStyle({ width: `${EXPANSAO_DA_SIDEBAR.larguraExpandidaPx}px` });

    fireEvent.mouseLeave(screen.getByTestId("sidebar"));
    act(() => vi.advanceTimersByTime(EXPANSAO_DA_SIDEBAR.atrasoFecharMs));
    expect(screen.getByTestId("sidebar")).toHaveAttribute("data-state", "expanded");

    fireEvent.click(screen.getByRole("button", { name: "pin" }));
    expect(screen.getByTestId("sidebar")).toHaveAttribute("data-fixada", "false");
    expect(screen.getByTestId("sidebar")).toHaveAttribute("data-state", "collapsed");
    expect(slotDaSidebar()).toHaveStyle({ width: `${EXPANSAO_DA_SIDEBAR.larguraRetraidaPx}px` });
  });

  it("cancela timers ao desmontar", () => {
    vi.useFakeTimers();
    const tela = render(
      <ShellComSidebar>
        <p>Conteúdo</p>
      </ShellComSidebar>,
    );

    fireEvent.mouseEnter(screen.getByTestId("sidebar"));
    expect(vi.getTimerCount()).toBeGreaterThan(0);
    tela.unmount();
    expect(vi.getTimerCount()).toBe(0);
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
    expect(screen.queryByTestId("sidebar-slot")).not.toBeInTheDocument();
    expect(document.querySelector("[data-slot='sidebar-slot']")).toBeNull();
    expect(screen.getByTestId("navegacao-inferior")).toBeInTheDocument();
  });

  it("falha se a expansao temporaria voltar a absolute/sobreposicao", () => {
    const pasta = dirname(fileURLToPath(import.meta.url));
    const shell = readFileSync(join(pasta, "shell-com-sidebar.tsx"), "utf8");
    const sidebar = readFileSync(join(pasta, "sidebar.tsx"), "utf8");
    expect(shell).not.toMatch(/sobreposta/);
    expect(sidebar).not.toMatch(/sobreposta/);
    expect(sidebar).not.toMatch(/absolute inset-y-0/);
    expect(shell).toMatch(/estiloDaLarguraDaSidebar/);
  });
});
