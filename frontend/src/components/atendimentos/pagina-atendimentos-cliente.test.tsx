import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { useEffect, useRef } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import type {
  CartaoAtendimento,
  ItemInbox,
  NotificacaoTempoReal,
} from "@/lib/atendimento/types";

const callbacks = vi.hoisted(() => ({
  abrir: undefined as ((cartao: ItemInbox) => void) | undefined,
  atualizarLista: undefined as ((cartoes: ItemInbox[]) => void) | undefined,
  mensagens: undefined as { historico: string | null; assinatura: string | null } | undefined,
}));

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
  }: {
    leadInicialGatilho?: number;
    onAbrirAtendimento: (cartao: ItemInbox) => void;
    onAtendimentosAtualizados?: (cartoes: ItemInbox[]) => void;
  }) => {
    const gatilhoAnterior = useRef(leadInicialGatilho);
    callbacks.abrir = onAbrirAtendimento;
    callbacks.atualizarLista = onAtendimentosAtualizados;
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
  }: {
    conversa: CartaoAtendimento;
    painelDetalhesAberto: boolean;
    onAlternarPainelDetalhes: () => void;
  }) => (
    <div data-testid="responsavel-cabecalho">
      {conversa.atendenteNome}
      {!painelDetalhesAberto && (
        <button type="button" onClick={onAlternarPainelDetalhes}>
          Reabrir detalhes do lead
        </button>
      )}
    </div>
  ),
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
vi.mock("./lista-mensagens", () => ({ ListaMensagens: () => null }));
vi.mock("./composer", () => ({ Composer: () => <div data-testid="composer" /> }));
vi.mock("@/lib/atendimento/api", () => ({ marcarAtendimentoComoLido: vi.fn(() => Promise.resolve()) }));
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
vi.mock("@/lib/atendimento/use-enviar-mensagem", () => ({
  useEnviarMensagem: () => ({ mutate: vi.fn() }),
}));
vi.mock("@/lib/auth/auth-store", () => ({
  useAuthStore: { getState: () => ({ accessToken: "token" }) },
}));
vi.mock("@/lib/config/textos-provider", () => ({
  useTextos: () => ({
    estados: { vazio: "Nenhuma conversa" },
    atendimentos: {
      finalizar: { sucesso: "Atendimento finalizado." },
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
    callbacks.mensagens = undefined;
    stomp.clientes.length = 0;
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

  it("fecha a superfície da conversa quando o atendimento desaparece da visão", () => {
    renderPagina();
    act(() => callbacks.atualizarLista?.([cartaoInicial]));
    act(() => callbacks.abrir?.(cartaoInicial));
    expect(screen.getByTestId("responsavel-cabecalho")).toBeInTheDocument();

    act(() => callbacks.atualizarLista?.([]));

    expect(screen.queryByTestId("responsavel-cabecalho")).not.toBeInTheDocument();
    expect(screen.queryByTestId("responsavel-painel")).not.toBeInTheDocument();
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
});
