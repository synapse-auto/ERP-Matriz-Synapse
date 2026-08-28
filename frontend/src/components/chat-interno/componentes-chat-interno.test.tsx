import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

import type { Textos } from "@/lib/config/schema";
import type { ChatMensagem } from "@/lib/chat-interno/types";

import { CabecalhoChatInterno, ComposerChatInterno, ListaMensagensChatInterno } from "./componentes-chat-interno";
import { TextosProvider } from "@/lib/config/textos-provider";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";

const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });

const mockTextosCompletos = {
  chatInterno: {
    titulo: "Chat interno",
    semMensagens: "Nenhuma mensagem ainda.",
    placeholder: "Escreva uma mensagem...",
    enviar: "Enviar",
    erroEnviar: "Não foi possível enviar a mensagem.",
  },
  atendimentos: {
    composer: {
      anexo: "A",
      anexoRemover: "A",
      audioGravando: "A",
      audioDescartar: "A",
      audioParar: "A",
      audioPreview: "A",
      audioEnviar: "A",
      audioSemMicrofone: "A",
      audioPermissaoNegada: "A",
      audioMicrofoneEmUso: "A",
      audioErroCaptura: "A",
      audioExcedeuLimite: "A",
    },
    media: { audio: "A", baixar: "A", documento: "A", imagem: "A" },
  },
} as unknown as Textos;

const textos = mockTextosCompletos.chatInterno;

const mensagens: ChatMensagem[] = [
  { id: "m1", conversaId: "c1", remetenteId: "u1", remetenteNome: "Ana", conteudo: "Olá", enviadoEm: "2026-08-27T12:00:00Z" },
  { id: "m2", conversaId: "c1", remetenteId: "u2", remetenteNome: "Bruno", conteudo: "Tudo bem?", enviadoEm: "2026-08-27T12:01:00Z" },
];

describe("componentes de apresentação do chat interno", () => {
  it("não oferece finalização, transferência ou controles de lead no cabeçalho interno", () => {
    render(<CabecalhoChatInterno textos={textos} conversa={{ id: "c1", tipo: "DIRETA", participantes: "Bruno Almeida", ultimaMensagem: "Oi", ultimaMensagemEm: "2026-08-27T12:00:00Z", naoLidas: 0 }} />);

    expect(screen.getByText("Bruno Almeida")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Finalizar" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Transferir" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Mais ações" })).not.toBeInTheDocument();
    expect(screen.queryByText("Finalizar todos")).not.toBeInTheDocument();
  });

  it("posiciona a mensagem própria pela id real e identifica o remetente recebido", () => {
    const { container } = render(<TextosProvider textos={mockTextosCompletos}><ListaMensagensChatInterno mensagens={mensagens} usuarioAtual="u1" textos={textos} /></TextosProvider>);
    const linhas = container.firstElementChild?.children;
    expect(linhas?.[0]).toHaveClass("justify-end");
    expect(linhas?.[1]).toHaveClass("justify-start");
    expect(screen.getByText("Bruno")).toBeInTheDocument();
    expect(screen.getByText("Tudo bem?")).toBeInTheDocument();
  });

  it("envia por Enter, preserva Shift+Enter e mantém o texto quando falha", async () => {
    const enviar = vi.fn().mockRejectedValue(new Error("falha"));
    render(<QueryClientProvider client={client}><TextosProvider textos={mockTextosCompletos}><ComposerChatInterno textos={textos} onEnviar={enviar} erro /></TextosProvider></QueryClientProvider>);
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
