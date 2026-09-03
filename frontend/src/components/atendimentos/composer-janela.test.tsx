import { fireEvent, render, screen } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { describe, expect, it, vi } from "vitest";

import type { CartaoAtendimento } from "@/lib/atendimento/types";

vi.mock("@/lib/atendimento/use-enviar-mensagem", () => ({
  useEnviarMensagem: () => ({ mutate: vi.fn(), isPending: false, isError: false, error: null }),
}));

vi.mock("@/lib/atendimento/use-enviar-midia", () => ({
  useEnviarMidia: () => ({
    mutate: vi.fn(),
    mutateAsync: vi.fn(),
    isPending: false,
    isError: false,
    error: null,
  }),
}));

vi.mock("@/lib/atendimento/use-configuracao-composer", () => ({
  useConfiguracaoComposer: () => ({ data: undefined }),
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

vi.mock("@/lib/lead/use-painel-lead", () => ({
  useLead: () => ({ data: undefined }),
}));

vi.mock("@/lib/suporte/api", () => ({
  listarMensagensRapidas: () => Promise.resolve([]),
}));

vi.mock("@/components/mensagens-programadas/formulario-mensagem-programada", () => ({
  FormularioMensagemProgramada: () => null,
}));

vi.mock("@/components/mensagens/painel-emoji-composer", () => ({
  PainelEmojiComposer: () => null,
}));

vi.mock("@/lib/config/textos-provider", () => ({
  useTextos: () => ({
    atendimentos: {
      composer: {
        placeholder: "Digite uma mensagem...",
        enviar: "Enviar",
        anexo: "Anexo",
        anexoIndisponivel: "",
        anexoSelecionar: "",
        anexoMenuArquivos: "Arquivos",
        anexoMenuTemplates: "Templates",
        anexoRemover: "",
        anexoEnviando: "",
        anexoErro: "",
        respostaErro: "",
        anexoLegendaPlaceholder: "",
        anexoTipoNaoPermitido: "",
        anexoSoltar: "",
        anexoEnviandoLote: "",
        anexoExcedeuLimite: "",
        audioGravar: "Gravar áudio",
        audioGravando: "",
        audioParar: "",
        audioDescartar: "",
        audioEnviar: "",
        audioPreview: "",
        audioSemMicrofone: "",
        audioPermissaoNegada: "",
        audioMicrofoneEmUso: "",
        audioErroCaptura: "",
        audioExcedeuLimite: "",
        audioLimiteDuracao: "",
        emoji: "Emoji",
        janelaAberta: "Janela de texto livre aberta",
        janelaFechadaTitulo: "A janela de 24h encerrou",
        janelaFechadaDescricao:
          "A regra é da Meta: depois de 24h sem mensagem do cliente, só um template aprovado chega.",
        janelaInexistenteTitulo: "Ainda sem mensagem do cliente",
        janelaInexistenteDescricao: "Este contato ainda não escreveu, então não há janela de texto livre.",
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
        colunaTemplates: "Templates",
        colunaConfiguracao: "Configuração de envio",
        colunaPrevia: "Prévia",
        parametroEnvio: "Mensagem — variável {indice}",
        marcadorVariavelVazia: "[variável {indice}]",
        configuracaoSemSelecao: "Escolha um template para preencher as variáveis de envio.",
        previaSemSelecao: "Escolha um template para ver como a mensagem chega.",
        configuracaoSemVariaveis: "Não há nada a preencher neste template.",
        novaMensagem: "Nova mensagem",
        cancelarTemplate: "Cancelar",
        templatesErro: "Erro templates",
        templatePendente: "Pendente",
        agendar: "Agendar mensagem",
        mensagensRapidas: "Mensagens rápidas",
      },
      mensagem: {
        status: { falhou: "Falha ao enviar" },
        acoes: { seletor: { search: "", categories: {}, skins: {} } },
        citacao: { cancelar: "Cancelar resposta" },
      },
      finalizar: { sucesso: "Finalizado" },
    },
    mensagensProgramadas: { formulario: { tituloCriar: "Programar", cancelar: "Cancelar", salvar: "Salvar" } },
    templatesWhatsApp: {
      categorias: { UTILIDADE: "Utilidade", MARKETING: "Marketing", AUTENTICACAO: "Autenticação" },
      status: {
        APROVADO: "Aprovado",
        PENDENTE: "Pendente",
        REJEITADO: "Rejeitado",
        PAUSADO: "Pausado",
        DESCONHECIDO: "Desconhecido",
      },
    },
  }),
}));

import { Composer } from "./composer";

const base: CartaoAtendimento = {
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
  ultimaMensagemDoLeadEm: null,
  naoLidas: 0,
};

function renderizar(conversa: CartaoAtendimento) {
  const cliente = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={cliente}>
      <Composer conversa={conversa} />
    </QueryClientProvider>,
  );
}

describe("Composer — janela de 24h", () => {
  it("mostra indicação discreta quando a janela está aberta e libera o texto livre", () => {
    renderizar({ ...base, ultimaMensagemDoLeadEm: new Date().toISOString() });

    const indicacao = screen.getByText("Janela de texto livre aberta");
    expect(indicacao).toBeInTheDocument();
    expect(indicacao).toHaveClass("text-muted-foreground");
    expect(indicacao.className).not.toMatch(/bg-cor-|bg-destructive|bg-primary/);
    expect(screen.getByPlaceholderText("Digite uma mensagem...")).toBeEnabled();
  });

  it("flutua sobre o historico com largura responsiva e escala de icones local", () => {
    renderizar({ ...base, ultimaMensagemDoLeadEm: new Date().toISOString() });

    const composer = document.querySelector('[data-slot="composer"]');
    expect(composer).toHaveClass("absolute", "bottom-0", "pointer-events-none");
    expect(composer).not.toHaveClass("bg-background");
    expect(composer?.querySelector(".max-w-\\[874px\\]")).toBeInTheDocument();
    expect(composer?.querySelector('[class*="--tamanho-icone-interface"]')).toBeInTheDocument();
    expect(screen.getByText("Janela de texto livre aberta").closest("[data-slot=\"composer\"]")?.querySelector(".bg-card")).toBeInTheDocument();
  });

  it("bloqueia o texto livre e explica a regra da Meta quando a janela fechou", async () => {
    const { container } = renderizar({
      ...base,
      ultimaMensagemDoLeadEm: new Date(Date.now() - 25 * 60 * 60 * 1000).toISOString(),
    });

    expect(screen.getByText("A janela de 24h encerrou")).toBeInTheDocument();
    expect(screen.getByText(/A regra é da Meta/)).toBeInTheDocument();
    expect(screen.queryByPlaceholderText("Digite uma mensagem...")).not.toBeInTheDocument();
    expect(screen.queryByText("boas_vindas")).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Nova mensagem" })).toBeInTheDocument();
    expect(container.textContent).not.toMatch(/\d{1,2}:\d{2}/);
    expect(container.textContent).not.toMatch(/faltam \d/i);

    fireEvent.click(screen.getByRole("button", { name: "Nova mensagem" }));
    expect(await screen.findByRole("dialog")).toBeInTheDocument();
    expect(await screen.findByText("boas_vindas")).toBeInTheDocument();
  });

  it("não trata lead que nunca escreveu como janela fechada com cara de erro", () => {
    renderizar({ ...base, ultimaMensagemDoLeadEm: null });

    expect(screen.getByText("Ainda sem mensagem do cliente")).toBeInTheDocument();
    expect(screen.getByText("Ainda sem mensagem do cliente")).toHaveClass("text-foreground");
    expect(screen.queryByText("A janela de 24h encerrou")).not.toBeInTheDocument();
    expect(screen.queryByPlaceholderText("Digite uma mensagem...")).not.toBeInTheDocument();
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
    expect(screen.queryByText("boas_vindas")).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Nova mensagem" })).toBeInTheDocument();
  });
});
