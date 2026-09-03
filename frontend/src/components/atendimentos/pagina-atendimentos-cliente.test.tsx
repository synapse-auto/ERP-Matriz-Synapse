import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { useEffect, useRef } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import type {
  AtendimentoResumo,
  CartaoAtendimento,
  ItemInbox,
  NotificacaoTempoReal,
} from "@/lib/atendimento/types";
import { useEnviarMensagem } from "@/lib/atendimento/use-enviar-mensagem";

const callbacks = vi.hoisted(() => ({
  abrir: undefined as ((cartao: ItemInbox) => void) | undefined,
  atualizarLista: undefined as ((cartoes: ItemInbox[]) => void) | undefined,
  alterarVisao: undefined as ((visao: string) => void) | undefined,
  visaoAtual: undefined as string | undefined,
  finalizar: undefined as ((resumo: AtendimentoResumo) => void) | undefined,
  mensagens: undefined as { historico: string | null; assinatura: string | null } | undefined,
}));
const abrirExistente = vi.hoisted(() => vi.fn());

interface ClienteStompFalso {
  connected: boolean;
  onConnect?: () => void;
  onWebSocketClose?: () => void;
  onStompError?: () => void;
  assinaturas: Map<string, (mensagem: { body: string }) => void>;
}

const stomp = vi.hoisted(() => ({ clientes: [] as ClienteStompFalso[] }));

vi.mock("@stomp/stompjs", () => ({
  Client: class implements ClienteStompFalso {
    connected = false;
    onConnect?: () => void;
    onWebSocketClose?: () => void;
    onStompError?: () => void;
    assinaturas = new Map<string, (mensagem: { body: string }) => void>();

    constructor() {
      stomp.clientes.push(this);
    }

    activate() {
      this.connected = true;
      this.onConnect?.();
    }

    deactivate() {
      this.connected = false;
    }

    subscribe(destino: string, callback: (mensagem: { body: string }) => void) {
      this.assinaturas.set(destino, callback);
      return { id: destino, unsubscribe: vi.fn(() => this.assinaturas.delete(destino)) };
    }
  },
}));

const cartaoInicial: CartaoAtendimento = {
  atendimentoId: "atendimento-1",
  leadId: "lead-1",
  leadNome: "Lead de teste",
  leadFotoUrl: null,
  leadEmpresa: null,
  canalTipo: "WHATSAPP",
  etapaId: null,
  etapaNome: null,
  etapaCor: null,
  status: "EM_ATENDIMENTO",
  atendenteId: "ana-id",
  atendenteNome: "Ana Atendente",
  ultimaMensagemPreview: "Olá",
  ultimaMensagemRemetenteTipo: "LEAD",
  ultimaMensagemEm: "2026-01-01T00:00:00Z",
  ultimaMensagemDoLeadEm: "2026-01-01T00:00:00Z",
  naoLidas: 0,
};

vi.mock("./lista-conversas", () => ({
  ListaConversas: ({
    leadInicialGatilho = 0,
    onAbrirAtendimento,
    onAtendimentosAtualizados,
    onVisaoAlterada,
    visaoAtual,
  }: {
    leadInicialGatilho?: number;
    onAbrirAtendimento: (cartao: ItemInbox) => void;
    onAtendimentosAtualizados?: (cartoes: ItemInbox[]) => void;
    onVisaoAlterada?: (visao: string) => void;
    visaoAtual?: string;
  }) => {
    const gatilhoAnterior = useRef(leadInicialGatilho);
    callbacks.abrir = onAbrirAtendimento;
    callbacks.atualizarLista = onAtendimentosAtualizados;
    callbacks.alterarVisao = onVisaoAlterada;
    callbacks.visaoAtual = visaoAtual;
    useEffect(() => {
      if (leadInicialGatilho === gatilhoAnterior.current) return;
      gatilhoAnterior.current = leadInicialGatilho;
      onAbrirAtendimento(cartaoInicial);
    }, [leadInicialGatilho, onAbrirAtendimento]);
    return <button type="button" onClick={() => onAbrirAtendimento(cartaoInicial)}>Abrir lista</button>;
  },
}));
vi.mock("./cabecalho-conversa", () => ({
  CabecalhoConversa: ({
    conversa,
    painelDetalhesAberto,
    onAlternarPainelDetalhes,
    onAbrirNovoAtendimento,
    onAtendimentoFinalizado,
  }: {
    conversa: CartaoAtendimento;
    painelDetalhesAberto: boolean;
    onAlternarPainelDetalhes: () => void;
    onAbrirNovoAtendimento?: () => void;
    onAtendimentoFinalizado?: (resumo: AtendimentoResumo) => void;
  }) => {
    callbacks.finalizar = onAtendimentoFinalizado;
    return (
      <div data-testid="responsavel-cabecalho">
      {conversa.atendenteNome}
      {conversa.status !== "FINALIZADO" && onAtendimentoFinalizado && (
        <button
          type="button"
          onClick={() => onAtendimentoFinalizado({ id: conversa.atendimentoId, status: "FINALIZADO", atendenteId: conversa.atendenteId })}
        >
          Simular finalização
        </button>
      )}
      {conversa.status === "FINALIZADO" && onAbrirNovoAtendimento && (
        <button type="button" onClick={onAbrirNovoAtendimento}>Reativar atendimento</button>
      )}
      {!painelDetalhesAberto && (
        <button type="button" onClick={onAlternarPainelDetalhes}>
          Reabrir detalhes do lead
        </button>
      )}
      </div>
    );
  },
}));
vi.mock("./painel-da-conversa", () => ({
  PainelDaConversa: ({
    responsavelNome,
    onRetrair,
  }: {
    responsavelNome: string | null;
    onRetrair: () => void;
  }) => (
    <div data-testid="responsavel-painel">
      {responsavelNome}
      <button type="button" onClick={onRetrair}>Retrair detalhes do lead</button>
    </div>
  ),
}));
vi.mock("@/components/chat-interno/painel-conversa-interna", () => ({
  PainelConversaInterna: () => <div data-testid="conversa-interna" />,
}));
vi.mock("./lista-mensagens", () => ({ ListaMensagens: () => <div data-testid="historico" /> }));
function ComposerDeTeste({
  conversa,
  onMensagemEnviada,
}: {
  conversa: CartaoAtendimento;
  onMensagemEnviada?: () => void;
}) {
  const enviar = useEnviarMensagem(onMensagemEnviada);
  return (
    <>
      <div data-testid="composer" />
      {onMensagemEnviada && (
        <button type="button" onClick={onMensagemEnviada}>
          Simular envio
        </button>
      )}
      <button
        type="button"
        onClick={() =>
          enviar.mutate({
            atendimentoId: conversa.atendimentoId,
            leadId: conversa.leadId,
            conteudo: "mensagem que falha",
          })
        }
      >
        Simular falha real
      </button>
      {enviar.isError && <span data-testid="erro-envio-real">Falha de envio</span>}
    </>
  );
}

vi.mock("./composer", () => ({
  Composer: ({
    conversa,
    onMensagemEnviada,
  }: {
    conversa: CartaoAtendimento;
    onMensagemEnviada?: () => void;
  }) => (
    <ComposerDeTeste conversa={conversa} onMensagemEnviada={onMensagemEnviada} />
  ),
}));
vi.mock("@/lib/atendimento/api", () => ({
  marcarAtendimentoComoLido: vi.fn(() => Promise.resolve()),
  iniciarNovoContato: vi.fn(),
  abrirAtendimentoParaLead: abrirExistente,
  enviarMensagem: vi.fn(() => Promise.reject(new Error("falha de rede"))),
  enviarTemplate: vi.fn(),
}));
vi.mock("@/lib/atendimento/use-configuracao-composer", () => ({
  useConfiguracaoComposer: () => ({ data: { tempoNotificacaoSegundos: 8 } }),
}));
vi.mock("@/lib/atendimento/use-mensagens", () => ({
  useMensagens: (...args: unknown[]) => {
    callbacks.mensagens = {
      historico: (args[0] as string | null) ?? null,
      assinatura: (args[4] as string | null) ?? null,
    };
    return { data: [], isLoading: false, hasNextPage: false, isFetchingNextPage: false, fetchNextPage: vi.fn() };
  },
}));
vi.mock("@/lib/auth/auth-store", () => ({
  useAuthStore: { getState: () => ({ accessToken: "token" }) },
}));
const telaEstreita = vi.hoisted(() => ({ atual: false }));

vi.mock("@/lib/navegacao/tela-estreita", () => ({
  useTelaEstreita: () => telaEstreita.atual,
}));

vi.mock("@/lib/config/textos-provider", () => ({
  useTextos: () => ({
    estados: { vazio: "Nenhuma conversa" },
    atendimentos: {
      cabecalho: { voltar: "Voltar para a lista" },
      composer: { anexoSoltar: "Solte os arquivos aqui" },
      finalizar: { sucesso: "Atendimento finalizado." },
      novoContato: {
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
        primeiraMensagemPlaceholder: "Digite a mensagem.",
        avisoTemplate: "Use um template.",
        cancelar: "Cancelar",
        confirmar: "Iniciar atendimento",
        erro: "Não foi possível iniciar o atendimento.",
      },
      tempoReal: {
        transferenciaRecebida: "Transferência recebida",
        transferenciaRecebidaDescricao: "{nome}",
        abrirTransferencia: "Abrir transferência",
        atendimentoDevolvidoParaIa: "Devolvido para IA",
        atendimentoDevolvidoParaIaDescricao: "{nome}",
        reconectando: "Reconectando",
        conversaEncerrada: "Conversa encerrada",
        fechar: "Fechar aviso",
      },
    },
  }),
}));

import { PaginaAtendimentosCliente } from "./pagina-atendimentos-cliente";

function renderPagina() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const resultado = render(
    <QueryClientProvider client={queryClient}>
      <PaginaAtendimentosCliente leadInicialId={null} visaoInicial="TODOS" />
    </QueryClientProvider>,
  );
  return { ...resultado, queryClient };
}

function emitirNotificacao(notificacao: NotificacaoTempoReal) {
  const cliente = stomp.clientes.at(-1);
  const assinatura = cliente?.assinaturas.get("/user/queue/notificacoes");
  if (!assinatura) throw new Error("A fila pessoal de notificações não foi assinada");
  assinatura({ body: JSON.stringify(notificacao) });
}

describe("PaginaAtendimentosCliente", () => {
  beforeEach(() => {
    callbacks.abrir = undefined;
    callbacks.atualizarLista = undefined;
    callbacks.alterarVisao = undefined;
    callbacks.visaoAtual = undefined;
    callbacks.finalizar = undefined;
    callbacks.mensagens = undefined;
    stomp.clientes.length = 0;
    telaEstreita.atual = false;
    abrirExistente.mockReset();
    abrirExistente.mockResolvedValue({
      leadId: "lead-1",
      atendimentoId: "atendimento-novo",
      mensagemId: null,
      leadCriado: false,
    });
  });

  it("deriva cabeçalho e painel da lista atualizada após transferência, sem reabrir a conversa", () => {
    renderPagina();
    act(() => callbacks.atualizarLista?.([cartaoInicial]));
    act(() => callbacks.abrir?.(cartaoInicial));

    expect(screen.getByTestId("responsavel-cabecalho")).toHaveTextContent("Ana Atendente");
    act(() => callbacks.atualizarLista?.([{ ...cartaoInicial, atendenteId: "bruno-id", atendenteNome: "Bruno Atendente" }]));

    expect(screen.getByTestId("responsavel-cabecalho")).toHaveTextContent("Bruno Atendente");
    expect(screen.getByTestId("responsavel-painel")).toHaveTextContent("Bruno Atendente");
  });

  it("encerra o composer quando a finalização bem-sucedida remove o cartão da lista", () => {
    renderPagina();
    act(() => callbacks.atualizarLista?.([cartaoInicial]));
    act(() => callbacks.abrir?.(cartaoInicial));

    expect(screen.getByTestId("composer")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Simular finalização" }));
    act(() => callbacks.atualizarLista?.([]));

    expect(screen.queryByTestId("composer")).not.toBeInTheDocument();
    expect(screen.getByText("Atendimento finalizado.")).toBeInTheDocument();
    expect(screen.getByTestId("responsavel-cabecalho")).toBeInTheDocument();
    expect(screen.getByTestId("historico")).toBeInTheDocument();
  });

  it("mantém a conversa aberta quando o atendimento muda de visão após o envio", () => {
    renderPagina();
    act(() => callbacks.atualizarLista?.([cartaoInicial]));
    act(() => callbacks.abrir?.(cartaoInicial));
    expect(screen.getByTestId("responsavel-cabecalho")).toBeInTheDocument();

    act(() => callbacks.atualizarLista?.([]));

    expect(screen.getByTestId("responsavel-cabecalho")).toBeInTheDocument();
    expect(screen.getByTestId("responsavel-painel")).toBeInTheDocument();
    expect(screen.getByTestId("composer")).toBeInTheDocument();
  });

  it("preserva a conversa selecionada enquanto a lista é refiltrada depois da primeira resposta", () => {
    render(
      <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
        <PaginaAtendimentosCliente leadInicialId={null} visaoInicial="PENDENTES" />
      </QueryClientProvider>,
    );
    act(() => callbacks.atualizarLista?.([cartaoInicial]));
    act(() => callbacks.abrir?.(cartaoInicial));
    act(() => callbacks.alterarVisao?.("PENDENTES"));

    fireEvent.click(screen.getByRole("button", { name: "Simular envio" }));
    act(() => callbacks.atualizarLista?.([]));

    expect(callbacks.visaoAtual).toBe("ATIVOS");
    expect(screen.getByTestId("responsavel-cabecalho")).toBeInTheDocument();
    expect(screen.getByTestId("composer")).toBeInTheDocument();
  });

  it("mantém conversa e visão Pendentes quando o envio real falha", async () => {
    render(
      <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
        <PaginaAtendimentosCliente leadInicialId={null} visaoInicial="PENDENTES" />
      </QueryClientProvider>,
    );
    act(() => callbacks.atualizarLista?.([cartaoInicial]));
    act(() => callbacks.abrir?.(cartaoInicial));
    act(() => callbacks.alterarVisao?.("PENDENTES"));

    fireEvent.click(screen.getByRole("button", { name: "Simular falha real" }));
    await waitFor(() => expect(screen.getByTestId("erro-envio-real")).toBeInTheDocument());

    expect(callbacks.visaoAtual).toBe("PENDENTES");
    expect(screen.getByTestId("responsavel-cabecalho")).toBeInTheDocument();
    expect(screen.getByTestId("composer")).toBeInTheDocument();
  });

  it("retrai e reabre os detalhes sem perder a conversa, o histórico ou o composer", () => {
    const pagina = renderPagina();
    act(() => callbacks.atualizarLista?.([cartaoInicial]));
    act(() => callbacks.abrir?.(cartaoInicial));

    expect(pagina.container.firstElementChild).toHaveClass(
      "grid-cols-[346px_minmax(0,1fr)_344px]",
      "grid-rows-[minmax(0,1fr)]",
      "min-h-0",
      "overflow-hidden",
    );
    expect(pagina.container.firstElementChild?.children[1]).toHaveClass(
      "flex",
      "h-full",
      "min-h-0",
      "overflow-hidden",
    );
    expect(screen.getByTestId("composer")).toBeInTheDocument();
    expect(screen.getByTestId("responsavel-painel")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Retrair detalhes do lead" }));

    expect(pagina.container.firstElementChild).toHaveClass(
      "grid-cols-[346px_minmax(0,1fr)]",
    );
    expect(screen.queryByTestId("responsavel-painel")).not.toBeInTheDocument();
    expect(screen.getByTestId("responsavel-cabecalho")).toHaveTextContent("Ana Atendente");
    expect(screen.getByTestId("composer")).toBeInTheDocument();
    expect(callbacks.mensagens).toEqual({
      historico: "atendimento-1",
      assinatura: "atendimento-1",
    });

    const outroLead = {
      ...cartaoInicial,
      atendimentoId: "atendimento-2",
      leadId: "lead-2",
      leadNome: "Outro lead",
    };
    act(() => callbacks.atualizarLista?.([cartaoInicial, outroLead]));
    act(() => callbacks.abrir?.(outroLead));
    expect(screen.queryByTestId("responsavel-painel")).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "Reabrir detalhes do lead" }));
    expect(screen.getByTestId("responsavel-painel")).toBeInTheDocument();
    expect(callbacks.mensagens).toEqual({
      historico: "atendimento-2",
      assinatura: "atendimento-2",
    });
  });

  it("não renderiza painel nem controle de lead em conversa interna", () => {
    const conversaInterna: ItemInbox = {
      tipo: "EQUIPE_INTERNA",
      atendimentoId: null,
      conversaId: "conversa-interna-1",
      nome: "Equipe",
      avatarUrl: null,
      identificadorVisual: "EQ",
      ultimaMensagemPreview: null,
      ultimaMensagemEm: null,
      naoLidas: 0,
      participantes: "Ana e Bruno",
      tipoConversa: "DIRETA",
    };
    renderPagina();
    act(() => callbacks.atualizarLista?.([conversaInterna]));
    act(() => callbacks.abrir?.(conversaInterna));

    expect(screen.getByTestId("conversa-interna")).toBeInTheDocument();
    expect(screen.queryByTestId("responsavel-painel")).not.toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: /detalhes do lead/i }),
    ).not.toBeInTheDocument();
  });

  it("abre a transferência e limpa o aviso", async () => {
    renderPagina();
    act(() => callbacks.atualizarLista?.([cartaoInicial]));
    const notificacao: NotificacaoTempoReal = {
      tipo: "TRANSFERENCIA_RECEBIDA",
      dados: {
        atendimentoId: "atendimento-1",
        leadId: "lead-1",
        leadNome: "Lead de teste",
        quemTransferiu: "Gestor",
        atorTipo: "USUARIO",
        ocorridoEm: "2026-01-01T00:00:00Z",
      },
    };
    act(() => emitirNotificacao(notificacao));
    fireEvent.click(screen.getByRole("button", { name: "Abrir transferência" }));
    await waitFor(() =>
      expect(screen.getByTestId("responsavel-cabecalho")).toHaveTextContent("Ana Atendente"),
    );

    expect(screen.queryByRole("status")).not.toBeInTheDocument();
  });

  it("mantém o mesmo evento dispensado após invalidação, render e reconexão, mas exibe uma nova ocorrência", async () => {
    vi.useFakeTimers();
    const random = vi.spyOn(Math, "random").mockReturnValue(1);
    try {
      const pagina = renderPagina();
      const notificacao: NotificacaoTempoReal = {
        tipo: "TRANSFERENCIA_RECEBIDA",
        dados: {
          atendimentoId: "atendimento-1",
          leadId: "lead-1",
          leadNome: "Lead de teste",
          quemTransferiu: "Gestor",
          atorTipo: "USUARIO",
          ocorridoEm: "2026-01-01T00:00:00Z",
        },
      };
      act(() => emitirNotificacao(notificacao));
      expect(screen.getByRole("status")).toHaveTextContent("Transferência recebida");

      fireEvent.click(screen.getByRole("button", { name: "Fechar aviso" }));
      expect(screen.queryByRole("status")).not.toBeInTheDocument();

      await pagina.queryClient.invalidateQueries({ queryKey: ["atendimentos"] });
      pagina.rerender(
        <QueryClientProvider client={pagina.queryClient}>
          <PaginaAtendimentosCliente leadInicialId={null} visaoInicial="TODOS" />
        </QueryClientProvider>,
      );
      act(() => {
        stomp.clientes[0]?.onWebSocketClose?.();
        vi.advanceTimersByTime(1_000);
      });
      expect(stomp.clientes).toHaveLength(2);

      act(() => emitirNotificacao(notificacao));
      expect(screen.queryByRole("status")).not.toBeInTheDocument();

      act(() =>
        emitirNotificacao({
          ...notificacao,
          dados: { ...notificacao.dados, ocorridoEm: "2026-01-01T00:01:00Z" },
        }),
      );
      expect(screen.getByRole("status")).toHaveTextContent("Transferência recebida");
    } finally {
      random.mockRestore();
      vi.useRealTimers();
    }
  });

  it("dispensa a devolução para IA e respeita a expiração configurada", () => {
    vi.useFakeTimers();
    try {
      renderPagina();
      const notificacao: NotificacaoTempoReal = {
        tipo: "ATENDIMENTO_DEVOLVIDO_PARA_IA",
        dados: {
          atendimentoId: "atendimento-1",
          leadId: "lead-1",
          leadNome: "Lead de teste",
          ocorridoEm: "2026-01-01T00:00:00Z",
        },
      };
      act(() => emitirNotificacao(notificacao));
      expect(screen.getByRole("status")).toHaveTextContent("Devolvido para IA");

      act(() => vi.advanceTimersByTime(8_000));
      expect(screen.queryByRole("status")).not.toBeInTheDocument();

      act(() => emitirNotificacao(notificacao));
      expect(screen.queryByRole("status")).not.toBeInTheDocument();
    } finally {
      vi.useRealTimers();
    }
  });

  it("não ressuscita aviso antigo depois de desmontar e montar a tela", () => {
    const primeira = renderPagina();
    act(() =>
      emitirNotificacao({
        tipo: "ATENDIMENTO_DEVOLVIDO_PARA_IA",
        dados: {
          atendimentoId: "atendimento-1",
          leadId: "lead-1",
          leadNome: "Lead de teste",
          ocorridoEm: "2026-01-01T00:00:00Z",
        },
      }),
    );
    expect(screen.getByRole("status")).toBeInTheDocument();
    primeira.unmount();

    renderPagina();
    expect(screen.queryByRole("status")).not.toBeInTheDocument();
  });

  it("assina somente o atendimento ativo e deixa o historico do lead navegavel", () => {
    const finalizadoComNovoAtivo: CartaoAtendimento = {
      ...cartaoInicial,
      atendimentoId: "atendimento-finalizado",
      atendimentoAtivoId: "atendimento-ativo",
      status: "FINALIZADO",
    };
    renderPagina();
    act(() => callbacks.atualizarLista?.([finalizadoComNovoAtivo]));
    act(() => callbacks.abrir?.(finalizadoComNovoAtivo));

    expect(callbacks.mensagens).toEqual({
      historico: "atendimento-finalizado",
      assinatura: "atendimento-ativo",
    });
    expect(screen.getByTestId("composer")).toBeInTheDocument();
  });

  it("não renderiza composer quando o lead não tem atendimento ativo", () => {
    const finalizado: CartaoAtendimento = {
      ...cartaoInicial,
      atendimentoId: "atendimento-finalizado",
      atendimentoAtivoId: null,
      status: "FINALIZADO",
    };
    renderPagina();
    act(() => callbacks.atualizarLista?.([finalizado]));
    act(() => callbacks.abrir?.(finalizado));

    expect(screen.queryByTestId("composer")).not.toBeInTheDocument();
    expect(screen.getByText("Atendimento finalizado.")).toBeInTheDocument();
  });

  it("pede atendimento novo para o lead finalizado e mantém o envio fora do botão", async () => {
    const finalizado: CartaoAtendimento = {
      ...cartaoInicial,
      atendimentoId: "atendimento-finalizado",
      atendimentoAtivoId: null,
      status: "FINALIZADO",
      ultimaMensagemDoLeadEm: "2026-01-01T00:00:00Z",
    };
    renderPagina();
    act(() => callbacks.atualizarLista?.([finalizado]));
    act(() => callbacks.abrir?.(finalizado));

    fireEvent.click(screen.getByRole("button", { name: "Reativar atendimento" }));

    await waitFor(() =>
      expect(abrirExistente).toHaveBeenCalledWith("lead-1", expect.anything()),
    );
    act(() =>
      callbacks.atualizarLista?.([
        {
          ...finalizado,
          atendimentoId: "atendimento-novo",
          atendimentoAtivoId: "atendimento-novo",
          status: "EM_ATENDIMENTO",
        },
      ]),
    );
    await waitFor(() => expect(screen.getByTestId("composer")).toBeInTheDocument());
  });

  it("no celular mostra só a lista e troca para a conversa em tela cheia", () => {
    telaEstreita.atual = true;
    const pagina = renderPagina();
    act(() => callbacks.atualizarLista?.([cartaoInicial]));

    expect(screen.getByRole("button", { name: "Abrir lista" })).toBeInTheDocument();
    expect(screen.queryByTestId("responsavel-cabecalho")).not.toBeInTheDocument();

    act(() => callbacks.abrir?.(cartaoInicial));

    expect(screen.getByTestId("responsavel-cabecalho")).toBeInTheDocument();
    expect(screen.getByTestId("composer")).toBeInTheDocument();
    expect(pagina.container.firstElementChild).toHaveClass("grid-cols-1");
    expect(screen.queryByTestId("responsavel-painel")).not.toBeInTheDocument();
  });
});
