import { act, render, screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { GradeEmColunas, contarColunas, distribuirEmColunas } from "./grade-em-colunas";

/**
 * O jsdom não faz layout: o teste decide quantas trilhas a grade CSS resolveu, respondendo ao
 * `getComputedStyle` da grade como o navegador responderia (uma medida em px por coluna).
 */
const layout = { trilhas: "" };
const observadores = new Set<ResizeObserverCallback>();

class ResizeObserverFalso {
  constructor(private readonly callback: ResizeObserverCallback) {}
  observe() {
    observadores.add(this.callback);
  }
  unobserve() {}
  disconnect() {
    observadores.delete(this.callback);
  }
}

function redimensionar(trilhas: string) {
  layout.trilhas = trilhas;
  act(() => {
    observadores.forEach((callback) => callback([], {} as ResizeObserver));
  });
}

beforeEach(() => {
  layout.trilhas = "";
  vi.stubGlobal("ResizeObserver", ResizeObserverFalso);
  const original = window.getComputedStyle.bind(window);
  vi.spyOn(window, "getComputedStyle").mockImplementation((elemento, pseudo) => {
    const estilo = original(elemento, pseudo);
    if (!(elemento instanceof HTMLElement) || elemento.dataset.slot !== "grade-em-colunas") return estilo;
    return new Proxy(estilo, {
      get(alvo, propriedade) {
        if (propriedade === "gridTemplateColumns") return layout.trilhas;
        const valor = Reflect.get(alvo, propriedade, alvo);
        return typeof valor === "function" ? valor.bind(alvo) : valor;
      },
    });
  });
});

afterEach(() => {
  observadores.clear();
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

const PESOS: Record<string, number> = { A: 5, B: 2, C: 2, D: 1, E: 1 };
const ITENS = Object.keys(PESOS);

function colunasRenderizadas(): string[][] {
  return [...document.querySelectorAll('[data-slot="coluna"]')].map((coluna) =>
    [...coluna.querySelectorAll("article")].map((cartao) => cartao.textContent ?? ""),
  );
}

function Grade() {
  return (
    <GradeEmColunas itens={ITENS} chave={(item) => item} peso={(item) => PESOS[item]}>
      {(item) => <article>{item}</article>}
    </GradeEmColunas>
  );
}

describe("distribuirEmColunas", () => {
  it("cada item vai para a coluna mais leve até ali; empate fica na mais à esquerda", () => {
    expect(distribuirEmColunas(ITENS, 2, (item) => PESOS[item])).toEqual([["A", "E"], ["B", "C", "D"]]);
    expect(distribuirEmColunas(["x", "y", "z"], 3, () => 1)).toEqual([["x"], ["y"], ["z"]]);
  });

  it("uma coluna preserva a ordem; quantidade inválida vira uma coluna", () => {
    expect(distribuirEmColunas(ITENS, 1, (item) => PESOS[item])).toEqual([ITENS]);
    expect(distribuirEmColunas(ITENS, 0, (item) => PESOS[item])).toEqual([ITENS]);
    expect(distribuirEmColunas(ITENS, Number.NaN, (item) => PESOS[item])).toEqual([ITENS]);
  });

  it("colunas sobrando ficam vazias em vez de esticar os cartões", () => {
    expect(distribuirEmColunas(["x"], 3, () => 1)).toEqual([["x"], [], []]);
  });
});

describe("contarColunas", () => {
  it("conta as trilhas resolvidas em px", () => {
    expect(contarColunas("352px 352px")).toBe(2);
    expect(contarColunas(" 412.5px 412.5px 412.5px ")).toBe(3);
  });

  it("valor que não é layout resolvido conta como uma coluna", () => {
    expect(contarColunas("")).toBe(1);
    expect(contarColunas("none")).toBe(1);
    expect(contarColunas("repeat(auto-fill, minmax(min(100%, 22rem), 1fr))")).toBe(1);
  });
});

describe("GradeEmColunas", () => {
  it("sem layout resolvido, uma coluna na ordem original", () => {
    render(<Grade />);
    expect(colunasRenderizadas()).toEqual([ITENS]);
  });

  it("distribui pelas colunas que o CSS resolveu e redistribui quando a largura muda", () => {
    layout.trilhas = "352px 352px";
    render(<Grade />);
    expect(colunasRenderizadas()).toEqual([["A", "E"], ["B", "C", "D"]]);

    redimensionar("352px");
    expect(colunasRenderizadas()).toEqual([ITENS]);
    expect(screen.getAllByRole("article")).toHaveLength(ITENS.length);
  });
});
