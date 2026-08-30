"use client";

import { act, render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

const estadoSidebar = vi.hoisted(() => ({ retraida: false }));

vi.mock("@/components/shell/sidebar", () => ({
  Sidebar: ({ retraida }: { retraida: boolean }) => {
    estadoSidebar.retraida = retraida;
    return <aside data-testid="sidebar" data-state={retraida ? "collapsed" : "expanded"} />;
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
    expect(screen.queryByTestId("navegacao-inferior")).not.toBeInTheDocument();
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

  /*
   * jsdom não faz layout: não dá para medir scrollHeight aqui e provar a rolagem. O que este
   * teste protege é a única causa real já diagnosticada — sem `shrink-0` nos filhos diretos, o
   * flex column encolhe a página inteira até caber e nada rola. A prova de comportamento é a
   * medição no navegador (E96): main.scrollHeight 1221 vs clientHeight 760 no Dashboard, e
   * 760 vs 760 em Atendimentos. Se alguém remover a classe, este teste reprova.
   */
  it("a superficie de pagina nao deixa o filho direto encolher — sem isso a pagina nao rola", () => {
    const { container } = render(
      <ShellComSidebar>
        <p>Conteúdo</p>
      </ShellComSidebar>,
    );

    const superficie = container.querySelector('[data-slot="page-surface"]');
    expect(superficie).toHaveClass("overflow-y-auto");
    expect(superficie).toHaveClass("[&>*]:shrink-0");
  });
});
