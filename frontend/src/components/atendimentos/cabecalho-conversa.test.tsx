import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import type { CartaoAtendimento, EstadoAtendimentoSelecionado } from "@/lib/atendimento/types";
import { ErroDeApi } from "@/lib/api/errors";

const finalizar = vi.fn();
const participacao = vi.hoisted(() => ({
  usuarioId: "usuario-1",
  papel: "GESTOR",
  participantes: [] as Array<{ usuarioId: string; nome: string; fotoUrl?: string | null }>,
  meuPedido: null as { status: "PENDENTE" | "RECUSADO"; solicitanteNome?: string; solicitadoEm?: string } | null,
  pedidosPendentes: [] as Array<{ id: string; solicitanteNome: string; solicitadoEm: string }>,
  recarregar: vi.fn(),
  invalidar: vi.fn(),
  pedir: vi.fn(),
  entrar: vi.fn(),
  sair: vi.fn(),
  aprovar: vi.fn(),
  recusar: vi.fn(),
}));

vi.mock("@/lib/auth/auth-store", () => ({
  useAuthStore: (seletor: (estado: { accessToken: string | null; papel: string; usuarioId: string }) => unknown) =>
    seletor({ accessToken: null, papel: participacao.papel, usuarioId: participacao.usuarioId }),
}));

vi.mock("@/lib/atendimento/use-participantes", () => ({
  useParticipantes: () => ({ data: participacao.participantes, recarregar: participacao.recarregar }),
}));

vi.mock("@/lib/atendimento/use-participacao", () => ({
  aprovarPedido: participacao.aprovar,
  entrarAtendimento: participacao.entrar,
  invalidarParticipacao: participacao.invalidar,
  pedirEntrada: participacao.pedir,
  recusarPedido: participacao.recusar,
  sairAtendimento: participacao.sair,
  useMeuPedido: () => participacao.meuPedido,
  usePedidosPendentes: () => participacao.pedidosPendentes,
}));

vi.mock("@/lib/atendimento/use-transferir-finalizar", () => ({
  useFinalizarAtendimento: () => ({ mutate: finalizar, isPending: false }),
}));

vi.mock("@/lib/lead/use-painel-lead", () => ({
  useLead: () => ({
    data: { telefone: "(61) 99999-0000", empresa: "Vidraçaria Central" },
  }),
}));

vi.mock("@/lib/config/textos-provider", () => ({
  useTextos: () => ({
    atendimentos: {
      canais: { whatsapp: "WhatsApp" },
      cabecalho: {
        atendidoPor: "Atendido por",
        semAtendente: "Sem atendente",
        transferir: "Transferir",
        maisAcoes: "Mais ações",
        convidar: "Convidar",
        finalizar: "Finalizar",
        buscar: "Buscar na conversa",
        novoAtendimento: "Reativar atendimento",
        participantes: "Participantes",
        participando: "Você está participando",
        pedirEntrada: "Pedir para entrar",
        pedidoPendente: "Pedido pendente",
        entrar: "Entrar no atendimento",
        pedirEntradaDescricao: "O responsável precisa aprovar; o atendimento não será transferido.",
        sair: "Sair do atendimento",
        recusado: "Pedido recusado",
        aprovarEntrada: "Aceitar pedido",
        recusarEntrada: "Recusar pedido",
        pedidoEnviado: "Pedido enviado ao responsável {nome}.",
        pedidoValidadeConfigurada: "A validade segue a configuração da instância.",
        pedidoRecebido: "{nome} pediu para entrar",
        pedidoSolicitadoEm: "Solicitado em {horario}",
        avisoEnviarAssume: "Ao enviar agora, você assume este atendimento.",
        sucessoEntrou: "Você entrou no atendimento.",
        sucessoPedido: "Pedido enviado. O responsável será avisado.",
        sucessoSaiu: "Você saiu do atendimento.",
        sucessoAprovado: "{nome} agora participa do atendimento.",
        sucessoRecusado: "Pedido de {nome} recusado.",
        erroSemPermissao: "Você não tem permissão para entrar diretamente neste atendimento.",
        erroPedidoExpirado: "Esse pedido expirou. Solicite novamente.",
        erroParticipacaoNaoEncontrada: "Sua participação não está mais ativa.",
        erroParticipacao: "Não foi possível atualizar sua participação.",
      },
      finalizar: {
        titulo: "Finalizar atendimento",
        descricao: "",
        confirmar: "Finalizar",
        cancelar: "Cancelar",
        sucesso: "Finalizado",
        erro: "Erro",
        todosMenu: "Mais ações",
        todos: "Finalizar Todos",
        todosTitulo: "Finalizar atendimentos",
        todosDescricao: "Encerrar {quantidade}",
        todosConfirmar: "Finalizar {quantidade}",
        todosCancelar: "Voltar",
        todosResultado: "{finalizados} finalizados; {recusados} recusados",
        todosErro: "Erro",
      },
      avaliacao: {
        titulo: "Avaliação",
        descricao: "Nota 1 a 5",
        registrar: "Avaliar",
        confirmar: "Salvar",
        cancelar: "Agora não",
        jaRegistrada: "Nota {nota}",
        sucesso: "Ok",
        erro: "Erro",
        nota: "Nota {nota}",
      },
      painel: {
        reabrir: "Reabrir detalhes do lead",
      },
    },
    painelLead: { dados: { telefone: "Telefone" }, tags: { titulo: "Tags" } },
  }),
}));

vi.mock("./atalho-tags", () => ({
  AtalhoTags: () => <button type="button">Etiquetar</button>,
  DialogoTagsDoLead: ({ aberto }: { aberto: boolean }) => (aberto ? <div>dialogo-tags</div> : null),
}));

vi.mock("./dialogo-transferir", () => ({
  DialogoTransferir: ({ aberto }: { aberto: boolean }) => (aberto ? <div>dialogo-transferir</div> : null),
}));

vi.mock("./dialogo-convidar", () => ({
  DialogoConvidar: ({ aberto }: { aberto: boolean }) => (aberto ? <div>dialogo-convidar</div> : null),
}));

import { CabecalhoConversa } from "./cabecalho-conversa";

const conversa: CartaoAtendimento = {
  atendimentoId: "atendimento-1",
  leadId: "lead-1",
  leadNome: "Ana Vidros",
  leadFotoUrl: null,
  leadEmpresa: "Empresa antiga",
  canalTipo: "WHATSAPP",
  etapaId: null,
  etapaNome: null,
  etapaCor: null,
  status: "EM_ATENDIMENTO",
  atendenteId: "usuario-1",
  atendenteNome: "Jardel Lima",
  ultimaMensagemPreview: null,
  ultimaMensagemRemetenteTipo: null,
  ultimaMensagemEm: null,
  ultimaMensagemDoLeadEm: null,
  naoLidas: 0,
};

function estado(
  sobrescritas: Partial<EstadoAtendimentoSelecionado> = {},
): EstadoAtendimentoSelecionado {
  const participantes = participacao.participantes.map((item) => ({
    ...item,
    entrouEm: "2026-09-01T10:00:00Z",
  }));
  return {
    cartao: conversa,
    versao: 1,
    participantes,
    usuarioAtualEhResponsavel: false,
    usuarioAtualParticipa: participantes.some((item) => item.usuarioId === participacao.usuarioId),
    podeEnviar: true,
    ...sobrescritas,
  };
}

describe("CabecalhoConversa", () => {
  beforeEach(() => {
    participacao.papel = "GESTOR";
    participacao.participantes = [];
    participacao.meuPedido = null;
    participacao.pedidosPendentes = [];
    participacao.recarregar.mockReset().mockResolvedValue(undefined);
    participacao.invalidar.mockReset();
    participacao.pedir.mockReset().mockResolvedValue(undefined);
    participacao.entrar.mockReset().mockResolvedValue(undefined);
    participacao.sair.mockReset().mockResolvedValue(undefined);
    participacao.aprovar.mockReset().mockResolvedValue(undefined);
    participacao.recusar.mockReset().mockResolvedValue(undefined);
  });

  it("mostra contexto real e oferece ações funcionais do protótipo", () => {
    const alternarBusca = vi.fn();
    render(
      <CabecalhoConversa
        conversa={conversa}
        estado={estado()}
        buscaAberta={false}
        onAlternarBusca={alternarBusca}
        painelDetalhesAberto
        onAlternarPainelDetalhes={vi.fn()}
      />,
    );

    expect(screen.getByText("WhatsApp")).toBeInTheDocument();
    expect(
      screen.getByText(
        /\(61\) 99999-0000 · Vidraçaria Central · Atendido por Jardel Lima/,
      ),
    ).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /Telefone/ })).toHaveAttribute(
      "href",
      "tel:61999990000",
    );

    fireEvent.click(screen.getByRole("button", { name: "Buscar na conversa" }));
    expect(alternarBusca).toHaveBeenCalledOnce();

    fireEvent.click(screen.getByRole("button", { name: "Finalizar" }));
    expect(finalizar).toHaveBeenCalledWith("atendimento-1");
  });

  it("mantém a ação individual e não oferece o menu global de finalização", () => {
    render(
      <CabecalhoConversa
        conversa={conversa}
        estado={estado()}
        buscaAberta={false}
        onAlternarBusca={vi.fn()}
        painelDetalhesAberto
        onAlternarPainelDetalhes={vi.fn()}
      />,
    );

    expect(screen.queryByRole("button", { name: "Mais ações" })).not.toBeInTheDocument();
    expect(screen.queryByText("Finalizar Todos")).not.toBeInTheDocument();
    expect(screen.queryByRole("menuitem")).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Finalizar" })).toBeEnabled();
    expect(screen.getAllByRole("button", { name: "Transferir" }).length).toBeGreaterThan(0);
  });

  it("não oferece pedir entrada nem sair para o responsável atual", () => {
    render(
      <CabecalhoConversa
        conversa={conversa}
        estado={estado({ usuarioAtualEhResponsavel: true })}
        buscaAberta={false}
        onAlternarBusca={vi.fn()}
        painelDetalhesAberto
        onAlternarPainelDetalhes={vi.fn()}
      />,
    );

    expect(screen.queryByRole("button", { name: "Pedir para entrar" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Entrar no atendimento" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Sair do atendimento" })).not.toBeInTheDocument();
    expect(screen.queryByText("Ao enviar agora, você assume este atendimento.")).not.toBeInTheDocument();
  });

  it("oferece reabrir os detalhes somente quando o painel está retraído", () => {
    const onAlternar = vi.fn();
    render(
      <CabecalhoConversa
        conversa={conversa}
        estado={estado()}
        buscaAberta={false}
        onAlternarBusca={vi.fn()}
        painelDetalhesAberto={false}
        onAlternarPainelDetalhes={onAlternar}
      />,
    );

    const controle = screen.getByRole("button", { name: "Reabrir detalhes do lead" });
    expect(controle).toHaveAttribute("aria-expanded", "false");
    expect(controle).toHaveAttribute("aria-controls", "painel-detalhes-lead");
    fireEvent.click(controle);
    expect(onAlternar).toHaveBeenCalledOnce();
  });

  it("abre um atendimento novo a partir do finalizado sem alterar o significado do estado", () => {
    const abrirNovo = vi.fn();
    render(
      <CabecalhoConversa
        conversa={{ ...conversa, status: "FINALIZADO" }}
        estado={estado({
          cartao: { ...conversa, status: "FINALIZADO" },
          podeEnviar: false,
        })}
        buscaAberta={false}
        onAlternarBusca={vi.fn()}
        painelDetalhesAberto
        onAlternarPainelDetalhes={vi.fn()}
        onAbrirNovoAtendimento={abrirNovo}
      />,
    );

    expect(screen.queryByRole("button", { name: "Finalizar" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Avaliar" })).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Reativar atendimento" }));
    expect(abrirNovo).toHaveBeenCalledOnce();
  });

  it("mostra erro legível ao falhar a entrada e mantém o estado fora", async () => {
    participacao.entrar.mockRejectedValueOnce(new Error("sem alçada para entrar diretamente"));
    render(
      <CabecalhoConversa
        conversa={conversa}
        estado={estado()}
        buscaAberta={false}
        onAlternarBusca={vi.fn()}
        painelDetalhesAberto
        onAlternarPainelDetalhes={vi.fn()}
      />,
    );

    fireEvent.click(screen.getByRole("button", { name: "Entrar no atendimento" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("Você não tem permissão para entrar diretamente neste atendimento.");
    expect(screen.queryByRole("button", { name: "Sair do atendimento" })).not.toBeInTheDocument();
  });

  it("confirma a entrada e oferece sair do atendimento", async () => {
    render(
      <CabecalhoConversa
        conversa={conversa}
        estado={estado()}
        buscaAberta={false}
        onAlternarBusca={vi.fn()}
        painelDetalhesAberto
        onAlternarPainelDetalhes={vi.fn()}
      />,
    );

    fireEvent.click(screen.getByRole("button", { name: "Entrar no atendimento" }));

    expect(await screen.findByRole("status")).toHaveTextContent("Você entrou no atendimento.");
    expect(await screen.findByRole("button", { name: "Sair do atendimento" })).toBeInTheDocument();
  });

  it.each([403, 404])("mantém o sucesso ao sair mesmo quando a reconciliação perde acesso (%s)", async (status) => {
    participacao.participantes = [{ usuarioId: "usuario-1", nome: "Jardel Lima" }];
    const reconciliar = vi.fn().mockRejectedValue(new ErroDeApi(status, null, "Atendimento indisponível"));
    render(
      <CabecalhoConversa
        conversa={conversa}
        estado={estado({ usuarioAtualParticipa: true })}
        onReconciliarEstado={reconciliar}
        buscaAberta={false}
        onAlternarBusca={vi.fn()}
        painelDetalhesAberto
        onAlternarPainelDetalhes={vi.fn()}
      />,
    );

    fireEvent.click(screen.getByRole("button", { name: "Sair do atendimento" }));

    expect(await screen.findByRole("status")).toHaveTextContent("Você saiu do atendimento.");
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
    expect(participacao.sair).toHaveBeenCalledWith("atendimento-1");
    expect(reconciliar).toHaveBeenCalledOnce();
  });

  it("mantém a reconciliação de entrada quando o snapshot retorna com sucesso", async () => {
    const reconciliar = vi.fn().mockResolvedValue(undefined);
    render(
      <CabecalhoConversa
        conversa={conversa}
        estado={estado()}
        onReconciliarEstado={reconciliar}
        buscaAberta={false}
        onAlternarBusca={vi.fn()}
        painelDetalhesAberto
        onAlternarPainelDetalhes={vi.fn()}
      />,
    );

    fireEvent.click(screen.getByRole("button", { name: "Entrar no atendimento" }));

    expect(await screen.findByRole("status")).toHaveTextContent("Você entrou no atendimento.");
    expect(reconciliar).toHaveBeenCalledOnce();
  });

  it("distingue participantes do responsável", () => {
    participacao.participantes = [{ usuarioId: "usuario-2", nome: "Ana Beatriz" }];
    render(
      <CabecalhoConversa
        conversa={conversa}
        estado={estado()}
        buscaAberta={false}
        onAlternarBusca={vi.fn()}
        painelDetalhesAberto
        onAlternarPainelDetalhes={vi.fn()}
      />,
    );

    expect(screen.getByText(/Atendido por Jardel Lima/)).toBeInTheDocument();
    expect(screen.getByLabelText("Participantes")).toBeInTheDocument();
    expect(screen.getByText("Ana Beatriz")).toBeInTheDocument();
  });

  it("mostra o nome de cada solicitante ao responsável", () => {
    participacao.pedidosPendentes = [
      { id: "pedido-1", solicitanteNome: "Ana Beatriz", solicitadoEm: "2026-09-01T10:00:00Z" },
      { id: "pedido-2", solicitanteNome: "Carlos Silva", solicitadoEm: "2026-09-01T10:01:00Z" },
    ];
    render(
      <CabecalhoConversa
        conversa={conversa}
        estado={estado({ usuarioAtualEhResponsavel: true })}
        buscaAberta={false}
        onAlternarBusca={vi.fn()}
        painelDetalhesAberto
        onAlternarPainelDetalhes={vi.fn()}
      />,
    );

    expect(screen.getByText("Ana Beatriz")).toBeInTheDocument();
    expect(screen.getByText("Carlos Silva")).toBeInTheDocument();
    expect(screen.getAllByRole("button", { name: "Aceitar pedido" })).toHaveLength(2);
  });

  it("avisa que o envio assume o atendimento somente fora da participação", () => {
    const { rerender } = render(
      <CabecalhoConversa
        conversa={conversa}
        estado={estado()}
        buscaAberta={false}
        onAlternarBusca={vi.fn()}
        painelDetalhesAberto
        onAlternarPainelDetalhes={vi.fn()}
      />,
    );

    expect(screen.getByText("Ao enviar agora, você assume este atendimento.")).toBeInTheDocument();
    participacao.participantes = [{ usuarioId: "usuario-1", nome: "Jardel Lima" }];
    rerender(
      <CabecalhoConversa
        conversa={conversa}
        estado={estado()}
        buscaAberta={false}
        onAlternarBusca={vi.fn()}
        painelDetalhesAberto
        onAlternarPainelDetalhes={vi.fn()}
      />,
    );
    expect(screen.queryByText("Ao enviar agora, você assume este atendimento.")).not.toBeInTheDocument();
  });

  it("mantém o cabeçalho legível em viewport móvel", () => {
    render(
      <CabecalhoConversa
        conversa={{ ...conversa, leadNome: "Cliente com um nome bastante comprido para testar o cabeçalho" }}
        estado={estado()}
        buscaAberta={false}
        onAlternarBusca={vi.fn()}
        painelDetalhesAberto
        onAlternarPainelDetalhes={vi.fn()}
      />,
    );

    expect(document.querySelector('[data-slot="cabecalho-conversa"]')).toHaveClass("flex-wrap", "min-h-[72px]");
  });
});

describe("CabecalhoConversa — transbordo para o ⋯ (E210)", () => {
  const larguras: Record<string, number> = {
    participacao: 180, convidar: 100, transferir: 110, finalizar: 100, "novo-atendimento": 160,
    separador: 10, buscar: 40, tags: 40, telefone: 40, "reabrir-painel": 40, __menu: 40,
  };
  let larguraDoCabecalho = 1000;
  const observadores = new Set<ResizeObserverCallback>();

  beforeEach(() => {
    participacao.papel = "GESTOR";
    participacao.participantes = [];
    participacao.meuPedido = null;
    participacao.pedidosPendentes = [];
    larguraDoCabecalho = 1000;
    vi.stubGlobal("ResizeObserver", class {
      constructor(private readonly callback: ResizeObserverCallback) {}
      observe() { observadores.add(this.callback); }
      unobserve() {}
      disconnect() { observadores.delete(this.callback); }
    });
    vi.spyOn(HTMLElement.prototype, "getBoundingClientRect").mockImplementation(function (this: HTMLElement) {
      const largura = this.dataset.slot === "cabecalho-conversa"
        ? larguraDoCabecalho
        : (larguras[this.dataset.medida ?? ""] ?? 0);
      return { width: largura, height: 32, x: 0, y: 0, top: 0, left: 0, right: largura, bottom: 32, toJSON: () => ({}) };
    });
  });

  afterEach(() => {
    observadores.clear();
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
  });

  function redimensionar(largura: number) {
    larguraDoCabecalho = largura;
    act(() => observadores.forEach((callback) => callback([], {} as ResizeObserver)));
  }

  function renderizar(sobrescritas: Partial<Parameters<typeof CabecalhoConversa>[0]> = {}) {
    const props = {
      conversa,
      estado: estado(),
      buscaAberta: false,
      onAlternarBusca: vi.fn(),
      painelDetalhesAberto: false,
      onAlternarPainelDetalhes: vi.fn(),
      ...sobrescritas,
    };
    render(<CabecalhoConversa {...props} />);
    return props;
  }

  function abrirMenu() {
    fireEvent.click(screen.getByRole("button", { name: "Mais ações" }));
  }

  it("desktop grande: todas as ações na barra e nenhum ⋯", () => {
    renderizar();

    for (const nome of ["Convidar", "Transferir", "Finalizar", "Buscar na conversa", "Reabrir detalhes do lead", "Entrar no atendimento"]) {
      expect(screen.getByRole("button", { name: new RegExp(nome) })).toBeInTheDocument();
    }
    expect(screen.getByRole("link", { name: /Telefone/ })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Mais ações" })).not.toBeInTheDocument();
  });

  it("desktop pequeno: telefone e convidar saem primeiro; transferir e finalizar ficam", async () => {
    renderizar();
    redimensionar(800);

    expect(screen.queryByRole("link", { name: /Telefone/ })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /Convidar/ })).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: /Transferir/ })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /Finalizar/ })).toBeInTheDocument();

    abrirMenu();
    const itens = await screen.findAllByRole("menuitem");
    expect(itens.map((item) => item.textContent)).toEqual(["Convidar", "Telefone: (61) 99999-0000"]);
  });

  it("muito estreito: só participação e Finalizar ficam; o ⋯ traz o resto na ordem original", async () => {
    renderizar();
    redimensionar(600);

    expect(screen.getByRole("button", { name: "Entrar no atendimento" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /Finalizar/ })).toBeInTheDocument();
    for (const nome of ["Convidar", "Transferir", "Buscar na conversa", "Reabrir detalhes do lead"]) {
      expect(screen.queryByRole("button", { name: new RegExp(nome) })).not.toBeInTheDocument();
    }

    abrirMenu();
    await screen.findByRole("menu");
    expect(screen.getAllByRole("menuitem").map((item) => item.textContent)).toEqual([
      "Convidar", "Transferir", "Tags", "Telefone: (61) 99999-0000", "Reabrir detalhes do lead",
    ]);
    expect(screen.getByRole("menuitemcheckbox", { name: "Buscar na conversa" })).toBeInTheDocument();
  });

  it("ação pelo menu abre o mesmo diálogo e o menu fecha", async () => {
    renderizar();
    redimensionar(600);

    abrirMenu();
    fireEvent.click(await screen.findByRole("menuitem", { name: "Transferir" }));

    expect(screen.getByText("dialogo-transferir")).toBeInTheDocument();
    await waitFor(() => expect(screen.queryByRole("menu")).not.toBeInTheDocument());
  });

  it("tags pelo menu abrem o seletor em diálogo", async () => {
    renderizar();
    redimensionar(600);

    abrirMenu();
    fireEvent.click(await screen.findByRole("menuitem", { name: "Tags" }));

    expect(screen.getByText("dialogo-tags")).toBeInTheDocument();
  });

  it("buscar no menu reflete o mesmo estado ligado e alterna pelo mesmo handler", async () => {
    const props = renderizar({ buscaAberta: true });
    expect(screen.getByRole("button", { name: "Buscar na conversa" })).toHaveAttribute("aria-pressed", "true");

    redimensionar(600);
    abrirMenu();
    const item = await screen.findByRole("menuitemcheckbox", { name: "Buscar na conversa" });
    expect(item).toHaveAttribute("aria-checked", "true");

    fireEvent.click(item);
    expect(props.onAlternarBusca).toHaveBeenCalledTimes(1);
  });

  it("telefone no menu continua sendo o mesmo link de discagem", async () => {
    renderizar();
    redimensionar(600);

    abrirMenu();
    const item = await screen.findByRole("menuitem", { name: "Telefone: (61) 99999-0000" });
    expect(item).toHaveAttribute("href", "tel:61999990000");
  });

  it("volta a mostrar as ações quando o painel lateral fecha e sobra espaço", () => {
    renderizar();
    redimensionar(600);
    expect(screen.queryByRole("button", { name: /Transferir/ })).not.toBeInTheDocument();

    redimensionar(1200);
    expect(screen.getByRole("button", { name: /Transferir/ })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Mais ações" })).not.toBeInTheDocument();
  });

  it("atendente fora do atendimento: pedir entrada nunca vai para o menu e convidar não existe", async () => {
    participacao.papel = "ATENDENTE";
    renderizar();
    redimensionar(500);

    expect(screen.getByRole("button", { name: "Pedir para entrar" })).toBeInTheDocument();
    abrirMenu();
    await screen.findByRole("menu");
    expect(screen.queryByRole("menuitem", { name: "Convidar" })).not.toBeInTheDocument();
  });

  it("finalizado: Novo atendimento é fixo e transferir/finalizar não aparecem em lugar nenhum", async () => {
    renderizar({
      conversa: { ...conversa, status: "FINALIZADO" },
      onAbrirNovoAtendimento: vi.fn(),
    });
    redimensionar(500);

    expect(screen.getByRole("button", { name: /Reativar atendimento/ })).toBeInTheDocument();
    abrirMenu();
    await screen.findByRole("menu");
    expect(screen.queryByRole("menuitem", { name: "Transferir" })).not.toBeInTheDocument();
    expect(screen.queryByRole("menuitem", { name: /Finalizar/ })).not.toBeInTheDocument();
  });

  it("painel aberto: reabrir detalhes não existe nem na barra nem no menu", async () => {
    renderizar({ painelDetalhesAberto: true });
    redimensionar(500);

    expect(screen.queryByRole("button", { name: "Reabrir detalhes do lead" })).not.toBeInTheDocument();
    abrirMenu();
    await screen.findByRole("menu");
    expect(screen.queryByRole("menuitem", { name: "Reabrir detalhes do lead" })).not.toBeInTheDocument();
  });

  it("nome longo fica truncado com o nome completo no título", () => {
    const nome = "Cliente com um nome bastante comprido para testar o cabeçalho responsivo";
    renderizar({ conversa: { ...conversa, leadNome: nome } });

    const titulo = screen.getByText(nome, { selector: "p" });
    expect(titulo).toHaveClass("truncate");
    expect(titulo).toHaveAttribute("title", nome);
  });
});
