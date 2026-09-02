import { fireEvent, render, screen, within } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { beforeEach, describe, expect, it, vi } from "vitest";

const apiMocks = vi.hoisted(() => ({
  obterCapacidadeDoCanal: vi.fn(),
  listarTemplatesWhatsApp: vi.fn(),
}));

const textosComposer = {
  semTemplates: "Nenhum template aprovado.",
  templatesCarregando: "Carregando templates...",
  templatesErro: "Não foi possível carregar os templates.",
  escolherTemplate: "Escolher template",
  enviarTemplate: "Enviar template",
  parametroTemplate: "Parâmetro {indice}",
  parametroObrigatorio: "Preencha este parâmetro.",
  previaTemplate: "Prévia",
  buscaTemplate: "Buscar template",
  semResultadosTemplate: "Nenhum resultado.",
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
};

const textos = {
  botao: "Novo atendimento",
  titulo: "Novo atendimento",
  descricao: "Abra uma conversa em modo humano pelo WhatsApp.",
  nome: "Nome do contato",
  nomePlaceholder: "Nome do contato",
  nomeObrigatorio: "Nome do contato é obrigatório.",
  telefone: "Telefone",
  telefonePlaceholder: "(83) 99999-9999",
  telefoneObrigatorio: "Telefone é obrigatório.",
  primeiraMensagem: "Primeira mensagem (opcional)",
  primeiraMensagemPlaceholder: "Digite a mensagem que será enviada após abrir o atendimento.",
  avisoTemplate:
    "A mensagem será enviada após a abertura do atendimento. Dependendo do canal, pode ser necessário utilizar um template aprovado.",
  cancelar: "Cancelar",
  confirmar: "Iniciar atendimento",
  erro: "Não foi possível iniciar o atendimento.",
};

vi.mock("@/lib/config/textos-provider", () => ({
  useTextos: () => ({
    atendimentos: { novoContato: textos, composer: textosComposer },
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

vi.mock("@/lib/atendimento/api", () => apiMocks);

import { DialogoNovoContato, mascararTelefoneBr } from "./dialogo-novo-contato";

function renderDialog(
  ui: React.ReactElement,
  capacidade?: boolean,
) {
  const cliente = new QueryClient({
    defaultOptions: { queries: { retry: false, staleTime: Infinity } },
  });
  if (capacidade !== undefined) {
    cliente.setQueryData(["config", "canal"], {
      exigeTemplateForaDaJanela: capacidade,
    });
  }
  return render(<QueryClientProvider client={cliente}>{ui}</QueryClientProvider>);
}

function preencherContato() {
  fireEvent.change(screen.getByLabelText("Nome do contato"), { target: { value: "Maria" } });
  fireEvent.change(screen.getByLabelText("Telefone"), { target: { value: "83999998888" } });
}

beforeEach(() => {
  apiMocks.obterCapacidadeDoCanal.mockResolvedValue({ exigeTemplateForaDaJanela: false });
  apiMocks.listarTemplatesWhatsApp.mockResolvedValue([]);
});

describe("DialogoNovoContato", () => {
  it("mostra os erros de nome e telefone sem enviar", () => {
    const confirmar = vi.fn();
    renderDialog(
      <DialogoNovoContato aberto onFechar={vi.fn()} onConfirmar={confirmar} />,
      false,
    );

    fireEvent.click(screen.getByRole("button", { name: "Iniciar atendimento" }));
    expect(screen.getByText("Nome do contato é obrigatório.")).toBeInTheDocument();
    expect(screen.getByText("Telefone é obrigatório.")).toBeInTheDocument();
    expect(confirmar).not.toHaveBeenCalled();
  });

  it("mascara o telefone brasileiro e envia nome e telefone", () => {
    const confirmar = vi.fn();
    renderDialog(
      <DialogoNovoContato aberto onFechar={vi.fn()} onConfirmar={confirmar} />,
      false,
    );

    expect(screen.getByPlaceholderText("(83) 99999-9999")).toBeInTheDocument();
    expect(
      screen.getByText(
        "A mensagem será enviada após a abertura do atendimento. Dependendo do canal, pode ser necessário utilizar um template aprovado.",
      ),
    ).toBeInTheDocument();

    fireEvent.change(screen.getByLabelText("Nome do contato"), { target: { value: "Maria" } });
    fireEvent.change(screen.getByLabelText("Telefone"), { target: { value: "83999998888" } });
    expect(screen.getByLabelText("Telefone")).toHaveValue("(83) 99999-8888");

    fireEvent.click(screen.getByRole("button", { name: "Iniciar atendimento" }));
    expect(confirmar).toHaveBeenCalledWith({ nome: "Maria", telefone: "(83) 99999-8888" });
  });

  it("envia a primeira mensagem só quando preenchida e mostra o erro recebido", () => {
    const confirmar = vi.fn();
    renderDialog(
      <DialogoNovoContato
        aberto
        onFechar={vi.fn()}
        onConfirmar={confirmar}
        erro="a janela de 24h esta fechada"
      />,
      false,
    );

    fireEvent.change(screen.getByLabelText("Nome do contato"), { target: { value: "Maria" } });
    fireEvent.change(screen.getByLabelText("Telefone"), { target: { value: "83988887777" } });
    fireEvent.change(screen.getByLabelText("Primeira mensagem (opcional)"), {
      target: { value: "  ola  " },
    });
    fireEvent.click(screen.getByRole("button", { name: "Iniciar atendimento" }));

    expect(confirmar).toHaveBeenCalledWith({
      nome: "Maria",
      telefone: "(83) 98888-7777",
      primeiraMensagem: "ola",
    });
    expect(screen.getByText("a janela de 24h esta fechada")).toBeInTheDocument();
  });

  it("cancela sem submeter", () => {
    const fechar = vi.fn();
    const confirmar = vi.fn();
    renderDialog(<DialogoNovoContato aberto onFechar={fechar} onConfirmar={confirmar} />, false);
    fireEvent.click(screen.getByRole("button", { name: "Cancelar" }));
    expect(fechar).toHaveBeenCalledTimes(1);
    expect(confirmar).not.toHaveBeenCalled();
  });

  it("mantém o campo em carregamento até conhecer a capacidade do canal", () => {
    apiMocks.obterCapacidadeDoCanal.mockReturnValue(new Promise(() => undefined));

    renderDialog(<DialogoNovoContato aberto onFechar={vi.fn()} onConfirmar={vi.fn()} />);

    expect(screen.getByRole("status")).toHaveTextContent("Carregando templates...");
    expect(screen.queryByLabelText("Primeira mensagem (opcional)")).not.toBeInTheDocument();
  });

  it("em canal que exige template, seleciona template sem enviar antes da confirmação", async () => {
    apiMocks.listarTemplatesWhatsApp.mockResolvedValue([
      {
        nome: "boas_vindas",
        idioma: "pt_BR",
        categoria: "UTILIDADE",
        status: "APROVADO",
        corpo: "Olá!",
        quantidadeDeParametros: 0,
      },
    ]);
    const confirmar = vi.fn();
    renderDialog(<DialogoNovoContato aberto onFechar={vi.fn()} onConfirmar={confirmar} />, true);

    expect(await screen.findByRole("button", { name: "Escolher template" })).toBeInTheDocument();
    preencherContato();
    fireEvent.click(screen.getByRole("button", { name: "Escolher template" }));
    const modal = await screen.findByRole("dialog", { name: "Escolher template" });
    fireEvent.click(within(modal).getByRole("button", { name: /boas_vindas/ }));
    fireEvent.click(within(modal).getByRole("button", { name: "Escolher template" }));
    expect(confirmar).not.toHaveBeenCalled();

    fireEvent.click(screen.getByRole("button", { name: "Iniciar atendimento" }));
    expect(confirmar).toHaveBeenCalledWith({
      nome: "Maria",
      telefone: "(83) 99999-8888",
      template: { nome: "boas_vindas", idioma: "pt_BR", parametros: [] },
    });
  });

  it("em canal que exige template permite abrir sem escolher um", async () => {
    const confirmar = vi.fn();
    renderDialog(<DialogoNovoContato aberto onFechar={vi.fn()} onConfirmar={confirmar} />, true);
    await screen.findByText("Nenhum template aprovado.");
    preencherContato();
    fireEvent.click(screen.getByRole("button", { name: "Iniciar atendimento" }));
    expect(confirmar).toHaveBeenCalledWith({
      nome: "Maria",
      telefone: "(83) 99999-8888",
    });
  });

  it("não permite confirmar template com parâmetro vazio", async () => {
    apiMocks.listarTemplatesWhatsApp.mockResolvedValue([
      {
        nome: "orcamento",
        idioma: "pt_BR",
        categoria: "UTILIDADE",
        status: "APROVADO",
        corpo: "Olá {{1}}",
        quantidadeDeParametros: 1,
      },
    ]);
    const confirmar = vi.fn();
    renderDialog(<DialogoNovoContato aberto onFechar={vi.fn()} onConfirmar={confirmar} />, true);
    expect(await screen.findByRole("button", { name: "Escolher template" })).toBeInTheDocument();
    preencherContato();
    fireEvent.click(screen.getByRole("button", { name: "Escolher template" }));
    const modal = await screen.findByRole("dialog", { name: "Escolher template" });
    fireEvent.click(within(modal).getByRole("button", { name: /orcamento/ }));
    const parametro = within(modal).getByLabelText("Mensagem — variável 1");
    fireEvent.change(parametro, { target: { value: "Maria" } });
    fireEvent.click(within(modal).getByRole("button", { name: "Escolher template" }));
    fireEvent.click(screen.getByRole("button", { name: "Escolher template" }));
    const modalAberto = await screen.findByRole("dialog", { name: "Escolher template" });
    fireEvent.change(within(modalAberto).getByLabelText("Mensagem — variável 1"), {
      target: { value: "" },
    });
    fireEvent.click(within(modalAberto).getByRole("button", { name: "Cancelar" }));
    fireEvent.click(screen.getByRole("button", { name: "Iniciar atendimento" }));
    expect(confirmar).not.toHaveBeenCalled();
  });
});

describe("mascararTelefoneBr", () => {
  it("formata 10 e 11 dígitos", () => {
    expect(mascararTelefoneBr("8333334444")).toBe("(83) 3333-4444");
    expect(mascararTelefoneBr("83999998888")).toBe("(83) 99999-8888");
  });
});
