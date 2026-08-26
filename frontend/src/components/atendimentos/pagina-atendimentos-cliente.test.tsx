import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { useEffect, useRef } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import type { CartaoAtendimento, NotificacaoTempoReal } from "@/lib/atendimento/types";

const callbacks = vi.hoisted(() => ({
  abrir: undefined as ((cartao: CartaoAtendimento) => void) | undefined,
  atualizarLista: undefined as ((cartoes: CartaoAtendimento[]) => void) | undefined,
  notificar: undefined as ((notificacao: NotificacaoTempoReal) => void) | undefined,
  mensagens: undefined as { historico: string | null; assinatura: string | null } | undefined,
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
    onAbrirAtendimento: (cartao: CartaoAtendimento) => void;
    onAtendimentosAtualizados?: (cartoes: CartaoAtendimento[]) => void;
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
  CabecalhoConversa: ({ conversa }: { conversa: CartaoAtendimento }) => (
    <div data-testid="responsavel-cabecalho">{conversa.atendenteNome}</div>
  ),
}));
vi.mock("./painel-da-conversa", () => ({
  PainelDaConversa: ({ responsavelNome }: { responsavelNome: string | null }) => (
    <div data-testid="responsavel-painel">{responsavelNome}</div>
  ),
}));
vi.mock("./lista-mensagens", () => ({ ListaMensagens: () => null }));
vi.mock("./composer", () => ({ Composer: () => <div data-testid="composer" /> }));
vi.mock("@/lib/atendimento/api", () => ({ marcarAtendimentoComoLido: vi.fn(() => Promise.resolve()) }));
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
vi.mock("@/lib/atendimento/tempo-real", () => ({
  useConexaoTempoReal: (_token: unknown, _onRevogacao: unknown, onNotificacao: (notificacao: NotificacaoTempoReal) => void) => {
    callbacks.notificar = onNotificacao;
    return { conexao: { fecharConversa: vi.fn() }, estado: "desconectado" };
  },
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
      },
    },
  }),
}));

import { PaginaAtendimentosCliente } from "./pagina-atendimentos-cliente";

function renderPagina() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <PaginaAtendimentosCliente leadInicialId={null} visaoInicial="TODOS" />
    </QueryClientProvider>,
  );
}

describe("PaginaAtendimentosCliente", () => {
  beforeEach(() => {
    callbacks.abrir = undefined;
    callbacks.atualizarLista = undefined;
    callbacks.notificar = undefined;
    callbacks.mensagens = undefined;
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

  it("abre duas vezes seguidas a mesma transferência pelo gatilho monotônico", async () => {
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
    act(() => callbacks.notificar?.(notificacao));
    fireEvent.click(screen.getByRole("button", { name: "Abrir transferência" }));
    await waitFor(() =>
      expect(screen.getByTestId("responsavel-cabecalho")).toHaveTextContent("Ana Atendente"),
    );

    act(() => callbacks.notificar?.(notificacao));
    fireEvent.click(screen.getByRole("button", { name: "Abrir transferência" }));
    await waitFor(() =>
      expect(screen.getByTestId("responsavel-cabecalho")).toHaveTextContent("Ana Atendente"),
    );
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
