import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

import type { Textos } from "@/lib/config/schema";
import type { ChatMensagem } from "@/lib/chat-interno/types";

import { ComposerChatInterno, ListaMensagensChatInterno } from "./componentes-chat-interno";

const textos = {
  titulo: "Chat interno",
  semMensagens: "Nenhuma mensagem ainda.",
  placeholder: "Escreva uma mensagem...",
  enviar: "Enviar",
  erroEnviar: "Não foi possível enviar a mensagem.",
} as Textos["chatInterno"];

const mensagens: ChatMensagem[] = [
  { id: "m1", conversaId: "c1", remetenteId: "u1", remetenteNome: "Ana", conteudo: "Olá", enviadoEm: "2026-08-27T12:00:00Z" },
  { id: "m2", conversaId: "c1", remetenteId: "u2", remetenteNome: "Bruno", conteudo: "Tudo bem?", enviadoEm: "2026-08-27T12:01:00Z" },
];

describe("componentes de apresentação do chat interno", () => {
  it("posiciona a mensagem própria pela id real e identifica o remetente recebido", () => {
    const { container } = render(<ListaMensagensChatInterno mensagens={mensagens} usuarioAtual="u1" textos={textos} />);
    const linhas = container.firstElementChild?.children;
    expect(linhas?.[0]).toHaveClass("justify-end");
    expect(linhas?.[1]).toHaveClass("justify-start");
    expect(screen.getByText("Bruno")).toBeInTheDocument();
    expect(screen.getByText("Tudo bem?")).toBeInTheDocument();
  });

  it("envia por Enter, preserva Shift+Enter e mantém o texto quando falha", async () => {
    const enviar = vi.fn().mockRejectedValue(new Error("falha"));
    render(<ComposerChatInterno textos={textos} onEnviar={enviar} erro />);
    const campo = screen.getByPlaceholderText(textos.placeholder);
    fireEvent.change(campo, { target: { value: "mensagem" } });
    fireEvent.keyDown(campo, { key: "Enter", shiftKey: true });
    expect(enviar).not.toHaveBeenCalled();
    fireEvent.keyDown(campo, { key: "Enter", shiftKey: false });
    await waitFor(() => expect(enviar).toHaveBeenCalledWith("mensagem"));
    expect(campo).toHaveValue("mensagem");
    expect(screen.getByRole("alert")).toHaveTextContent(textos.erroEnviar);
  });
});
