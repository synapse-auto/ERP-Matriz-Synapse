import { type ReactElement, type ReactNode, useState } from "react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import type { MensagemResposta } from "@/lib/atendimento/types";
import type { ItemDoVisualizador } from "./visualizador-midia";

const { emitirUrlAssinadaDaMidia, baixarUrlAssinada, apiFetchBlob } = vi.hoisted(() => ({
  emitirUrlAssinadaDaMidia: vi.fn(),
  baixarUrlAssinada: vi.fn(),
  apiFetchBlob: vi.fn(),
}));

vi.mock("@/lib/config/textos-provider", () => ({
  useTextos: () => ({
    atendimentos: {
      media: {
        imagem: "Imagem",
        audio: "Áudio",
        reproduzir: "Reproduzir áudio",
        pausar: "Pausar áudio",
        posicao: "Posição do áudio",
        documento: "Documento",
        baixar: "Baixar",
        botoes: "Opções",
        lista: "Lista",
        visualizador: {
          fechar: "Fechar visualizador",
          anterior: "Mídia anterior",
          proxima: "Próxima mídia",
          carregando: "Carregando mídia...",
          pdfIndisponivel: "Não foi possível exibir o PDF neste navegador. Baixe o arquivo para abrir.",
          documentoNaoRenderizavel: "Este tipo de arquivo não pode ser aberto aqui. Baixe para visualizar.",
          erroAoCarregar: "Não foi possível carregar a mídia.",
          video: "Vídeo",
          abrirFoto: "Ampliar foto de {nome}",
          abrirMidia: "Abrir {nome}",
        },
      },
      mensagem: {
        acoes: { abrir: "Ações", titulo: "Ações", copiar: "Copiar", copiada: "ok", copiarErro: "erro", reagir: "Reagir", reacaoQuantidade: "{emoji}", reacaoMinha: "{emoji}", maisEmojis: "Mais", seletorTitulo: "Escolher", seletorFechar: "Fechar", reacaoErro: "erro", responder: "Responder", encaminhar: "Encaminhar", rapidas: ["👍"], seletor: { search: "Buscar", searchNoResults: "Nenhum", pick: "Escolha", addCustom: "Custom", categories: { activity: "A", custom: "C", flags: "F", foods: "Fo", frequent: "R", nature: "N", objects: "O", people: "P", places: "V", search: "B", symbols: "S" }, skins: { choose: "Tom", 1: "1", 2: "2", 3: "3", 4: "4", 5: "5", 6: "6" } } },
        citacao: { resposta: "Respondendo", encaminhamento: "Encaminhada", cancelar: "Cancelar", origemIndisponivel: "indisponível", imagem: "Foto", audio: "Áudio", documento: "Documento" },
        status: { pendente: "Enviando", enviado: "Enviado", entregue: "Entregue", lido: "Lido", falhou: "Falha" },
        reenviar: "Reenviar",
      },
    },
  }),
}));

vi.mock("@/lib/lead/api", () => ({
  emitirUrlAssinadaDaMidia: (...argumentos: unknown[]) => emitirUrlAssinadaDaMidia(...argumentos),
}));

vi.mock("@/lib/midia/baixar-url-assinada", () => ({
  baixarUrlAssinada: (...argumentos: unknown[]) => baixarUrlAssinada(...argumentos),
}));

vi.mock("@/lib/api/http-client", () => ({
  apiFetchBlob: (...argumentos: unknown[]) => apiFetchBlob(...argumentos),
}));

vi.mock("@/components/mensagens/interacao-mensagem", () => ({
  InteracaoMensagem: ({ children }: { children: ReactNode }) => children,
}));

import { BolhaMensagem } from "./bolha-mensagem";
import { FotoDoLeadClicavel, VisualizadorMidia } from "./visualizador-midia";

const URL_FRESCA = "https://storage.example/nova.jpg?token=fresco";
const URL_VIDEO = "https://storage.example/clip.mp4?token=fresco";
const URL_PDF = "https://storage.example/doc.pdf?token=fresco";
const URL_DOCX = "https://storage.example/planilha.xlsx?token=fresco";
const URL_AUDIO = "https://storage.example/voz.m4a?token=fresco";
const URL_EXPIRADA = "https://storage.example/antiga.jpg?token=expirado";

function item(parcial: Partial<ItemDoVisualizador> & Pick<ItemDoVisualizador, "id">): ItemDoVisualizador {
  return {
    nome: parcial.nome ?? "arquivo",
    mimetype: parcial.mimetype ?? "image/jpeg",
    tamanho: parcial.tamanho ?? 2048,
    enviadoEm: parcial.enviadoEm ?? "2026-08-16T12:00:00Z",
    tipoMensagem: parcial.tipoMensagem ?? "IMAGEM",
    origem: parcial.origem ?? { tipo: "mensagem", leadId: "lead-1", mensagemId: parcial.id },
    ...parcial,
  };
}

function renderizar(ui: ReactElement) {
  const cliente = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(<QueryClientProvider client={cliente}>{ui}</QueryClientProvider>);
}

describe("VisualizadorMidia", () => {
  const URLOriginal = URL;

  beforeEach(() => {
    emitirUrlAssinadaDaMidia.mockReset().mockResolvedValue({ url: URL_FRESCA });
    baixarUrlAssinada.mockReset();
    apiFetchBlob.mockReset();
    class URLComBlob extends URLOriginal {
      static createObjectURL = vi.fn(() => "blob:foto-visualizador");
      static revokeObjectURL = vi.fn();
    }
    vi.stubGlobal("URL", URLComBlob);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("emite URL nova ao abrir e ignora a URL expirada da listagem", async () => {
    renderizar(
      <VisualizadorMidia
        aberto
        onFechar={vi.fn()}
        itens={[item({ id: "msg-1", nome: "vao.jpg" })]}
        indice={0}
      />,
    );

    await waitFor(() => {
      expect(emitirUrlAssinadaDaMidia).toHaveBeenCalledWith("lead-1", "msg-1");
    });
    const imagem = await screen.findByRole("img", { name: "vao.jpg" });
    expect(imagem).toHaveAttribute("src", URL_FRESCA);
    expect(imagem).not.toHaveAttribute("src", URL_EXPIRADA);
    expect(document.querySelector("[src*='/api/']")).toBeNull();
    expect(document.querySelector("[href*='/api/']")).toBeNull();
  });

  it("mostra vídeo com controls na URL recém-assinada", async () => {
    emitirUrlAssinadaDaMidia.mockResolvedValue({ url: URL_VIDEO });
    renderizar(
      <VisualizadorMidia
        aberto
        onFechar={vi.fn()}
        itens={[item({ id: "vid-1", nome: "clip.mp4", mimetype: "video/mp4", tipoMensagem: "DOCUMENTO" })]}
        indice={0}
      />,
    );

    await waitFor(() => {
      expect(document.querySelector("video")).toHaveAttribute("src", URL_VIDEO);
    });
    expect(document.querySelector("video")).toHaveAttribute("controls");
  });

  it("embute PDF e oferece download no fallback", async () => {
    emitirUrlAssinadaDaMidia.mockResolvedValue({ url: URL_PDF });
    renderizar(
      <VisualizadorMidia
        aberto
        onFechar={vi.fn()}
        itens={[item({ id: "pdf-1", nome: "orcamento.pdf", mimetype: "application/pdf", tipoMensagem: "DOCUMENTO" })]}
        indice={0}
      />,
    );

    await waitFor(() => {
      expect(document.querySelector("object[type='application/pdf']")).toHaveAttribute("data", URL_PDF);
    });
    expect(screen.getByText(/Não foi possível exibir o PDF/)).toBeInTheDocument();
  });

  it("não tenta renderizar documento não visualizável e mostra o botão de baixar", async () => {
    emitirUrlAssinadaDaMidia.mockResolvedValue({ url: URL_DOCX });
    renderizar(
      <VisualizadorMidia
        aberto
        onFechar={vi.fn()}
        itens={[item({
          id: "xlsx-1",
          nome: "planilha.xlsx",
          mimetype: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
          tipoMensagem: "DOCUMENTO",
          tamanho: 4096,
        })]}
        indice={0}
      />,
    );

    expect(await screen.findByText("Este tipo de arquivo não pode ser aberto aqui. Baixe para visualizar.")).toBeInTheDocument();
    expect(screen.getAllByText("planilha.xlsx").length).toBeGreaterThan(0);
    expect(document.querySelector("iframe")).toBeNull();
    expect(document.querySelector("object")).toBeNull();
    fireEvent.click(screen.getAllByRole("button", { name: "Baixar" })[0]);
    await waitFor(() => {
      expect(baixarUrlAssinada).toHaveBeenCalledWith(URL_DOCX);
    });
  });

  it("reusa PlayerAudio para áudio", async () => {
    emitirUrlAssinadaDaMidia.mockResolvedValue({ url: URL_AUDIO });
    renderizar(
      <VisualizadorMidia
        aberto
        onFechar={vi.fn()}
        itens={[item({ id: "aud-1", nome: "voz.m4a", mimetype: "audio/mp4", tipoMensagem: "AUDIO" })]}
        indice={0}
      />,
    );

    await waitFor(() => {
      expect(document.querySelector('[data-slot="player-audio"]')).toBeInTheDocument();
    });
    expect(document.querySelector("video")).toBeNull();
  });

  it("fecha por Esc, pelo botão e devolve o foco a quem abriu", async () => {
    function Hospede() {
      const [aberto, setAberto] = useState(false);
      return (
        <>
          <button type="button" onClick={() => setAberto(true)}>Abrir gatilho</button>
          <VisualizadorMidia
            aberto={aberto}
            onFechar={() => setAberto(false)}
            itens={[item({ id: "msg-1", nome: "vao.jpg" })]}
            indice={0}
          />
        </>
      );
    }

    renderizar(<Hospede />);
    const gatilho = screen.getByRole("button", { name: "Abrir gatilho" });
    gatilho.focus();
    fireEvent.click(gatilho);

    expect(await screen.findByRole("dialog")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Fechar visualizador" }));
    await waitFor(() => {
      expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    });
    expect(gatilho).toHaveFocus();

    fireEvent.click(gatilho);
    expect(await screen.findByRole("dialog")).toBeInTheDocument();
    fireEvent.keyDown(screen.getByRole("dialog"), { key: "Escape" });
    await waitFor(() => {
      expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    });

    fireEvent.click(gatilho);
    expect(await screen.findByRole("dialog")).toBeInTheDocument();
    fireEvent.click(document.querySelector('[data-slot="dialog-overlay"]')!);
    await waitFor(() => {
      expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    });
  });

  it("navega com setas entre as mídias e para nas pontas", async () => {
    function Galeria() {
      const [indice, setIndice] = useState(0);
      const itens = [
        item({ id: "a", nome: "um.jpg" }),
        item({ id: "b", nome: "dois.jpg" }),
        item({ id: "c", nome: "tres.jpg" }),
      ];
      return (
        <VisualizadorMidia
          aberto
          onFechar={vi.fn()}
          itens={itens}
          indice={indice}
          onIndiceChange={setIndice}
        />
      );
    }

    emitirUrlAssinadaDaMidia.mockImplementation((_lead: string, mensagemId: string) =>
      Promise.resolve({ url: `https://storage.example/${mensagemId}.jpg?token=ok` }),
    );

    renderizar(<Galeria />);
    expect(await screen.findByRole("img", { name: "um.jpg" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Mídia anterior" })).toBeDisabled();

    fireEvent.click(screen.getByRole("button", { name: "Próxima mídia" }));
    expect(await screen.findByRole("img", { name: "dois.jpg" })).toBeInTheDocument();

    fireEvent.keyDown(screen.getByRole("dialog"), { key: "ArrowRight" });
    expect(await screen.findByRole("img", { name: "tres.jpg" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Próxima mídia" })).toBeDisabled();

    fireEvent.keyDown(screen.getByRole("dialog"), { key: "ArrowRight" });
    expect(screen.getByRole("img", { name: "tres.jpg" })).toBeInTheDocument();

    fireEvent.keyDown(screen.getByRole("dialog"), { key: "ArrowLeft" });
    expect(await screen.findByRole("img", { name: "dois.jpg" })).toBeInTheDocument();
  });

  it("abre a mídia pela bolha com URL da listagem já vencida, porque o overlay emite outra", async () => {
    function mensagem(parcial: Partial<MensagemResposta>): MensagemResposta {
      return {
        id: "mensagem-1",
        remetenteTipo: "LEAD",
        remetenteId: null,
        remetenteNome: null,
        tipo: "IMAGEM",
        conteudo: null,
        midiaUrl: URL_EXPIRADA,
        midiaMetadados: JSON.stringify({ nome: "medida.jpg", mimetype: "image/jpeg" }),
        opcoes: null,
        statusEntrega: "ENTREGUE",
        enviadoEm: "2026-08-16T12:00:00Z",
        ...parcial,
      };
    }

    renderizar(
      <BolhaMensagem
        mensagem={mensagem({})}
        leadId="lead-1"
        onDefinirReacao={vi.fn()}
        onRemoverReacao={vi.fn()}
      />,
    );

    expect(screen.getByRole("img", { name: "Imagem" })).toHaveAttribute("src", URL_EXPIRADA);
    fireEvent.click(screen.getByRole("button", { name: "Abrir medida.jpg" }));

    await waitFor(() => {
      expect(emitirUrlAssinadaDaMidia).toHaveBeenCalledWith("lead-1", "mensagem-1");
    });
    const noOverlay = await screen.findAllByRole("img");
    expect(noOverlay.some((el) => el.getAttribute("src") === URL_FRESCA)).toBe(true);
  });
});

describe("FotoDoLeadClicavel", () => {
  it("não vira clicável e não abre overlay quando o lead não tem foto", () => {
    const cliente = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    render(
      <QueryClientProvider client={cliente}>
        <FotoDoLeadClicavel id="lead-1" nome="Maria Silva" fotoUrl={null} />
      </QueryClientProvider>,
    );

    expect(screen.queryByRole("button", { name: "Ampliar foto de Maria Silva" })).not.toBeInTheDocument();
    expect(screen.getByText("MS")).toBeInTheDocument();
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });

  it("abre a foto no overlay quando há foto", async () => {
    emitirUrlAssinadaDaMidia.mockClear();
    const cliente = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    render(
      <QueryClientProvider client={cliente}>
        <FotoDoLeadClicavel id="lead-1" nome="Maria Silva" fotoUrl="https://cdn.example/foto.webp" />
      </QueryClientProvider>,
    );

    fireEvent.click(screen.getByRole("button", { name: "Ampliar foto de Maria Silva" }));
    const dialogo = await screen.findByRole("dialog");
    expect(within(dialogo).getByRole("img", { name: "Maria Silva" })).toHaveAttribute(
      "src",
      "https://cdn.example/foto.webp",
    );
    expect(emitirUrlAssinadaDaMidia).not.toHaveBeenCalled();
    expect(document.querySelector("[src*='/api/']")).toBeNull();
  });
});
