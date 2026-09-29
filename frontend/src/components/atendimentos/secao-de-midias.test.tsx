import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import type { MidiaDoLead } from "@/lib/lead/types";

const { emitirUrlAssinadaDaMidia, baixarUrlAssinada, midiasState, useMidiasDoLead, visualizador } = vi.hoisted(() => ({
  emitirUrlAssinadaDaMidia: vi.fn(),
  baixarUrlAssinada: vi.fn(),
  midiasState: { pages: [] as MidiaDoLead[][] },
  useMidiasDoLead: vi.fn(),
  visualizador: vi.fn(),
}));

vi.mock("@/lib/config/textos-provider", () => ({
  useTextos: () => ({
    atendimentos: {
      painel: {
        carregandoMidias: "Carregando mídias...",
        erroMidias: "Não foi possível carregar as mídias.",
        erroOperacao: "Erro",
        vazioMidias: "Nenhuma mídia ou documento",
        vazioLembretes: "Nenhum lembrete",
        carregarMaisMidias: "Carregar mais",
        adicionar: "Adicionar",
        salvarImagem: "Salvar imagem",
        origemMidia: "Origem",
      },
      media: {
        baixar: "Baixar",
        imagem: "Imagem",
        audio: "Áudio",
        visualizador: { abrirMidia: "Abrir {nome}" },
      },
    },
  }),
}));

vi.mock("@/lib/lead/use-painel-lead", () => ({
  TIPOS_MIDIAS_DA_FICHA: ["IMAGEM", "VIDEO", "DOCUMENTO"],
  useMidiasDoLead: (...argumentos: unknown[]) => {
    useMidiasDoLead(...argumentos);
    return {
    data: { pages: midiasState.pages },
    isLoading: false,
    isError: false,
    hasNextPage: false,
    isFetchingNextPage: false,
    fetchNextPage: vi.fn(),
    };
  },
}));

vi.mock("@/lib/lead/api", () => ({
  emitirUrlAssinadaDaMidia: (...argumentos: unknown[]) => emitirUrlAssinadaDaMidia(...argumentos),
}));

vi.mock("@/lib/midia/baixar-url-assinada", () => ({
  baixarUrlAssinada: (...argumentos: unknown[]) => baixarUrlAssinada(...argumentos),
}));

vi.mock("@/lib/lead/use-url-assinada-da-midia", () => ({
  useUrlAssinadaDaMidia: (_leadId: string, mensagemId: string) => ({
    data: { url: `https://fake-storage.local/${mensagemId}?token=assinatura` },
  }),
}));

vi.mock("./visualizador-midia", () => ({
  VisualizadorMidia: (props: unknown) => {
    visualizador(props);
    return <div role="dialog" />;
  },
}));

import { readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

import { ListaDeMidiasDoLead } from "./secao-de-midias";

const URL_ASSINADA = "https://fake-storage.local/arquivo.png?token=abc";

function midia(parcial: Partial<MidiaDoLead> & Pick<MidiaDoLead, "mensagemId" | "tipo">): MidiaDoLead {
  return {
    atendimentoId: "at-1",
    nome: parcial.nome ?? parcial.tipo,
    mimetype: "application/octet-stream",
    tamanho: 2048,
    legenda: null,
    enviadoEm: "2026-08-16T12:00:00Z",
    origem: "WHATSAPP",
    ...parcial,
  };
}

function renderizar() {
  const cliente = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={cliente}>
      <ListaDeMidiasDoLead leadId="lead-1" />
    </QueryClientProvider>,
  );
}

describe("ListaDeMidiasDoLead", () => {
  beforeEach(() => {
    useMidiasDoLead.mockReset();
    visualizador.mockReset();
    emitirUrlAssinadaDaMidia.mockReset().mockResolvedValue({ url: URL_ASSINADA });
    baixarUrlAssinada.mockReset();
    midiasState.pages = [
      [
        midia({ mensagemId: "msg-img", tipo: "IMAGEM", nome: "vao.png", legenda: "Foto do vão" }),
        midia({ mensagemId: "msg-video", tipo: "VIDEO", nome: "visita.mp4" }),
        midia({ mensagemId: "msg-doc", tipo: "DOCUMENTO", nome: "orcamento.pdf" }),
      ],
    ];
  });

  it("pede apenas tipos da ficha e renderiza imagem, vídeo e documento sem áudio", () => {
    renderizar();

    expect(useMidiasDoLead).toHaveBeenCalledWith("lead-1", ["IMAGEM", "VIDEO", "DOCUMENTO"]);

    expect(screen.getByRole("img", { name: "Foto do vão" })).toHaveAttribute(
      "src",
      "https://fake-storage.local/msg-img?token=assinatura",
    );
    expect(screen.getByText("visita.mp4")).toBeInTheDocument();
    expect(screen.queryByLabelText("Áudio")).not.toBeInTheDocument();
    expect(screen.queryByText("voz.m4a")).not.toBeInTheDocument();
    expect(screen.getByText("orcamento.pdf")).toBeInTheDocument();

    expect(document.querySelector("[src*='/api/']")).toBeNull();
    expect(document.querySelector("[href*='/api/']")).toBeNull();
    expect(document.querySelector("a[download]")).toBeNull();
  });

  it("mostra vazio e não oferece Carregar mais quando o lead possui apenas áudios", () => {
    midiasState.pages = [[]];
    renderizar();

    expect(screen.getByText("Nenhuma mídia ou documento")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Carregar mais" })).not.toBeInTheDocument();
    expect(screen.queryByLabelText("Áudio")).not.toBeInTheDocument();
  });

  it("falha se o painel ou a seção voltarem a pendurar /api/ em src ou href", () => {
    const pasta = dirname(fileURLToPath(import.meta.url));
    const fontes = ["painel-da-conversa.tsx", "secao-de-midias.tsx", "midia-com-url-assinada.tsx"].map((arquivo) =>
      readFileSync(join(pasta, arquivo), "utf8"),
    );
    for (const fonte of fontes) {
      expect(fonte).not.toMatch(/urlDownload/);
      expect(fonte).not.toMatch(/\b(src|href)\s*=\s*\{[^}]*\/api\//);
    }
  });

  it("baixar emite a URL autenticada e abre a assinada, sem navegar para /api/", async () => {
    renderizar();

    fireEvent.click(screen.getByRole("button", { name: "Salvar imagem: vao.png" }));

    await waitFor(() => {
      expect(emitirUrlAssinadaDaMidia).toHaveBeenCalledWith("lead-1", "msg-img");
    });
    expect(baixarUrlAssinada).toHaveBeenCalledWith(URL_ASSINADA);
    expect(document.querySelector("a[href*='/api/']")).toBeNull();
  });

  it("documento mantém o download pela mesma rota autorizada", async () => {
    renderizar();
    fireEvent.click(screen.getByRole("button", { name: "Baixar: orcamento.pdf" }));
    await waitFor(() => expect(emitirUrlAssinadaDaMidia).toHaveBeenCalledWith("lead-1", "msg-doc"));
    expect(baixarUrlAssinada).toHaveBeenCalledWith(URL_ASSINADA);
  });

  it("abre imagem e documento no visualizador sem incluir áudio", () => {
    renderizar();
    fireEvent.click(screen.getAllByRole("button", { name: "Abrir vao.png" })[0]);
    const itens = visualizador.mock.lastCall?.[0].itens as { tipoMensagem: string }[];
    expect(itens.map((item) => item.tipoMensagem)).toEqual(["IMAGEM", "VIDEO", "DOCUMENTO"]);
    fireEvent.click(screen.getByRole("button", { name: "Abrir orcamento.pdf" }));
    expect(visualizador.mock.lastCall?.[0].indice).toBe(2);
  });
});
