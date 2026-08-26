import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, renderHook, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import * as api from "./api";
import type { DadosDoHistorico } from "./cache-mensagens";
import type { ConexaoTempoReal } from "./tempo-real";
import { useEnviarMensagem } from "./use-enviar-mensagem";
import { useMensagens } from "./use-mensagens";
import type { EventoTempoReal, MensagemResposta } from "./types";

vi.mock("./api", () => ({
  enviarMensagem: vi.fn(),
  paginaMensagens: vi.fn(),
  mensagensDesde: vi.fn(),
}));

function criarWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false, staleTime: Number.POSITIVE_INFINITY },
      mutations: { retry: false },
    },
  });
  function Wrapper({ children }: { children: React.ReactNode }) {
    return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>;
  }
  return { queryClient, Wrapper };
}

function prepararHistorico(
  queryClient: QueryClient,
  atendimentoId: string,
  mensagens: MensagemResposta[] = [],
) {
  queryClient.setQueryData<DadosDoHistorico>(["mensagens", atendimentoId], {
    pages: [{ mensagens, proximoCursor: null }],
    pageParams: [null],
  });
}

function mensagensDoHistorico(queryClient: QueryClient, atendimentoId: string) {
  return queryClient.getQueryData<DadosDoHistorico>(["mensagens", atendimentoId])?.pages[0]
    .mensagens;
}

describe("useEnviarMensagem", () => {
  beforeEach(() => {
    vi.resetAllMocks();
  });

  it("sucesso: insere no cache a mensagem real devolvida pelo backend, com status PENDENTE", async () => {
    vi.mocked(api.enviarMensagem).mockResolvedValue({
      atendimentoId: "at-1",
      mensagemId: "msg-1",
      statusEntrega: "PENDENTE",
      enviadoEm: "2026-01-01T00:00:00Z",
      transferiuOLead: false,
    });
    const { queryClient, Wrapper } = criarWrapper();
    prepararHistorico(queryClient, "at-1");
    const { result } = renderHook(() => useEnviarMensagem(), { wrapper: Wrapper });

    result.current.mutate({ atendimentoId: "at-1", leadId: "lead-1", conteudo: "olá" });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    const mensagens = mensagensDoHistorico(queryClient, "at-1");
    expect(mensagens).toHaveLength(1);
    expect(mensagens?.[0].id).toBe("msg-1");
    expect(mensagens?.[0].statusEntrega).toBe("PENDENTE");
  });

  it("falha de rede: a mensagem otimista transita para FALHOU, sem duplicar entrada", async () => {
    vi.mocked(api.enviarMensagem).mockRejectedValue(new Error("falha de rede"));
    const { queryClient, Wrapper } = criarWrapper();
    prepararHistorico(queryClient, "at-2");
    const { result } = renderHook(() => useEnviarMensagem(), { wrapper: Wrapper });

    result.current.mutate({ atendimentoId: "at-2", leadId: "lead-2", conteudo: "olá" });

    await waitFor(() => expect(result.current.isError).toBe(true));

    const mensagens = mensagensDoHistorico(queryClient, "at-2");
    expect(mensagens).toHaveLength(1);
    expect(mensagens?.[0].statusEntrega).toBe("FALHOU");
  });

  it("a otimista PENDENTE aparece no resultado consumido pela tela via useMensagens", async () => {
    vi.mocked(api.enviarMensagem).mockImplementation(() => new Promise(() => {}));
    const { queryClient, Wrapper } = criarWrapper();
    prepararHistorico(queryClient, "at-3");
    const conexao = {
      abrirConversa: vi.fn(),
      fecharConversa: vi.fn(),
    } as unknown as ConexaoTempoReal;

    const { result } = renderHook(
      () => ({
        envio: useEnviarMensagem(),
        historico: useMensagens("at-3", conexao, "desconectado"),
      }),
      { wrapper: Wrapper },
    );

    act(() =>
      result.current.envio.mutate({
        atendimentoId: "at-3",
        leadId: "lead-3",
        conteudo: "mensagem visível",
      }),
    );

    await waitFor(() => {
      expect(result.current.historico.data).toHaveLength(1);
      expect(result.current.historico.data[0]).toMatchObject({
        conteudo: "mensagem visível",
        statusEntrega: "PENDENTE",
      });
    });
  });

  it("concilia quando o WebSocket chega antes do HTTP, sem duplicar e preservando autoria", async () => {
    let resolver!: (resposta: Awaited<ReturnType<typeof api.enviarMensagem>>) => void;
    vi.mocked(api.enviarMensagem).mockImplementation(
      () => new Promise((resolve) => (resolver = resolve)),
    );
    vi.mocked(api.paginaMensagens).mockResolvedValue({ mensagens: [], proximoCursor: null });
    const { queryClient, Wrapper } = criarWrapper();
    prepararHistorico(queryClient, "at-corrida-1");
    queryClient.setQueryData(["me"], { id: "atendente-1", nome: "Ana Atendente" });
    let receber!: (evento: EventoTempoReal) => void;
    const conexao = {
      abrirConversa: vi.fn((_id: string, callback: (evento: EventoTempoReal) => void) => { receber = callback; }),
      fecharConversa: vi.fn(),
    } as unknown as ConexaoTempoReal;
    const { result } = renderHook(
      () => ({ envio: useEnviarMensagem(), historico: useMensagens("at-corrida-1", conexao, "desconectado") }),
      { wrapper: Wrapper },
    );

    await waitFor(() => expect(receber).toBeDefined());
    act(() => result.current.envio.mutate({ atendimentoId: "at-corrida-1", leadId: "lead-1", conteudo: "olá" }));
    await waitFor(() => expect(resolver).toBeDefined());
    act(() => receber({
      tipo: "MENSAGEM",
      dados: {
        atendimentoId: "at-corrida-1",
        leadId: "lead-1",
        mensagemId: "msg-real-1",
        remetenteTipo: "ATENDENTE",
        remetenteId: "atendente-1",
        tipo: "TEXTO",
        conteudo: "olá",
        midiaUrl: null,
        midiaMetadados: null,
        opcoes: null,
        statusEntrega: "PENDENTE",
        enviadoEm: "2026-01-01T00:00:01Z",
      },
    }));
    act(() => resolver({
      atendimentoId: "at-corrida-1",
      mensagemId: "msg-real-1",
      statusEntrega: "PENDENTE",
      enviadoEm: "2026-01-01T00:00:01Z",
      transferiuOLead: false,
    }));

    await waitFor(() => expect(result.current.envio.isSuccess).toBe(true));
    const mensagens = mensagensDoHistorico(queryClient, "at-corrida-1");
    expect(mensagens).toHaveLength(1);
    expect(mensagens?.[0]).toMatchObject({
      id: "msg-real-1",
      remetenteId: "atendente-1",
      remetenteNome: "Ana Atendente",
    });
  });

  it("concilia quando o HTTP chega antes do WebSocket, sem duplicar", async () => {
    vi.mocked(api.enviarMensagem).mockResolvedValue({
      atendimentoId: "at-corrida-2",
      mensagemId: "msg-real-2",
      statusEntrega: "ENVIADO",
      enviadoEm: "2026-01-01T00:00:02Z",
      transferiuOLead: false,
    });
    const { queryClient, Wrapper } = criarWrapper();
    prepararHistorico(queryClient, "at-corrida-2");
    queryClient.setQueryData(["me"], { id: "atendente-2", nome: "Bruno Atendente" });
    let receber!: (evento: EventoTempoReal) => void;
    const conexao = {
      abrirConversa: vi.fn((_id: string, callback: (evento: EventoTempoReal) => void) => { receber = callback; }),
      fecharConversa: vi.fn(),
    } as unknown as ConexaoTempoReal;
    const { result } = renderHook(
      () => ({ envio: useEnviarMensagem(), historico: useMensagens("at-corrida-2", conexao, "desconectado") }),
      { wrapper: Wrapper },
    );

    await waitFor(() => expect(result.current.historico.data).toBeDefined());
    act(() => result.current.envio.mutate({ atendimentoId: "at-corrida-2", leadId: "lead-2", conteudo: "retorno" }));
    await waitFor(() => expect(result.current.envio.isSuccess).toBe(true));
    const antesDoSocket = mensagensDoHistorico(queryClient, "at-corrida-2");
    expect(antesDoSocket).toHaveLength(1);

    await waitFor(() => expect(receber).toBeDefined());
    act(() => receber({
      tipo: "MENSAGEM",
      dados: {
        atendimentoId: "at-corrida-2",
        leadId: "lead-2",
        mensagemId: "msg-real-2",
        remetenteTipo: "ATENDENTE",
        remetenteId: "atendente-2",
        tipo: "TEXTO",
        conteudo: "retorno",
        midiaUrl: null,
        midiaMetadados: null,
        opcoes: null,
        statusEntrega: "ENVIADO",
        enviadoEm: "2026-01-01T00:00:02Z",
      },
    }));

    const mensagens = mensagensDoHistorico(queryClient, "at-corrida-2");
    expect(mensagens).toHaveLength(1);
    expect(mensagens?.[0].remetenteNome).toBe("Bruno Atendente");
  });

  it("avanca a leitura quando chega mensagem com a conversa aberta", async () => {
    vi.mocked(api.paginaMensagens).mockResolvedValue({ mensagens: [], proximoCursor: null });
    const { Wrapper } = criarWrapper();
    let receber!: (evento: EventoTempoReal) => void;
    const conexao = {
      abrirConversa: vi.fn((_id: string, callback: (evento: EventoTempoReal) => void) => {
        receber = callback;
      }),
      fecharConversa: vi.fn(),
    } as unknown as ConexaoTempoReal;
    const leitura = vi.fn();

    renderHook(() => useMensagens("at-leitura", conexao, "desconectado", leitura), {
      wrapper: Wrapper,
    });
    await waitFor(() => expect(receber).toBeDefined());

    act(() => receber({
      tipo: "MENSAGEM",
      dados: {
        atendimentoId: "at-leitura",
        leadId: "lead-leitura",
        mensagemId: "msg-leitura",
        remetenteTipo: "LEAD",
        remetenteId: null,
        tipo: "TEXTO",
        conteudo: "nova mensagem",
        midiaUrl: null,
        midiaMetadados: null,
        opcoes: null,
        statusEntrega: "ENVIADO",
        enviadoEm: "2026-01-01T00:00:00Z",
      },
    }));

    expect(leitura).toHaveBeenCalledOnce();
  });
});
