import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import type { MidiaDoLead } from "@/lib/lead/types";

const { apiFetchArquivo, baixarBlobComoArquivo, midiasState, useMidiasDoLead, visualizador } = vi.hoisted(() => ({
  apiFetchArquivo: vi.fn(),
  baixarBlobComoArquivo: vi.fn(),
  midiasState: { pages: [] as MidiaDoLead[][], isLoading: false, isError: false,
    hasNextPage: false, isFetchingNextPage: false, fetchNextPage: vi.fn() },
  useMidiasDoLead: vi.fn(),
  visualizador: vi.fn(),
}));

vi.mock("@/lib/config/textos-provider", () => ({
  useTextos: () => ({
    atendimentos: {
      painel: {
        carregandoMidias: "Carregando mídias...",
        carregandoArquivos: "Carregando arquivos...",
        erroArquivos: "Erro ao carregar arquivos",
        vazioMidiasDaFicha: "Nenhuma mídia",
        vazioDocumentos: "Nenhum documento",
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
        midiaNaoRecebida: "O arquivo não chegou ao CRM.",
        baixar: "Baixar",
        imagem: "Imagem",
        audio: "Áudio",
        visualizador: { abrirMidia: "Abrir {nome}", erroAoCarregar: "Não foi possível carregar o arquivo." },
      },
    },
  }),
}));

vi.mock("@/lib/lead/use-painel-lead", () => ({
  TIPOS_MIDIAS_DA_FICHA: ["IMAGEM", "VIDEO", "AUDIO"],
  TIPOS_DOCUMENTOS_DA_FICHA: ["DOCUMENTO"],
  useMidiasDoLead: (...argumentos: unknown[]) => {
    useMidiasDoLead(...argumentos);
    return {
    data: { pages: midiasState.pages.map(pagina => pagina.filter(item =>
      (argumentos[1] as readonly string[]).includes(item.tipo))) },
    isLoading: midiasState.isLoading,
    isError: midiasState.isError,
    hasNextPage: midiasState.hasNextPage,
    isFetchingNextPage: midiasState.isFetchingNextPage,
    fetchNextPage: midiasState.fetchNextPage,
    };
  },
}));

vi.mock("@/lib/api/http-client", () => ({
  apiFetchArquivo: (...argumentos: unknown[]) => apiFetchArquivo(...argumentos),
}));

vi.mock("@/lib/midia/baixar-arquivo", () => ({
  baixarBlobComoArquivo: (...argumentos: unknown[]) => baixarBlobComoArquivo(...argumentos),
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

const BLOB = new Blob(["conteúdo sintético"]);

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

function renderizar(categoria: "midias" | "documentos" = "midias") {
  const cliente = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(<ListaDeMidiasDoLead leadId="lead-1" categoria={categoria} />, {
    wrapper: ({children}) => <QueryClientProvider client={cliente}>{children}</QueryClientProvider>,
  });
}

describe("ListaDeMidiasDoLead", () => {
  beforeEach(() => {
    useMidiasDoLead.mockReset();
    visualizador.mockReset();
    apiFetchArquivo.mockReset().mockResolvedValue({ blob: BLOB, nome: "confirmado-no-backend.png" });
    baixarBlobComoArquivo.mockReset();
    midiasState.isLoading = false;
    midiasState.isError = false;
    midiasState.hasNextPage = false;
    midiasState.isFetchingNextPage = false;
    midiasState.fetchNextPage.mockReset();
    midiasState.pages = [
      [
        midia({ mensagemId: "msg-img", tipo: "IMAGEM", nome: "vao.png", legenda: "Foto do vão" }),
        midia({ mensagemId: "msg-video", tipo: "VIDEO", nome: "visita.mp4" }),
        midia({ mensagemId: "msg-doc", tipo: "DOCUMENTO", nome: "orcamento.pdf" }),
        midia({ mensagemId: "msg-audio", tipo: "AUDIO", nome: "voz.m4a" }),
      ],
    ];
  });

  it("pede imagem, vídeo e áudio, sem documento na seção Mídias", () => {
    renderizar();

    expect(useMidiasDoLead).toHaveBeenCalledWith("lead-1", ["IMAGEM", "VIDEO", "AUDIO"]);

    expect(screen.getByRole("img", { name: "Foto do vão" })).toHaveAttribute(
      "src",
      "https://fake-storage.local/msg-img?token=assinatura",
    );
    expect(screen.getByText("visita.mp4")).toBeInTheDocument();
    expect(screen.getByLabelText("Áudio")).toBeInTheDocument();
    expect(screen.getByText("voz.m4a")).toBeInTheDocument();
    expect(screen.queryByText("orcamento.pdf")).not.toBeInTheDocument();

    expect(document.querySelector("[src*='/api/']")).toBeNull();
    expect(document.querySelector("[href*='/api/']")).toBeNull();
    expect(document.querySelector("a[download]")).toBeNull();
  });

  it("mostra vazio e não oferece Carregar mais sem registros", () => {
    midiasState.pages = [[]];
    renderizar();

    expect(screen.getByText("Nenhuma mídia")).toBeInTheDocument();
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

  it("baixar usa o endpoint JWT e o nome confirmado, sem navegar para /api/", async () => {
    renderizar();

    fireEvent.click(screen.getByRole("button", { name: "Salvar imagem: vao.png" }));

    await waitFor(() => {
      expect(apiFetchArquivo).toHaveBeenCalledWith("/api/v1/leads/lead-1/midias/msg-img/download");
    });
    expect(baixarBlobComoArquivo).toHaveBeenCalledWith(BLOB, "confirmado-no-backend.png");
    expect(document.querySelector("a[href*='/api/']")).toBeNull();
  });

  it("documento mantém o download pela mesma rota autorizada", async () => {
    renderizar("documentos");
    fireEvent.click(screen.getByRole("button", { name: "Baixar: orcamento.pdf" }));
    await waitFor(() => expect(apiFetchArquivo).toHaveBeenCalledWith("/api/v1/leads/lead-1/midias/msg-doc/download"));
    expect(baixarBlobComoArquivo).toHaveBeenCalledWith(BLOB, "confirmado-no-backend.png");
  });

  it("visualizador da categoria preserva imagem, vídeo e áudio", () => {
    renderizar();
    fireEvent.click(screen.getAllByRole("button", { name: "Abrir vao.png" })[0]);
    const itens = visualizador.mock.lastCall?.[0].itens as { tipoMensagem: string }[];
    expect(itens.map((item) => item.tipoMensagem)).toEqual(["IMAGEM", "VIDEO", "AUDIO"]);
    fireEvent.click(screen.getByRole("button", { name: "Abrir voz.m4a" }));
    expect(visualizador.mock.lastCall?.[0].indice).toBe(2);
  });

  it("JPG como documento permanece em Documentos e preserva tipo no visualizador", () => {
    midiasState.pages = [[midia({mensagemId: "doc-jpg", tipo: "DOCUMENTO", nome: "foto.jpg", mimetype: "image/jpeg"})]];
    renderizar("documentos");
    expect(useMidiasDoLead).toHaveBeenCalledWith("lead-1", ["DOCUMENTO"]);
    fireEvent.click(screen.getByRole("button", {name: "Abrir foto.jpg"}));
    expect(visualizador.mock.lastCall?.[0].itens[0].tipoMensagem).toBe("DOCUMENTO");
  });

  it("indisponível não oferece preview, reprodução nem download", () => {
    midiasState.pages = [[midia({mensagemId: "doc-ausente", tipo: "DOCUMENTO", nome: "ausente.png", indisponivel: true})]];
    renderizar("documentos");
    expect(screen.getByText("O arquivo não chegou ao CRM.")).toBeInTheDocument();
    expect(screen.queryByRole("button")).not.toBeInTheDocument();
    expect(apiFetchArquivo).not.toHaveBeenCalled();
  });

  it("falha no download mostra feedback e permite nova tentativa", async () => {
    apiFetchArquivo.mockRejectedValueOnce(new Error("falhou"));
    renderizar("documentos");
    fireEvent.click(screen.getByRole("button", {name: "Baixar: orcamento.pdf"}));
    expect(await screen.findByRole("alert")).toHaveTextContent("Não foi possível carregar o arquivo.");
    expect(screen.getByRole("button", {name: "Baixar: orcamento.pdf"})).toBeEnabled();
    expect(baixarBlobComoArquivo).not.toHaveBeenCalled();
  });

  it("carregamento e erro inicial não fingem estado vazio", () => {
    midiasState.isLoading = true;
    const {rerender} = renderizar();
    expect(screen.getByRole("status")).toHaveTextContent("Carregando arquivos...");
    midiasState.isLoading = false;
    midiasState.isError = true;
    midiasState.pages = [];
    rerender(<ListaDeMidiasDoLead leadId="lead-1" />);
    expect(screen.getByRole("alert")).toHaveTextContent("Erro ao carregar arquivos");
    expect(screen.queryByText("Nenhuma mídia")).not.toBeInTheDocument();
  });

  it("erro na próxima página mantém arquivos existentes e permite tentar novamente", () => {
    midiasState.isError = true;
    midiasState.hasNextPage = true;
    renderizar("documentos");
    expect(screen.getByText("orcamento.pdf")).toBeInTheDocument();
    expect(screen.getByRole("alert")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", {name: "Carregar mais"}));
    expect(midiasState.fetchNextPage).toHaveBeenCalledOnce();
  });

  it("preview aberto acompanha o ID quando uma nova mídia é recebida", () => {
    const {rerender} = renderizar();
    fireEvent.click(screen.getAllByRole("button", {name: "Abrir vao.png"})[0]);
    midiasState.pages[0].unshift(midia({mensagemId: "nova", tipo: "IMAGEM", nome: "nova.png"}));
    rerender(<ListaDeMidiasDoLead leadId="lead-1" />);
    const props = visualizador.mock.lastCall?.[0];
    expect(props.itens[props.indice].id).toBe("msg-img");
  });
});
