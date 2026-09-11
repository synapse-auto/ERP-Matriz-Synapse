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
vi.mock("@/lib/chat-interno/api", () => ({
  emitirUrlAssinadaDaMidiaChat: vi.fn().mockResolvedValue({ url: "https://storage.test/media" }),
}));
vi.mock("@/lib/midia/baixar-url-assinada", () => ({ baixarUrlAssinada: vi.fn() }));

import type { Textos } from "@/lib/config/schema";
import type { MidiaDoGrupo } from "@/lib/chat-interno/types";
import { emitirUrlAssinadaDaMidiaChat } from "@/lib/chat-interno/api";
import { baixarUrlAssinada } from "@/lib/midia/baixar-url-assinada";
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

  it("emite URL sob demanda ao abrir ou baixar um documento", async () => {
    const documento: MidiaDoGrupo = { ...imagem, mensagemId: "m2", tipo: "DOCUMENTO", nome: "contrato.pdf", mimetype: "application/pdf" };
    estado.consulta = { ...estado.consulta, data: { pages: [[documento]] } };
    render(<ListaDeMidiasDoGrupo conversaId="c1" textos={textos} />);
    fireEvent.click(screen.getByRole("button", { name: "Baixar contrato.pdf" }));
    await waitFor(() => expect(emitirUrlAssinadaDaMidiaChat).toHaveBeenCalledWith("c1", "m2"));
    expect(baixarUrlAssinada).toHaveBeenCalledWith("https://storage.test/media");
  });
});
