import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

import type { Textos } from "@/lib/config/schema";

vi.mock("next/dynamic", () => ({
  default: () =>
    function SeletorMock({ onEscolher }: { onEscolher: (emoji: string) => void }) {
      return (
        <>
          <input aria-label="Buscar emoji" />
          <button type="button">Natureza</button>
          <button type="button" onClick={() => onEscolher("👍🏽")}>
            👍🏽
          </button>
        </>
      );
    },
}));

import { PainelEmojiComposer } from "./painel-emoji-composer";

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

describe("PainelEmojiComposer", () => {
  it("abre busca, categorias e envia o emoji com tom de pele", async () => {
    const onEscolher = vi.fn();
    render(<PainelEmojiComposer rotulo="Emoji" i18n={i18n} onEscolher={onEscolher} />);

    fireEvent.click(screen.getByRole("button", { name: "Emoji" }));

    expect(await screen.findByLabelText("Buscar emoji")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Natureza" })).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "👍🏽" }));
    expect(onEscolher).toHaveBeenCalledWith("👍🏽");
  });
});
