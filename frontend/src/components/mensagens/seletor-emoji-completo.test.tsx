import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";

import type { Textos } from "@/lib/config/schema";

import { nativoSelecionado, SeletorEmojiCompleto } from "./seletor-emoji-completo";

vi.mock("emoji-mart", () => {
  class Picker extends HTMLElement {
    constructor(options: {
      data?: unknown;
      set?: string;
      theme?: string;
      onEmojiSelect?: (escolha: { native?: unknown }) => void;
    }) {
      super();
      this.setAttribute("data-emoji-set", String(options.set ?? ""));
      this.setAttribute("data-emoji-theme", String(options.theme ?? ""));
      const busca = document.createElement("input");
      busca.setAttribute("aria-label", "Buscar emoji");
      const pessoas = document.createElement("button");
      pessoas.type = "button";
      pessoas.textContent = "Pessoas";
      const natureza = document.createElement("button");
      natureza.type = "button";
      natureza.textContent = "Natureza";
      const comTom = document.createElement("button");
      comTom.type = "button";
      comTom.textContent = "👍🏽";
      comTom.addEventListener("click", () => options.onEmojiSelect?.({ native: "👍🏽" }));
      this.append(busca, pessoas, natureza, comTom);
    }
  }
  if (typeof customElements !== "undefined" && !customElements.get("em-emoji-picker")) {
    customElements.define("em-emoji-picker", Picker);
  }
  return { Picker };
});

/** Simula um host que nasce com largura 0 (primeiro passe do popover) e só depois ganha largura
 * real — o cenário que deixava a grade do emoji-mart nascida estreita para sempre. */
class ResizeObserverMock {
  static instancias: ResizeObserverMock[] = [];
  observe = vi.fn();
  disconnect = vi.fn();
  unobserve = vi.fn();
  constructor(private callback: ResizeObserverCallback) {
    ResizeObserverMock.instancias.push(this);
  }
  disparar() {
    this.callback([], this as unknown as ResizeObserver);
  }
}

function definirLargura(elemento: HTMLElement, largura: number) {
  Object.defineProperty(elemento, "offsetWidth", { configurable: true, value: largura });
}

const i18n: Textos["atendimentos"]["mensagem"]["acoes"]["seletor"] = {
  search: "Buscar emoji",
  searchNoResults: "Nenhum",
  pick: "Escolha",
  addCustom: "Custom",
  categories: {
    activity: "Atividades",
    custom: "Personalizados",
    flags: "Bandeiras",
    foods: "Comidas",
    frequent: "Recentes",
    nature: "Natureza",
    objects: "Objetos",
    people: "Pessoas",
    places: "Viagens",
    search: "Busca",
    symbols: "Símbolos",
  },
  skins: { choose: "Tom", 1: "1", 2: "2", 3: "3", 4: "4", 5: "5", 6: "6" },
};

describe("SeletorEmojiCompleto", () => {
  afterEach(() => {
    document.documentElement.classList.remove("dark");
  });

  it("extrai o Unicode nativo com modificador de tom de pele", () => {
    expect(nativoSelecionado({ native: "👍🏽" })).toBe("👍🏽");
    expect(nativoSelecionado({ native: "" })).toBeNull();
    expect(nativoSelecionado({})).toBeNull();
  });

  it("renderiza o picker e envia o emoji escolhido com tom de pele", async () => {
    const onEscolher = vi.fn();
    render(<SeletorEmojiCompleto i18n={i18n} onEscolher={onEscolher} />);

    await waitFor(() => {
      expect(document.querySelector("[data-slot='seletor-emoji'] em-emoji-picker")).not.toBeNull();
    });
    expect(document.querySelector("em-emoji-picker")?.getAttribute("data-emoji-set")).toBe("native");
    expect(screen.getByLabelText("Buscar emoji")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Pessoas" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Natureza" })).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "👍🏽" }));
    expect(onEscolher).toHaveBeenCalledWith("👍🏽");
  });

  it("constrói o picker com theme='dark' quando a raiz do documento tem a classe dark", async () => {
    document.documentElement.classList.add("dark");
    render(<SeletorEmojiCompleto i18n={i18n} onEscolher={vi.fn()} />);

    await waitFor(() => {
      expect(document.querySelector("em-emoji-picker")).not.toBeNull();
    });
    expect(document.querySelector("em-emoji-picker")?.getAttribute("data-emoji-theme")).toBe("dark");
  });

  it("constrói o picker com theme='light' quando a raiz do documento não tem a classe dark", async () => {
    document.documentElement.classList.remove("dark");
    render(<SeletorEmojiCompleto i18n={i18n} onEscolher={vi.fn()} />);

    await waitFor(() => {
      expect(document.querySelector("em-emoji-picker")).not.toBeNull();
    });
    // Nunca "auto": auto segue prefers-color-scheme do SO, não a classe dark do CRM.
    expect(document.querySelector("em-emoji-picker")?.getAttribute("data-emoji-theme")).toBe("light");
  });

  describe("largura do host medida depois de existir", () => {
    const resizeObserverOriginal = globalThis.ResizeObserver;

    beforeEach(() => {
      ResizeObserverMock.instancias = [];
      (globalThis as { ResizeObserver: unknown }).ResizeObserver = ResizeObserverMock;
    });

    afterEach(() => {
      (globalThis as { ResizeObserver: unknown }).ResizeObserver = resizeObserverOriginal;
    });

    it("não constrói o picker enquanto o host está com largura zero", () => {
      const { container } = render(<SeletorEmojiCompleto i18n={i18n} onEscolher={vi.fn()} />);
      const host = container.querySelector("[data-slot='seletor-emoji']") as HTMLElement;
      definirLargura(host, 0);

      // O efeito já rodou (useEffect síncrono após o commit em ambiente de teste); com largura
      // zero e ResizeObserver disponível, ele deve esperar em vez de construir estreito.
      expect(host.querySelector("em-emoji-picker")).toBeNull();
      expect(ResizeObserverMock.instancias).toHaveLength(1);
      expect(ResizeObserverMock.instancias[0]!.observe).toHaveBeenCalledWith(host);
    });

    it("constrói o picker assim que o ResizeObserver reporta largura real", () => {
      const { container } = render(<SeletorEmojiCompleto i18n={i18n} onEscolher={vi.fn()} />);
      const host = container.querySelector("[data-slot='seletor-emoji']") as HTMLElement;
      definirLargura(host, 0);

      const observer = ResizeObserverMock.instancias[0]!;
      expect(host.querySelector("em-emoji-picker")).toBeNull();

      definirLargura(host, 352);
      observer.disparar();

      expect(host.querySelector("em-emoji-picker")).not.toBeNull();
      expect(observer.disconnect).toHaveBeenCalledTimes(1);
    });

    it("ignora um segundo disparo do ResizeObserver depois de já ter construído", () => {
      const { container } = render(<SeletorEmojiCompleto i18n={i18n} onEscolher={vi.fn()} />);
      const host = container.querySelector("[data-slot='seletor-emoji']") as HTMLElement;
      definirLargura(host, 352);

      const observer = ResizeObserverMock.instancias[0]!;
      observer.disparar();
      const pickerUnico = host.querySelector("em-emoji-picker");
      expect(pickerUnico).not.toBeNull();

      observer.disparar();
      expect(host.querySelectorAll("em-emoji-picker")).toHaveLength(1);
      expect(host.querySelector("em-emoji-picker")).toBe(pickerUnico);
    });
  });
});
