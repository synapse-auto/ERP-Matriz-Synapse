import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

import type { Textos } from "@/lib/config/schema";
import type { ChatContato } from "@/lib/chat-interno/types";

import { DialogoSelecionarPessoa } from "./dialogo-selecionar-pessoa";

const textos = {
  selecionarPessoa: "Selecionar pessoa",
  selecionarPessoaDescricao: "Escolha alguém",
  buscarPessoa: "Buscar por nome...",
  fecharSeletor: "Fechar seletor",
  online: "Online",
  ausente: "Ausente",
  offline: "Offline",
  semPessoas: "Nenhuma pessoa disponível",
  erroContatos: "Erro ao carregar pessoas",
  erroAbrirConversa: "Erro ao abrir conversa",
  tentarNovamente: "Tentar novamente",
  carregando: "Carregando...",
} as Textos["chatInterno"];

const contatos: ChatContato[] = [
  { id: "u-offline", nome: "Bruno", presenca: "OFFLINE" },
  { id: "u-online-z", nome: "Zeca", presenca: "ONLINE" },
  { id: "u-online-a", nome: "Ana", presenca: "ONLINE" },
  { id: "u-ausente", nome: "Carla", presenca: "AUSENTE" },
];

function renderDialog(overrides: Partial<React.ComponentProps<typeof DialogoSelecionarPessoa>> = {}) {
  return render(
    <DialogoSelecionarPessoa
      aberto
      onFechar={vi.fn()}
      contatos={contatos}
      onSelecionar={vi.fn(() => Promise.resolve())}
      textos={textos}
      {...overrides}
    />,
  );
}

describe("DialogoSelecionarPessoa", () => {
  it("ordena online primeiro e por nome, exibindo fallback de iniciais", () => {
    renderDialog();
    const itens = screen.getAllByRole("button", { name: /, (Online|Ausente|Offline)$/ });
    expect(itens.map((item) => item.getAttribute("aria-label"))).toEqual([
      "Ana, Online",
      "Zeca, Online",
      "Carla, Ausente",
      "Bruno, Offline",
    ]);
    expect(screen.getByText("A")).toBeInTheDocument();
  });

  it("filtra a lista pela busca e mostra estado vazio", () => {
    renderDialog();
    fireEvent.change(screen.getByRole("textbox", { name: "Buscar por nome..." }), { target: { value: "inexistente" } });
    expect(screen.getByText("Nenhuma pessoa disponível")).toBeInTheDocument();
  });

  it("mostra erro com retry e loading real", () => {
    const retry = vi.fn();
    const { rerender } = renderDialog({ carregando: true });
    expect(screen.getByText("Carregando...")).toBeInTheDocument();
    rerender(<DialogoSelecionarPessoa aberto onFechar={vi.fn()} contatos={[]} erro onTentarNovamente={retry} onSelecionar={vi.fn()} textos={textos} />);
    expect(screen.getByRole("alert")).toHaveTextContent("Erro ao carregar pessoas");
    fireEvent.click(screen.getByRole("button", { name: "Tentar novamente" }));
    expect(retry).toHaveBeenCalledOnce();
  });

  it("fecha após abrir conversa com sucesso e permanece aberta em erro", async () => {
    const fechar = vi.fn();
    const selecionar = vi.fn(() => Promise.resolve());
    const primeira = renderDialog({ onFechar: fechar, onSelecionar: selecionar });
    fireEvent.click(screen.getByRole("button", { name: "Ana, Online" }));
    await waitFor(() => expect(fechar).toHaveBeenCalledOnce());
    expect(selecionar).toHaveBeenCalledWith("u-online-a");
    primeira.unmount();

    const erro = vi.fn(() => Promise.reject(new Error("falha")));
    renderDialog({ onSelecionar: erro });
    fireEvent.click(screen.getByRole("button", { name: "Ana, Online" }));
    await waitFor(() => expect(screen.getByRole("alert")).toHaveTextContent("Erro ao abrir conversa"));
    expect(screen.getByRole("dialog")).toBeInTheDocument();
  });
});
