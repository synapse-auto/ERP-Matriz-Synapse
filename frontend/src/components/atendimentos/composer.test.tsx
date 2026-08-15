import { fireEvent, render, screen } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { beforeEach, describe, expect, it, vi } from "vitest";

import type { CartaoAtendimento } from "@/lib/atendimento/types";

const mutateMidia = vi.fn();
const mutateTexto = vi.fn();
const estadoTexto: { isError: boolean; error: Error | null } = { isError: false, error: null };

vi.mock("@/lib/atendimento/use-enviar-mensagem", () => ({
  useEnviarMensagem: () => ({ mutate: mutateTexto, isPending: false, ...estadoTexto }),
}));

vi.mock("@/lib/atendimento/use-enviar-midia", () => ({
  useEnviarMidia: () => ({
    mutate: mutateMidia,
    isPending: false,
    isError: false,
    error: null,
  }),
}));

vi.mock("@/lib/atendimento/janela-24h", () => ({
  janelaTextoLivreAberta: () => true,
}));

vi.mock("@/lib/suporte/api", () => ({
  listarMensagensRapidas: () => Promise.resolve([
    { id: "rapida-1", atendenteId: "ana", atendenteNome: "Ana", palavraChave: "saudacao", conteudo: "Olá! Como posso ajudar?", tipoMidia: null },
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
        emoji: "Emoji",
        janelaFechadaTitulo: "",
        janelaFechadaDescricao: "",
        semTemplates: "",
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
  const cliente = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(<QueryClientProvider client={cliente}><Composer conversa={conversa} /></QueryClientProvider>);
}

const conversa: CartaoAtendimento = {
  atendimentoId: "at-1",
  leadId: "lead-1",
  leadNome: "Cliente Teste",
  leadFotoUrl: null,
  leadEmpresa: null,
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
};

function arquivoFake(nome: string, tipo: string): File {
  return new File(["conteudo"], nome, { type: tipo });
}

describe("Composer — anexo", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    estadoTexto.isError = false;
    estadoTexto.error = null;
  });

  it("mostra o chip de preview com nome e tamanho ao selecionar um arquivo", () => {
    renderizar();
    const input = document.querySelector('input[type="file"]') as HTMLInputElement;

    fireEvent.change(input, { target: { files: [arquivoFake("foto.png", "image/png")] } });

    expect(screen.getByText("foto.png")).toBeInTheDocument();
  });

  it("remove o arquivo selecionado ao clicar em remover", () => {
    renderizar();
    const input = document.querySelector('input[type="file"]') as HTMLInputElement;
    fireEvent.change(input, { target: { files: [arquivoFake("foto.png", "image/png")] } });
    expect(screen.getByText("foto.png")).toBeInTheDocument();

    fireEvent.click(screen.getByLabelText("Remover anexo"));

    expect(screen.queryByText("foto.png")).not.toBeInTheDocument();
  });

  it("envia o arquivo selecionado com a legenda digitada ao clicar em enviar", () => {
    renderizar();
    const input = document.querySelector('input[type="file"]') as HTMLInputElement;
    fireEvent.change(input, { target: { files: [arquivoFake("orcamento.pdf", "application/pdf")] } });

    fireEvent.change(screen.getByPlaceholderText("Adicionar legenda (opcional)"), {
      target: { value: "segue o orçamento" },
    });
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

  it("mostra na tela quando o envio de texto falha", () => {
    estadoTexto.isError = true;
    estadoTexto.error = new Error("falha de rede");

    renderizar();

    expect(screen.getByRole("alert")).toHaveTextContent("Falha ao enviar");
  });
});
