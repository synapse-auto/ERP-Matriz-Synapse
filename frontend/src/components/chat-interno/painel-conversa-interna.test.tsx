import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

vi.mock("@/lib/chat-interno/api", () => ({
  listarConversasChat: vi.fn(),
  listarMensagensChat: vi.fn(),
  enviarMensagemChat: vi.fn(),
  marcarChatComoLido: vi.fn().mockResolvedValue(undefined),
}));

vi.mock("@/lib/config/textos-provider", () => ({
  useTextos: () => ({ atendimentos: { composer: { anexo: "A", anexoRemover: "A", audioGravando: "A", audioDescartar: "A", audioParar: "A", audioPreview: "A", audioEnviar: "A", audioSemMicrofone: "A", audioPermissaoNegada: "A", audioMicrofoneEmUso: "A", audioErroCaptura: "A", audioExcedeuLimite: "A" }, audio: "A", baixar: "A", documento: "A", imagem: "A", responder: "A" }, chatInterno: {
    titulo: "Chat interno", semMensagens: "Nenhuma mensagem ainda.", placeholder: "Escreva uma mensagem...", enviar: "Enviar", erroEnviar: "Não foi possível enviar a mensagem.", carregando: "Carregando conversas...", erro: "Não foi possível carregar o chat interno.",
  } }),
}));

import * as api from "@/lib/chat-interno/api";
import { useAuthStore } from "@/lib/auth/auth-store";
import { PainelConversaInterna } from "./painel-conversa-interna";

describe("PainelConversaInterna", () => {
  it("renderiza autoria real, cabeçalho interno e marca leitura", async () => {
    vi.mocked(api.listarConversasChat).mockResolvedValue([{ id: "c1", tipo: "DIRETA", participantes: "Bruno Almeida", ultimaMensagem: "Oi", ultimaMensagemEm: "2026-08-27T12:00:00Z", naoLidas: 1 }]);
    vi.mocked(api.listarMensagensChat).mockResolvedValue({ proximoCursor: null, mensagens: [
      { id: "m1", conversaId: "c1", remetenteId: "u-atual", remetenteNome: "Ana", conteudo: "Minha mensagem", enviadoEm: "2026-08-27T12:00:00Z" },
      { id: "m2", conversaId: "c1", remetenteId: "u-outro", remetenteNome: "Bruno", conteudo: "Resposta", enviadoEm: "2026-08-27T12:01:00Z" },
    ] });
    useAuthStore.setState({ usuarioId: "u-atual" });
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    const { container } = render(<QueryClientProvider client={client}><PainelConversaInterna conversaId="c1" /></QueryClientProvider>);
    await waitFor(() => expect(screen.getByText("Resposta")).toBeInTheDocument());
    expect(screen.getByText("Chat interno")).toBeInTheDocument();
    expect(screen.getByText("Bruno Almeida")).toBeInTheDocument();
    expect(screen.getByText("Bruno")).toBeInTheDocument();
    expect(container.querySelector(".justify-end")).toBeInTheDocument();
    expect(container.querySelector(".justify-start")).toBeInTheDocument();
    await waitFor(() => expect(api.marcarChatComoLido).toHaveBeenCalledWith("c1"));
    expect(screen.queryByRole("button", { name: "Transferir" })).not.toBeInTheDocument();
  });
});
