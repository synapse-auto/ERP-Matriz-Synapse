import { fireEvent, render, screen } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { beforeEach, describe, expect, it, vi } from "vitest";

import type { CartaoAtendimento } from "@/lib/atendimento/types";

const mutateMidia = vi.fn();
const mutateTexto = vi.fn();
const estadoMidia: { isError: boolean; error: Error | null } = {
  isError: false,
  error: null,
};
const estadoTexto: { isError: boolean; error: Error | null } = {
  isError: false,
  error: null,
};
const configuracaoComposer = {
  tamanhoMaximoAudioBytes: 1024,
  duracaoMaximaAudioSegundos: 120,
  tempoNotificacaoSegundos: 8,
};

vi.mock("@/lib/atendimento/use-enviar-mensagem", () => ({
  useEnviarMensagem: () => ({
    mutate: mutateTexto,
    isPending: false,
    ...estadoTexto,
  }),
}));

vi.mock("@/lib/atendimento/use-enviar-midia", () => ({
  useEnviarMidia: () => ({
    mutate: mutateMidia,
    isPending: false,
    ...estadoMidia,
  }),
}));

vi.mock("@/lib/atendimento/use-configuracao-composer", () => ({
  useConfiguracaoComposer: () => ({ data: configuracaoComposer }),
}));

vi.mock("@/lib/atendimento/janela-24h", () => ({
  janelaTextoLivreAberta: () => true,
}));

vi.mock("@/lib/suporte/api", () => ({
  listarMensagensRapidas: () =>
    Promise.resolve([
      {
        id: "rapida-1",
        atendenteId: "ana",
        atendenteNome: "Ana",
        palavraChave: "saudacao",
        conteudo: "Olá! Como posso ajudar?",
        tipoMidia: null,
      },
    ]),
  criarMensagemProgramada: vi.fn(),
  editarMensagemProgramada: vi.fn(),
}));

vi.mock("@/lib/config/textos-provider", () => ({
  useTextos: () => ({
    atendimentos: {
      composer: {
        placeholder: "Digite uma mensagem...",
        enviar: "Enviar",
        anexo: "Anexo",
        anexoIndisponivel: "indisponivel",
        anexoSelecionar: "Escolher arquivo",
        anexoRemover: "Remover anexo",
        anexoEnviando: "Enviando anexo...",
        anexoErro: "Falha ao enviar o anexo.",
        anexoLegendaPlaceholder: "Adicionar legenda (opcional)",
        anexoTipoNaoPermitido: "Tipo nao aceito.",
        anexoExcedeuLimite: "Excede o limite.",
        audioGravar: "Gravar áudio",
        audioGravando: "Gravando áudio",
        audioParar: "Parar gravação",
        audioDescartar: "Descartar gravação",
        audioEnviar: "Enviar gravação",
        audioPreview: "Pré-visualização da gravação",
        audioSemMicrofone: "Nenhum microfone disponível.",
        audioPermissaoNegada: "Permissão de microfone negada.",
        audioMicrofoneEmUso: "Microfone em uso.",
        audioErroCaptura: "Falha ao gravar.",
        audioExcedeuLimite: "Áudio excedeu o limite.",
        audioLimiteDuracao: "Duração máxima atingida.",
        emoji: "Emoji",
        janelaFechadaTitulo: "",
        janelaFechadaDescricao: "",
        semTemplates: "",
        escolherTemplate: "Enviar template",
        enviarTemplate: "Enviar este template",
        parametroTemplate: "Parâmetro {indice}",
        criarTemplate: "Criar template",
        templatesErro: "Erro templates",
        templatePendente: "Pendente",
        agendar: "Agendar mensagem",
        mensagensRapidas: "Mensagens rápidas",
      },
      mensagem: {
        status: {
          pendente: "Enviando",
          enviado: "Enviado",
          entregue: "Entregue",
          lido: "Lido",
          falhou: "Falha ao enviar",
        },
        reenviar: "Reenviar",
      },
    },
    mensagensProgramadas: {
      formulario: {
        tituloEditar: "Editar mensagem programada",
        tituloCriar: "Programar mensagem",
        lead: "Lead",
        selecionarLead: "Selecione um lead",
        dataEnvio: "Data e hora",
        conteudo: "Mensagem",
        erro: "Erro",
        cancelar: "Cancelar",
        salvar: "Salvar",
      },
    },
  }),
}));

import { Composer } from "./composer";

function renderizar() {
  const cliente = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return render(
    <QueryClientProvider client={cliente}>
      <Composer conversa={conversa} />
    </QueryClientProvider>,
  );
}

const conversa: CartaoAtendimento = {
  atendimentoId: "at-1",
  leadId: "lead-1",
  leadNome: "Cliente Teste",
  leadFotoUrl: null,
  leadEmpresa: null,
  canalTipo: "WHATSAPP",
  etapaId: null,
  etapaNome: null,
  etapaCor: null,
  status: "EM_ATENDIMENTO",
  atendenteId: "at-usr",
  atendenteNome: "Ana",
  ultimaMensagemPreview: null,
  ultimaMensagemRemetenteTipo: null,
  ultimaMensagemEm: null,
  ultimaMensagemDoLeadEm: new Date().toISOString(),
  naoLidas: 0,
};

function arquivoFake(nome: string, tipo: string): File {
  return new File(["conteudo"], nome, { type: tipo });
}

describe("Composer — anexo", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    estadoMidia.isError = false;
    estadoMidia.error = null;
    estadoTexto.isError = false;
    estadoTexto.error = null;
    configuracaoComposer.tamanhoMaximoAudioBytes = 1024;
    configuracaoComposer.duracaoMaximaAudioSegundos = 120;
    vi.stubGlobal("MediaRecorder", undefined);
    Object.defineProperty(window, "isSecureContext", {
      configurable: true,
      value: true,
    });
    Object.defineProperty(navigator, "mediaDevices", {
      configurable: true,
      value: undefined,
    });
    Object.defineProperty(URL, "createObjectURL", {
      configurable: true,
      value: vi.fn(() => "blob:gravacao"),
    });
    Object.defineProperty(URL, "revokeObjectURL", {
      configurable: true,
      value: vi.fn(),
    });
  });

  it("abre o formulário de mensagem programada sem desmontar o composer", async () => {
    renderizar();

    fireEvent.click(screen.getByRole("button", { name: "Agendar mensagem" }));

    expect(await screen.findByRole("dialog")).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Programar mensagem" })).toBeInTheDocument();
    expect(screen.getByPlaceholderText("Digite uma mensagem...")).toBeInTheDocument();
  });

  it("mostra o chip de preview com nome e tamanho ao selecionar um arquivo", () => {
    renderizar();
    const input = document.querySelector(
      'input[type="file"]',
    ) as HTMLInputElement;

    fireEvent.change(input, {
      target: { files: [arquivoFake("foto.png", "image/png")] },
    });

    expect(screen.getByText("foto.png")).toBeInTheDocument();
  });

  it("remove o arquivo selecionado ao clicar em remover", () => {
    renderizar();
    const input = document.querySelector(
      'input[type="file"]',
    ) as HTMLInputElement;
    fireEvent.change(input, {
      target: { files: [arquivoFake("foto.png", "image/png")] },
    });
    expect(screen.getByText("foto.png")).toBeInTheDocument();

    fireEvent.click(screen.getByLabelText("Remover anexo"));

    expect(screen.queryByText("foto.png")).not.toBeInTheDocument();
  });

  it("envia o arquivo selecionado com a legenda digitada ao clicar em enviar", () => {
    renderizar();
    const input = document.querySelector(
      'input[type="file"]',
    ) as HTMLInputElement;
    fireEvent.change(input, {
      target: { files: [arquivoFake("orcamento.pdf", "application/pdf")] },
    });

    fireEvent.change(
      screen.getByPlaceholderText("Adicionar legenda (opcional)"),
      {
        target: { value: "segue o orçamento" },
      },
    );
    fireEvent.click(screen.getByLabelText("Enviar"));

    expect(mutateMidia).toHaveBeenCalledWith(
      expect.objectContaining({
        atendimentoId: "at-1",
        leadId: "lead-1",
        legenda: "segue o orçamento",
      }),
      expect.anything(),
    );
    expect(mutateTexto).not.toHaveBeenCalled();
  });

  it("expande /palavra-chave com Enter sem enviar a mensagem", async () => {
    renderizar();
    const composer = screen.getByPlaceholderText("Digite uma mensagem...");
    fireEvent.change(composer, { target: { value: "/sau" } });
    expect(await screen.findByText("/saudacao")).toBeInTheDocument();

    fireEvent.keyDown(composer, { key: "Enter" });

    expect(composer).toHaveValue("Olá! Como posso ajudar?");
    expect(mutateTexto).not.toHaveBeenCalled();
  });

  it("abre respostas rápidas reais e preenche o textarea sem enviar", async () => {
    renderizar();

    fireEvent.click(
      await screen.findByRole("button", { name: "Mensagens rápidas" }),
    );
    fireEvent.click(await screen.findByText("Olá! Como posso ajudar?"));

    expect(screen.getByPlaceholderText("Digite uma mensagem...")).toHaveValue(
      "Olá! Como posso ajudar?",
    );
    expect(mutateTexto).not.toHaveBeenCalled();
  });

  it("mostra na tela quando o envio de texto falha", () => {
    estadoTexto.isError = true;
    estadoTexto.error = new Error("falha de rede");

    renderizar();

    expect(screen.getByRole("alert")).toHaveTextContent("Falha ao enviar");
  });

  it("nao mostra controle fantasma quando MediaRecorder esta indisponivel", () => {
    renderizar();

    expect(screen.queryByLabelText("Gravar áudio")).not.toBeInTheDocument();
    expect(screen.getByPlaceholderText("Digite uma mensagem...")).toBeEnabled();
  });

  it("grava, exige pre-visualizacao e envia um arquivo de audio mp4", async () => {
    habilitarGravacao();
    renderizar();

    fireEvent.click(await screen.findByLabelText("Gravar áudio"));
    fireEvent.click(await screen.findByLabelText("Parar gravação"));

    expect(await screen.findByLabelText("Pré-visualização da gravação")).toBeInTheDocument();
    expect(mutateMidia).not.toHaveBeenCalled();
    expect(screen.queryByLabelText("Enviar gravação")).not.toBeInTheDocument();
    fireEvent.click(screen.getByLabelText("Enviar"));

    const variaveis = mutateMidia.mock.calls[0]?.[0] as { arquivo: File };
    expect(variaveis.arquivo.type).toBe("audio/mp4;codecs=mp4a.40.2");
    expect(mutateMidia).toHaveBeenCalledTimes(1);
  });

  it("descarta a pre-visualizacao sem upload nem residuo remoto", async () => {
    habilitarGravacao();
    renderizar();

    fireEvent.click(await screen.findByLabelText("Gravar áudio"));
    fireEvent.click(await screen.findByLabelText("Parar gravação"));
    await screen.findByLabelText("Pré-visualização da gravação");
    fireEvent.click(screen.getByLabelText("Descartar gravação"));

    expect(
      screen.queryByLabelText("Pré-visualização da gravação"),
    ).not.toBeInTheDocument();
    expect(mutateMidia).not.toHaveBeenCalled();
    expect(URL.revokeObjectURL).toHaveBeenCalledWith("blob:gravacao");
  });

  it("mantem o texto e o composer utilizavel quando a permissao e negada", async () => {
    habilitarGravacao(
      vi.fn().mockRejectedValue(new DOMException("negado", "NotAllowedError")),
    );
    renderizar();
    const campo = screen.getByPlaceholderText("Digite uma mensagem...");
    fireEvent.change(campo, { target: { value: "texto preservado" } });

    fireEvent.click(await screen.findByLabelText("Gravar áudio"));

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Permissão de microfone negada",
    );
    expect(campo).toHaveValue("texto preservado");
    expect(campo).toBeEnabled();
  });

  it("encerra no limite de duracao e preserva a gravacao para revisao", async () => {
    configuracaoComposer.duracaoMaximaAudioSegundos = 0;
    habilitarGravacao();
    renderizar();

    fireEvent.click(await screen.findByLabelText("Gravar áudio"));

    expect(
      await screen.findByLabelText(
        "Pré-visualização da gravação",
        {},
        { timeout: 1000 },
      ),
    ).toBeInTheDocument();
    expect(screen.getByRole("status")).toHaveTextContent(
      "Duração máxima atingida",
    );
  });

  it("recusa gravacao acima do limite antes de chamar o upload", async () => {
    configuracaoComposer.tamanhoMaximoAudioBytes = 1;
    habilitarGravacao();
    renderizar();

    fireEvent.click(await screen.findByLabelText("Gravar áudio"));
    fireEvent.click(await screen.findByLabelText("Parar gravação"));

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Áudio excedeu o limite",
    );
    expect(screen.getByLabelText("Enviar")).toBeDisabled();
    expect(mutateMidia).not.toHaveBeenCalled();
  });

  it("não descarta áudio-only quando o navegador declara video/quicktime", async () => {
    habilitarGravacao(vi.fn().mockResolvedValue({ getTracks: () => [{ stop: vi.fn() }] }), "video/quicktime");
    renderizar();

    fireEvent.click(await screen.findByLabelText("Gravar áudio"));
    fireEvent.click(await screen.findByLabelText("Parar gravação"));

    expect(await screen.findByLabelText("Pré-visualização da gravação")).toBeInTheDocument();
    fireEvent.click(screen.getByLabelText("Enviar"));

    expect(mutateMidia).toHaveBeenCalledWith(
      expect.objectContaining({ arquivo: expect.any(File) }),
      expect.anything(),
    );
    expect((mutateMidia.mock.calls[0]?.[0] as { arquivo: File }).arquivo.type).toBe(
      "video/quicktime",
    );
  });

  it("não cria preview nem envia gravação vazia", async () => {
    habilitarGravacao(
      vi.fn().mockResolvedValue({ getTracks: () => [{ stop: vi.fn() }] }),
      "audio/mp4;codecs=mp4a.40.2",
      "",
    );
    renderizar();

    fireEvent.click(await screen.findByLabelText("Gravar áudio"));
    fireEvent.click(await screen.findByLabelText("Parar gravação"));

    expect(await screen.findByRole("alert")).toHaveTextContent("Falha ao gravar.");
    expect(screen.queryByLabelText("Pré-visualização da gravação")).not.toBeInTheDocument();
    expect(mutateMidia).not.toHaveBeenCalled();
  });

  it("limpa preview e controles quando o upload da gravação falha", async () => {
    habilitarGravacao();
    estadoMidia.isError = true;
    estadoMidia.error = new Error("falha de rede");
    renderizar();

    fireEvent.click(await screen.findByLabelText("Gravar áudio"));
    fireEvent.click(await screen.findByLabelText("Parar gravação"));
    await screen.findByLabelText("Pré-visualização da gravação");

    mutateMidia.mockImplementationOnce(
      (_variaveis: unknown, opcoes: { onError?: () => void }) => {
        opcoes.onError?.();
      },
    );
    fireEvent.click(screen.getByLabelText("Enviar"));

    expect(screen.queryByLabelText("Pré-visualização da gravação")).not.toBeInTheDocument();
    expect(screen.getByLabelText("Gravar áudio")).toBeInTheDocument();
    expect(screen.getByRole("alert")).toHaveTextContent("Falha ao enviar o anexo.");
  });

  it.each([
    ["NotFoundError", "Nenhum microfone disponível."],
    ["NotReadableError", "Microfone em uso."],
  ])(
    "informa o motivo quando a captura falha com %s",
    async (nome, mensagem) => {
      habilitarGravacao(
        vi.fn().mockRejectedValue(new DOMException("falha", nome)),
      );
      renderizar();

      fireEvent.click(await screen.findByLabelText("Gravar áudio"));

      expect(await screen.findByRole("alert")).toHaveTextContent(mensagem);
    },
  );
});

function habilitarGravacao(
  getUserMedia = vi.fn().mockResolvedValue({
    getTracks: () => [{ stop: vi.fn() }],
  }),
  mimeResult = "audio/mp4;codecs=mp4a.40.2",
  dados = "audio gravado",
) {
  class MediaRecorderFake {
    static isTypeSupported(tipo: string) {
      return tipo === "audio/mp4;codecs=mp4a.40.2";
    }

    readonly mimeType: string;
    state: RecordingState = "inactive";
    ondataavailable: ((evento: BlobEvent) => void) | null = null;
    onstop: ((evento: Event) => void) | null = null;
    onerror: ((evento: Event) => void) | null = null;

    constructor(_stream: MediaStream, opcoes?: MediaRecorderOptions) {
      this.mimeType = mimeResult || opcoes?.mimeType || "";
    }

    start() {
      this.state = "recording";
    }

    stop() {
      this.state = "inactive";
      this.ondataavailable?.({
        data: new Blob([dados], { type: this.mimeType }),
      } as BlobEvent);
      this.onstop?.(new Event("stop"));
    }
  }

  vi.stubGlobal("MediaRecorder", MediaRecorderFake);
  Object.defineProperty(navigator, "mediaDevices", {
    configurable: true,
    value: { getUserMedia },
  });
}
