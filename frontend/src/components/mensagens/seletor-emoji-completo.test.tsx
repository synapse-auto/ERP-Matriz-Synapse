import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

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
    expect(document.querySelector("em-emoji-picker")?.getAttribute("data-emoji-theme")).toBe("auto");
    expect(screen.getByLabelText("Buscar emoji")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Pessoas" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Natureza" })).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "👍🏽" }));
    expect(onEscolher).toHaveBeenCalledWith("👍🏽");
  });
});
