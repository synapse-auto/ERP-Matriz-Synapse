import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { ChatMensagem } from "@/lib/chat-interno/types";
import { MidiaMensagemChat } from "./midia-mensagem-chat";

const mocks = vi.hoisted(() => ({ baixar: vi.fn(), assinar: vi.fn() }));
vi.mock("@/lib/chat-interno/midia", async (original) => ({ ...await original<object>(), baixarArquivoChat: mocks.baixar }));
vi.mock("@/lib/chat-interno/api", () => ({ emitirUrlAssinadaDaMidiaChat: mocks.assinar }));
vi.mock("@/lib/config/textos-provider", () => ({ useTextos: () => ({
  chatInterno: { tentarNovamente: "Tentar novamente", midias: { abrir: "Abrir {nome}", baixar: "Baixar {nome}", carregando: "Carregando...", erro: "Arquivo indisponível." } },
  atendimentos: { media: { imagem: "Imagem", audio: "Áudio", documento: "Documento", baixar: "Baixar", reproduzir: "Reproduzir", pausar: "Pausar", posicao: "Posição", visualizador: { video: "Vídeo", abrirMidia: "Abrir {nome}", fechar: "Fechar visualizador", carregando: "Carregando...", erroAoCarregar: "Arquivo indisponível.", anterior: "Anterior", proxima: "Próxima", documentoNaoRenderizavel: "Baixe o documento", pdfIndisponivel: "PDF indisponível" } } },
}) }));

const mensagem: ChatMensagem = { id: "m1", conversaId: "c1", remetenteId: "u1", remetenteNome: "Ana", tipo: "IMAGEM", conteudo: "Legenda\nhttps://example.org", midiaUrl: "https://storage.test/expira", midiaMetadados: JSON.stringify({ nome_original: "foto.png", mimetype: "image/png", tamanho_bytes: 2048 }), enviadoEm: "2026-09-26T12:00:00Z" };
function exibir(valor = mensagem) {
  return render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><MidiaMensagemChat mensagem={valor} /></QueryClientProvider>);
}

describe("mídia do chat interno", () => {
  beforeEach(() => { vi.clearAllMocks(); mocks.baixar.mockResolvedValue(undefined); mocks.assinar.mockResolvedValue({ url: "https://storage.test/renovada" }); });

  it("imagem anexa abre o visualizador com URL recém-autorizada e fecha por Escape", async () => {
    exibir();
    fireEvent.click(screen.getByRole("button", { name: "Abrir foto.png" }));
    await waitFor(() => expect(mocks.assinar).toHaveBeenCalledWith("c1", "m1"));
    await waitFor(() => expect(screen.getAllByRole("img").some((imagem) => imagem.getAttribute("src") === "https://storage.test/renovada")).toBe(true));
    fireEvent.keyDown(screen.getByRole("dialog"), { key: "Escape" });
    await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument());
  });

  it.each(["IMAGEM", "AUDIO", "VIDEO", "DOCUMENTO"])("%s baixa pelo identificador persistido e nome real", async (tipo) => {
    exibir({ ...mensagem, tipo });
    fireEvent.click(screen.getByRole("button", { name: "Baixar foto.png" }));
    await waitFor(() => expect(mocks.baixar).toHaveBeenCalledWith("c1", "m1", "foto.png"));
    expect(screen.getByText("2 KB")).toBeInTheDocument();
  });

  it("download falho não anuncia sucesso, preserva bolha e permite nova tentativa sem duplicar clique", async () => {
    let rejeitar!: (erro: Error) => void;
    mocks.baixar.mockImplementationOnce(() => new Promise((_, reject) => { rejeitar = reject; }));
    exibir();
    const botao = screen.getByRole("button", { name: "Baixar foto.png" });
    fireEvent.click(botao);
    fireEvent.click(botao);
    expect(mocks.baixar).toHaveBeenCalledOnce();
    expect(botao).toBeDisabled();
    rejeitar(new Error("rede"));
    expect(await screen.findByRole("alert")).toHaveTextContent("Arquivo indisponível.");
    fireEvent.click(botao);
    await waitFor(() => expect(mocks.baixar).toHaveBeenCalledTimes(2));
  });

  it("URL expirada é renovada por ação explícita; arquivo ausente mantém erro recuperável", async () => {
    exibir();
    fireEvent.error(screen.getByRole("img"));
    mocks.assinar.mockRejectedValueOnce(new Error("404"));
    fireEvent.click(screen.getByRole("button", { name: "Tentar novamente" }));
    await waitFor(() => expect(screen.getByRole("button", { name: "Tentar novamente" })).toBeEnabled());
    expect(screen.getByRole("alert")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Tentar novamente" }));
    await waitFor(() => expect(screen.getByRole("img")).toHaveAttribute("src", "https://storage.test/renovada"));
  });

  it("legenda mantém links e quebra de linha; a imagem não vira link externo", () => {
    exibir();
    expect(screen.getByRole("link", { name: "Abrir https://example.org" })).toHaveAttribute("href", "https://example.org/");
    expect(screen.getByRole("img").closest("a")).toBeNull();
    expect(screen.getByRole("img").closest("button")).toHaveAccessibleName("Abrir foto.png");
  });
});
