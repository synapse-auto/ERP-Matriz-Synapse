import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

import type { Textos } from "@/lib/config/schema";

vi.mock("next/dynamic", () => ({
  default: () =>
    function SeletorMock({ onEscolher }: { onEscolher: (emoji: string) => void }) {
      return (
        <div>
          <input aria-label="Buscar emoji" />
          <button type="button" onClick={() => onEscolher("🎉")}>
            🎉
          </button>
        </div>
      );
    },
}));

import { InteracaoMensagem } from "./interacao-mensagem";

const textos: Textos["atendimentos"]["mensagem"]["acoes"] = {
  abrir: "Ações da mensagem",
  titulo: "Ações da mensagem",
  copiar: "Copiar",
  copiada: "Mensagem copiada.",
  copiarErro: "Não foi possível copiar a mensagem.",
  reagir: "Reagir com {emoji}",
  reacaoQuantidade: "{emoji}, {quantidade}",
  reacaoMinha: "{emoji}, {quantidade}, sua reação",
  maisEmojis: "Mais emojis",
  seletorTitulo: "Escolher emoji",
  seletorFechar: "Fechar seletor de emojis",
  reacaoErro: "Não foi possível salvar a reação.",
  responder: "Responder",
  encaminhar: "Encaminhar",
  rapidas: ["👍", "❤️", "😂", "😮", "😢", "🙏"],
  seletor: {
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
  },
};

const fantasmas = ["Responder", "Encaminhar", "Fixar", "Pergunte à IA", "Favoritar", "Denunciar", "Apagar"];

describe("InteracaoMensagem", () => {
  it("abre o menu com reações rápidas e copiar, sem comandos fora de escopo", async () => {
    render(
      <InteracaoMensagem
        alinhadaADireita
        textoCopiavel="Olá"
        reacoes={[]}
        textos={textos}
        onDefinirReacao={vi.fn()}
        onRemoverReacao={vi.fn()}
      >
        <p>Olá</p>
      </InteracaoMensagem>,
    );
    fireEvent.click(screen.getByRole("button", { name: "Ações da mensagem" }));
    expect(await screen.findByRole("button", { name: "Copiar" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Reagir com 👍" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Mais emojis" })).toBeInTheDocument();
    for (const nome of fantasmas) {
      expect(screen.queryByText(nome)).not.toBeInTheDocument();
    }
  });

  it("não mostra copiar quando não há texto copiável", async () => {
    render(
      <InteracaoMensagem
        alinhadaADireita={false}
        textoCopiavel={null}
        reacoes={[]}
        textos={textos}
        onDefinirReacao={vi.fn()}
        onRemoverReacao={vi.fn()}
      >
        <p>áudio</p>
      </InteracaoMensagem>,
    );
    fireEvent.click(screen.getByRole("button", { name: "Ações da mensagem" }));
    expect(screen.queryByRole("button", { name: "Copiar" })).not.toBeInTheDocument();
  });

  it("define reação rápida e remove a própria pelo agrupamento", async () => {
    const definir = vi.fn().mockResolvedValue(undefined);
    const remover = vi.fn().mockResolvedValue(undefined);
    const { rerender } = render(
      <InteracaoMensagem
        alinhadaADireita
        textoCopiavel="Olá"
        reacoes={[{ emoji: "😂", quantidade: 1, reagi: false }]}
        textos={textos}
        onDefinirReacao={definir}
        onRemoverReacao={remover}
      >
        <p>Olá</p>
      </InteracaoMensagem>,
    );
    fireEvent.click(screen.getByRole("button", { name: "Ações da mensagem" }));
    fireEvent.click(screen.getByRole("button", { name: "Reagir com 👍" }));
    await waitFor(() => expect(definir).toHaveBeenCalledWith("👍"));

    rerender(
      <InteracaoMensagem
        alinhadaADireita
        textoCopiavel="Olá"
        reacoes={[{ emoji: "👍", quantidade: 1, reagi: true }]}
        textos={textos}
        onDefinirReacao={definir}
        onRemoverReacao={remover}
      >
        <p>Olá</p>
      </InteracaoMensagem>,
    );
    fireEvent.click(screen.getByRole("button", { name: "👍, 1, sua reação" }));
    await waitFor(() => expect(remover).toHaveBeenCalled());
  });

  it("copia com sucesso e anuncia pelo catálogo", async () => {
    const writeText = vi.fn().mockResolvedValue(undefined);
    vi.stubGlobal("navigator", { clipboard: { writeText } });
    render(
      <InteracaoMensagem
        alinhadaADireita
        textoCopiavel="texto da bolha"
        reacoes={[]}
        textos={textos}
        onDefinirReacao={vi.fn()}
        onRemoverReacao={vi.fn()}
      >
        <p>texto da bolha</p>
      </InteracaoMensagem>,
    );
    fireEvent.click(screen.getByRole("button", { name: "Ações da mensagem" }));
    fireEvent.click(await screen.findByRole("button", { name: "Copiar" }));
    await waitFor(() => expect(writeText).toHaveBeenCalledWith("texto da bolha"));
    expect(await screen.findByRole("status")).toHaveTextContent("Mensagem copiada.");
  });

  it("anuncia erro quando a cópia falha", async () => {
    vi.stubGlobal("navigator", { clipboard: { writeText: vi.fn().mockRejectedValue(new Error("x")) } });
    render(
      <InteracaoMensagem
        alinhadaADireita
        textoCopiavel="texto"
        reacoes={[]}
        textos={textos}
        onDefinirReacao={vi.fn()}
        onRemoverReacao={vi.fn()}
      >
        <p>texto</p>
      </InteracaoMensagem>,
    );
    fireEvent.click(screen.getByRole("button", { name: "Ações da mensagem" }));
    fireEvent.click(await screen.findByRole("button", { name: "Copiar" }));
    expect(await screen.findByRole("status")).toHaveTextContent("Não foi possível copiar a mensagem.");
  });

  it("abre o seletor amplo e envia o emoji escolhido", async () => {
    const definir = vi.fn().mockResolvedValue(undefined);
    render(
      <InteracaoMensagem
        alinhadaADireita
        textoCopiavel="Olá"
        reacoes={[]}
        textos={textos}
        onDefinirReacao={definir}
        onRemoverReacao={vi.fn()}
      >
        <p>Olá</p>
      </InteracaoMensagem>,
    );
    fireEvent.click(screen.getByRole("button", { name: "Ações da mensagem" }));
    fireEvent.click(screen.getByRole("button", { name: "Mais emojis" }));
    expect(await screen.findByLabelText("Buscar emoji")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "🎉" }));
    await waitFor(() => expect(definir).toHaveBeenCalledWith("🎉"));
  });

  it("mostra Responder e Encaminhar só quando o atendimento oferece os callbacks", async () => {
    const responder = vi.fn();
    const encaminhar = vi.fn();
    render(
      <InteracaoMensagem
        alinhadaADireita
        textoCopiavel="Olá"
        reacoes={[]}
        textos={textos}
        onDefinirReacao={vi.fn()}
        onRemoverReacao={vi.fn()}
        onResponder={responder}
        onEncaminhar={encaminhar}
      >
        <p>Olá</p>
      </InteracaoMensagem>,
    );
    fireEvent.click(screen.getByRole("button", { name: "Ações da mensagem" }));
    fireEvent.click(await screen.findByRole("button", { name: "Responder" }));
    expect(responder).toHaveBeenCalled();
    fireEvent.click(screen.getByRole("button", { name: "Ações da mensagem" }));
    fireEvent.click(await screen.findByRole("button", { name: "Encaminhar" }));
    expect(encaminhar).toHaveBeenCalled();
    for (const nome of ["Fixar", "Pergunte à IA", "Favoritar", "Denunciar", "Apagar"]) {
      expect(screen.queryByText(nome)).not.toBeInTheDocument();
    }
  });
});
