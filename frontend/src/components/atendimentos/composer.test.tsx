import { createRef } from "react";
import { createEvent, fireEvent, render, screen, waitFor, act } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { beforeEach, describe, expect, it, vi } from "vitest";

import type { CartaoAtendimento, MensagemResposta } from "@/lib/atendimento/types";

const mutateMidia = vi.fn();
const mutateTexto = vi.fn();
const onCancelarResposta = vi.fn();
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
    mutateAsync: (variaveis: unknown) => Promise.resolve(mutateMidia(variaveis)),
    isPending: false,
    ...estadoMidia,
  }),
}));

vi.mock("@/lib/atendimento/use-configuracao-composer", () => ({
  useConfiguracaoComposer: () => ({ data: configuracaoComposer }),
}));

vi.mock("@/lib/atendimento/janela-24h", () => ({
  janelaTextoLivreAberta: () => true,
  estadoDaJanelaTextoLivre: () => "aberta",
}));

vi.mock("@/lib/atendimento/api", () => ({
  listarTemplatesWhatsApp: () =>
    Promise.resolve([
      {
        nome: "boas_vindas",
        idioma: "pt_BR",
        categoria: "UTILIDADE",
        status: "APROVADO",
        corpo: "Olá, bem-vindo",
        quantidadeDeParametros: 0,
      },
    ]),
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

vi.mock("@/components/mensagens/painel-emoji-composer", () => ({
  PainelEmojiComposer: ({
    rotulo,
    onEscolher,
  }: {
    rotulo: string;
    onEscolher: (emoji: string) => void;
  }) => (
    <>
      <button type="button" aria-label={rotulo} />
      <input aria-label="Buscar emoji" />
      <button type="button">Natureza</button>
      <button type="button" onClick={() => onEscolher("👍🏽")}>
        👍🏽
      </button>
    </>
  ),
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
        anexoMenuArquivos: "Arquivos",
        anexoMenuTemplates: "Templates",
        anexoRemover: "Remover anexo",
        anexoEnviando: "Enviando anexo...",
        anexoErro: "Falha ao enviar o anexo.",
        respostaErro: "Não foi possível responder a esta mensagem.",
        anexoLegendaPlaceholder: "Adicionar legenda (opcional)",
        anexoTipoNaoPermitido: "Tipo nao aceito.",
        anexoSoltar: "Solte os arquivos aqui",
        anexoEnviandoLote: "Enviando {atual} de {total}",
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
        janelaAberta: "Janela de texto livre aberta",
        janelaFechadaTitulo: "A janela de 24h encerrou",
        janelaFechadaDescricao: "A regra é da Meta.",
        janelaInexistenteTitulo: "Ainda sem mensagem do cliente",
        janelaInexistenteDescricao: "Este contato ainda não escreveu.",
        semTemplates: "Nenhum template aprovado ainda.",
        templatesCarregando: "Carregando templates...",
        escolherTemplate: "Enviar template",
        enviarTemplate: "Enviar este template",
        parametroTemplate: "Variável {indice}",
        parametroObrigatorio: "Preencha esta variável para enviar.",
        previaTemplate: "Prévia",
        buscaTemplate: "Buscar template",
        semResultadosTemplate: "Nenhum template encontrado.",
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
        acoes: {
          seletor: {
            search: "Buscar emoji",
            searchNoResults: "Nenhum",
            pick: "Escolha",
            addCustom: "Custom",
            categories: {
              activity: "A",
              custom: "C",
              flags: "F",
              foods: "Fo",
              frequent: "R",
              nature: "Natureza",
              objects: "O",
              people: "P",
              places: "V",
              search: "B",
              symbols: "S",
            },
            skins: { choose: "Tom", 1: "1", 2: "2", 3: "3", 4: "4", 5: "5", 6: "6" },
          },
        },
        citacao: {
          resposta: "Respondendo a {autor}",
          encaminhamento: "Encaminhada",
          cancelar: "Cancelar resposta",
          origemIndisponivel: "Mensagem original indisponível",
          imagem: "Foto",
          audio: "Áudio",
          documento: "Documento",
        },
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
    templatesWhatsApp: {
      categorias: {
        UTILIDADE: "Utilidade",
        MARKETING: "Marketing",
        AUTENTICACAO: "Autenticação",
      },
    },
  }),
}));

import { Composer, type ComposerHandle } from "./composer";

function renderizar(resposta?: MensagemResposta) {
  const cliente = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return render(
    <QueryClientProvider client={cliente}>
      <Composer conversa={conversa} resposta={resposta ?? null} onCancelarResposta={onCancelarResposta} />
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
    const { container } = renderizar();
    expect(container.firstElementChild).toHaveClass("shrink-0");

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

  it("abre o menu do clipe para cima com Arquivos e Templates", async () => {
    renderizar();
    fireEvent.click(screen.getByRole("button", { name: "Anexo" }));

    expect(await screen.findByRole("menuitem", { name: "Arquivos" })).toBeInTheDocument();
    expect(screen.getByRole("menuitem", { name: "Templates" })).toBeInTheDocument();
    expect(screen.queryByRole("heading", { name: "Enviar template" })).not.toBeInTheDocument();
  });

  it("Arquivos no menu dispara o seletor de arquivo atual", async () => {
    renderizar();
    const input = document.querySelector(
      'input[type="file"]',
    ) as HTMLInputElement;
    const abrir = vi.spyOn(input, "click");

    fireEvent.click(screen.getByRole("button", { name: "Anexo" }));
    fireEvent.click(await screen.findByRole("menuitem", { name: "Arquivos" }));

    await waitFor(() => expect(abrir).toHaveBeenCalled());
  });

  it("Templates no menu abre o catálogo aprovado e envia sem texto livre", async () => {
    renderizar();
    fireEvent.click(screen.getByRole("button", { name: "Anexo" }));
    fireEvent.click(await screen.findByRole("menuitem", { name: "Templates" }));

    expect(await screen.findByRole("heading", { name: "Enviar template" })).toBeInTheDocument();
    expect(await screen.findByText("boas_vindas")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Enviar este template" }));

    expect(mutateTexto).toHaveBeenCalledWith(
      expect.objectContaining({
        atendimentoId: "at-1",
        leadId: "lead-1",
        template: {
          nome: "boas_vindas",
          idioma: "pt_BR",
          parametros: [],
        },
      }),
    );
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
    );
    expect(mutateTexto).not.toHaveBeenCalled();
  });

  it("acumula varios arquivos no seletor e envia um POST de midia por arquivo", async () => {
    renderizar();
    const input = document.querySelector(
      'input[type="file"]',
    ) as HTMLInputElement;
    expect(input.multiple).toBe(true);

    fireEvent.change(input, {
      target: {
        files: [
          arquivoFake("foto.png", "image/png"),
          arquivoFake("orcamento.pdf", "application/pdf"),
        ],
      },
    });

    expect(screen.getByText("foto.png")).toBeInTheDocument();
    expect(screen.getByText("orcamento.pdf")).toBeInTheDocument();

    fireEvent.change(
      screen.getByPlaceholderText("Adicionar legenda (opcional)"),
      { target: { value: "lote" } },
    );
    fireEvent.click(screen.getByLabelText("Enviar"));

    await waitFor(() => expect(mutateMidia).toHaveBeenCalledTimes(2));
    expect(mutateMidia.mock.calls[0]?.[0]).toEqual(
      expect.objectContaining({
        arquivo: expect.objectContaining({ name: "foto.png" }),
        legenda: "lote",
      }),
    );
    expect(mutateMidia.mock.calls[1]?.[0]).toEqual(
      expect.objectContaining({
        arquivo: expect.objectContaining({ name: "orcamento.pdf" }),
        legenda: undefined,
      }),
    );
    await waitFor(() => {
      expect(screen.queryByText("foto.png")).not.toBeInTheDocument();
      expect(screen.queryByText("orcamento.pdf")).not.toBeInTheDocument();
    });
  });

  it("mantem os arquivos que ainda nao sairam quando um envio do lote falha", async () => {
    mutateMidia
      .mockReturnValueOnce(undefined)
      .mockImplementationOnce(() => Promise.reject(new Error("falha")));
    renderizar();
    const input = document.querySelector(
      'input[type="file"]',
    ) as HTMLInputElement;
    fireEvent.change(input, {
      target: {
        files: [
          arquivoFake("a.png", "image/png"),
          arquivoFake("b.png", "image/png"),
        ],
      },
    });
    fireEvent.click(screen.getByLabelText("Enviar"));

    await waitFor(() => expect(mutateMidia).toHaveBeenCalledTimes(2));
    await waitFor(() => {
      expect(screen.queryByText("a.png")).not.toBeInTheDocument();
      expect(screen.getByText("b.png")).toBeInTheDocument();
    });
  });

  it("recusa tipo nao permitido e avisa sem enfileirar", () => {
    renderizar();
    const input = document.querySelector(
      'input[type="file"]',
    ) as HTMLInputElement;
    fireEvent.change(input, {
      target: { files: [arquivoFake("setup.exe", "application/x-msdownload")] },
    });

    expect(screen.queryByText("setup.exe")).not.toBeInTheDocument();
    expect(screen.getByRole("alert")).toHaveTextContent("Tipo nao aceito.");
  });

  it("recebe arquivos pela api do composer usada no arrastar e soltar", () => {
    const referencia = createRef<ComposerHandle>();
    const cliente = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    });
    render(
      <QueryClientProvider client={cliente}>
        <Composer
          ref={referencia}
          conversa={conversa}
          resposta={null}
          onCancelarResposta={onCancelarResposta}
        />
      </QueryClientProvider>,
    );

    expect(referencia.current).not.toBeNull();
    act(() => {
      referencia.current?.adicionarArquivos([
        arquivoFake("arrastada.png", "image/png"),
        arquivoFake("setup.exe", "application/x-msdownload"),
      ]);
    });

    expect(screen.getByText("arrastada.png")).toBeInTheDocument();
    expect(screen.queryByText("setup.exe")).not.toBeInTheDocument();
    expect(screen.getByRole("alert")).toHaveTextContent("Tipo nao aceito.");
  });

  it("cola imagem como anexo com preview e nao envia automaticamente", () => {
    renderizar();
    const campo = screen.getByPlaceholderText("Digite uma mensagem...");
    const imagem = arquivoFake("image.png", "image/jpeg");

    fireEvent.paste(campo, {
      clipboardData: { files: [imagem] },
    });

    expect(screen.getByText("imagem-colada.jpg")).toBeInTheDocument();
    expect(mutateMidia).not.toHaveBeenCalled();
    expect(mutateTexto).not.toHaveBeenCalled();
  });

  it("cola varios arquivos pelo mesmo caminho do anexo", () => {
    renderizar();
    const campo = screen.getByPlaceholderText("Digite uma mensagem...");

    fireEvent.paste(campo, {
      clipboardData: {
        files: [arquivoFake("image.png", "image/png"), arquivoFake("arquivo.pdf", "application/pdf")],
      },
    });

    expect(screen.getByText("imagem-colada-1.png")).toBeInTheDocument();
    expect(screen.getByText("arquivo-colado-2.pdf")).toBeInTheDocument();
  });

  it("mantem colagem de texto no campo sem criar anexo", () => {
    renderizar();
    const campo = screen.getByPlaceholderText("Digite uma mensagem...");

    const evento = createEvent.paste(campo, {
      clipboardData: { files: [], getData: () => "texto colado" },
    });
    const prevenir = vi.spyOn(evento, "preventDefault");
    fireEvent(campo, evento);

    expect(screen.queryByText(/colada/)).not.toBeInTheDocument();
    expect(mutateMidia).not.toHaveBeenCalled();
    expect(prevenir).not.toHaveBeenCalled();
  });

  it("recusa tipo colado nao permitido com a mesma mensagem do seletor", () => {
    renderizar();
    const campo = screen.getByPlaceholderText("Digite uma mensagem...");

    fireEvent.paste(campo, {
      clipboardData: { files: [arquivoFake("planilha.csv", "text/csv")] },
    });

    expect(screen.queryByText("arquivo-colado.csv")).not.toBeInTheDocument();
    expect(screen.getByRole("alert")).toHaveTextContent("Tipo nao aceito.");
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

  it("abre o catálogo completo e insere o emoji no texto", async () => {
    renderizar();
    const campo = screen.getByPlaceholderText("Digite uma mensagem...");

    fireEvent.click(screen.getByRole("button", { name: "Emoji" }));
    expect(await screen.findByLabelText("Buscar emoji")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Natureza" })).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "👍🏽" }));
    expect(campo).toHaveValue("👍🏽");
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

  it("mostra a citação da resposta, envia o vínculo e cancela com Escape sem perder o rascunho", () => {
    const origem: MensagemResposta = {
      id: "msg-origem",
      remetenteTipo: "LEAD",
      remetenteId: null,
      remetenteNome: "Maria",
      tipo: "TEXTO",
      conteudo: "preciso de orçamento",
      midiaUrl: null,
      midiaMetadados: null,
      opcoes: null,
      statusEntrega: "ENTREGUE",
      enviadoEm: "2026-08-29T12:00:00Z",
    };
    renderizar(origem);
    const campo = screen.getByPlaceholderText("Digite uma mensagem...");
    fireEvent.change(campo, { target: { value: "já estou vendo" } });

    expect(screen.getByText("Respondendo a Maria")).toBeInTheDocument();
    expect(screen.getByText("preciso de orçamento")).toBeInTheDocument();

    fireEvent.click(screen.getByLabelText("Enviar"));
    expect(mutateTexto).toHaveBeenCalledWith(
      expect.objectContaining({
        conteudo: "já estou vendo",
        resposta: { mensagemId: "msg-origem", enviadoEm: "2026-08-29T12:00:00Z" },
      }),
      expect.anything(),
    );
    expect(campo).toHaveValue("já estou vendo");

    fireEvent.keyDown(campo, { key: "Escape" });
    expect(onCancelarResposta).toHaveBeenCalled();
    expect(campo).toHaveValue("já estou vendo");
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
    expect(variaveis.arquivo.type).toBe("audio/mp4");
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
