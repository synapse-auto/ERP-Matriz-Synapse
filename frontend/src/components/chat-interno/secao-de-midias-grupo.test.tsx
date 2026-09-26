import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

const estado = vi.hoisted(() => ({
  consulta: { isLoading: false, isError: false, data: { pages: [] as unknown[][] }, hasNextPage: false, isFetchingNextPage: false, fetchNextPage: vi.fn() },
  urls: new Map<string, { data?: { url: string } }>(),
}));

vi.mock("@/lib/chat-interno/use-midias-do-grupo", () => ({
  useMidiasDoGrupo: () => estado.consulta,
  useUrlAssinadaDaMidiaGrupo: (_conversaId: string, mensagemId: string) => estado.urls.get(mensagemId) ?? { data: undefined },
}));
vi.mock("@/lib/chat-interno/midia", () => ({ baixarArquivoChat: vi.fn().mockResolvedValue(undefined) }));
vi.mock("@/components/atendimentos/visualizador-midia", () => ({ VisualizadorMidia: ({ itens }: { itens: { origem: { conversaId: string; mensagemId: string } }[] }) => <div role="dialog">{itens[0].origem.conversaId}:{itens[0].origem.mensagemId}</div> }));

import type { Textos } from "@/lib/config/schema";
import type { MidiaDoGrupo } from "@/lib/chat-interno/types";
import { baixarArquivoChat } from "@/lib/chat-interno/midia";
import { ListaDeMidiasDoGrupo } from "./secao-de-midias-grupo";

const textos = {
  midias: {
    titulo: "Mídias compartilhadas", vazio: "Nenhuma mídia compartilhada.", carregando: "Carregando mídias...",
    erro: "Não foi possível carregar as mídias.", carregarMais: "Carregar mais", abrir: "Abrir {nome}", baixar: "Baixar {nome}",
  },
} as unknown as Textos["chatInterno"];

const imagem: MidiaDoGrupo = {
  mensagemId: "m1", tipo: "IMAGEM", nome: "foto.png", mimetype: "image/png", tamanho: 2048,
  legenda: "Orçamento", enviadoEm: "2026-09-01T12:00:00Z",
};

describe("ListaDeMidiasDoGrupo", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    estado.consulta = { isLoading: false, isError: false, data: { pages: [] }, hasNextPage: false, isFetchingNextPage: false, fetchNextPage: vi.fn() };
    estado.urls.clear();
  });

  it("mostra estado vazio, carregando e erro sem quebrar o painel", () => {
    const { rerender } = render(<ListaDeMidiasDoGrupo conversaId="c1" textos={textos} />);
    expect(screen.getByText(textos.midias.vazio)).toBeInTheDocument();
    estado.consulta = { ...estado.consulta, isLoading: true };
    rerender(<ListaDeMidiasDoGrupo conversaId="c1" textos={textos} />);
    expect(screen.getByText(textos.midias.carregando)).toBeInTheDocument();
    estado.consulta = { ...estado.consulta, isLoading: false, isError: true };
    rerender(<ListaDeMidiasDoGrupo conversaId="c1" textos={textos} />);
    expect(screen.getByRole("alert")).toHaveTextContent(textos.midias.erro);
  });

  it("lista mídia, prévia e paginação", async () => {
    estado.consulta = { ...estado.consulta, data: { pages: [[imagem]] }, hasNextPage: true };
    estado.urls.set("m1", { data: { url: "https://storage.test/foto.png" } });
    render(<ListaDeMidiasDoGrupo conversaId="c1" textos={textos} />);
    expect(screen.getByText("foto.png")).toBeInTheDocument();
    expect(screen.getByAltText("Orçamento")).toHaveAttribute("src", "https://storage.test/foto.png");
    fireEvent.click(screen.getByRole("button", { name: "Carregar mais" }));
    await waitFor(() => expect(estado.consulta.fetchNextPage).toHaveBeenCalledOnce());
  });

  it("baixa os bytes reais e abre documento no visualizador com IDs da conversa", async () => {
    const documento: MidiaDoGrupo = { ...imagem, mensagemId: "m2", tipo: "DOCUMENTO", nome: "contrato.pdf", mimetype: "application/pdf" };
    estado.consulta = { ...estado.consulta, data: { pages: [[documento]] } };
    render(<ListaDeMidiasDoGrupo conversaId="c1" textos={textos} />);
    fireEvent.click(screen.getByRole("button", { name: "Baixar contrato.pdf" }));
    await waitFor(() => expect(baixarArquivoChat).toHaveBeenCalledWith("c1", "m2", "contrato.pdf"));
    fireEvent.click(screen.getByRole("button", { name: "Abrir contrato.pdf" }));
    expect(screen.getByRole("dialog")).toHaveTextContent("c1:m2");
  });

  it("arquivo ausente mostra erro recuperável sem sair do painel", async () => {
    estado.consulta = { ...estado.consulta, data: { pages: [[imagem]] } };
    vi.mocked(baixarArquivoChat).mockRejectedValueOnce(new Error("503"));
    render(<ListaDeMidiasDoGrupo conversaId="c1" textos={textos} />);
    fireEvent.click(screen.getByRole("button", { name: "Baixar foto.png" }));
    expect(await screen.findByRole("alert")).toHaveTextContent(textos.midias.erro);
    expect(screen.getByRole("button", { name: "Baixar foto.png" })).toBeEnabled();
  });
});
