vi.mock("next/dynamic", () => ({
  default: () => () => null,
}));
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

vi.mock("@/lib/chat-interno/api", () => ({
  listarConversasChat: vi.fn(),
  listarMensagensChat: vi.fn(),
  enviarMensagemChat: vi.fn(),
  enviarMidiaChat: vi.fn(),
  definirReacaoChat: vi.fn(),
  removerReacaoChat: vi.fn(),
  marcarChatComoLido: vi.fn().mockResolvedValue(undefined),
}));

vi.mock("@/lib/config/textos-provider", () => ({
  useTextos: () => ({ atendimentos: {
    media: { audio: "Áudio", reproduzir: "Reproduzir áudio", pausar: "Pausar áudio", posicao: "Posição do áudio", baixar: "A", documento: "A", imagem: "A" },
    composer: { anexo: "A", anexoRemover: "A", audioGravando: "A", audioDescartar: "A", audioParar: "A", audioPreview: "A", audioEnviar: "A", audioSemMicrofone: "A", audioPermissaoNegada: "A", audioMicrofoneEmUso: "A", audioErroCaptura: "A", audioExcedeuLimite: "A" },
    mensagem: { acoes: { abrir: "Ações da mensagem", titulo: "Ações", copiar: "Copiar", copiada: "ok", copiarErro: "erro", reagir: "Reagir com {emoji}", reacaoQuantidade: "{emoji}, {quantidade}", reacaoMinha: "{emoji}, {quantidade}, sua reação", maisEmojis: "Mais emojis", seletorTitulo: "Escolher", seletorFechar: "Fechar", reacaoErro: "erro", rapidas: ["👍", "❤️", "😂", "😮", "😢", "🙏"], seletor: { search: "Buscar", searchNoResults: "Nenhum", pick: "Escolha", addCustom: "C", categories: { activity: "A", custom: "C", flags: "F", foods: "Fo", frequent: "R", nature: "N", objects: "O", people: "P", places: "V", search: "B", symbols: "S" }, skins: { choose: "Tom", 1: "1", 2: "2", 3: "3", 4: "4", 5: "5", 6: "6" } } } },
  }, chatInterno: {
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
    expect(screen.queryByRole("button", { name: "Finalizar" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Mais ações" })).not.toBeInTheDocument();
    expect(screen.queryByText("Finalizar todos")).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /lead/i })).not.toBeInTheDocument();
  });
});
