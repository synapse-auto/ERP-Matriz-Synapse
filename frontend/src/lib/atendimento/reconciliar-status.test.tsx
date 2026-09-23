import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, renderHook, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import * as api from "./api";
import type { DadosDoHistorico } from "./cache-mensagens";
import type { ConexaoTempoReal, EstadoConexao } from "./tempo-real";
import { useEnviarMensagem } from "./use-enviar-mensagem";
import { useMensagens } from "./use-mensagens";
import type { EventoTempoReal, MensagemResposta, StatusEntrega } from "./types";

vi.mock("./api", () => ({
  enviarMensagem: vi.fn(),
  enviarTemplate: vi.fn(),
  paginaMensagens: vi.fn(),
  mensagensDesde: vi.fn(),
}));

const ENVIADO_EM = "2026-09-22T12:00:00Z";

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

function prepararHistorico(queryClient: QueryClient, atendimentoId: string, mensagens: MensagemResposta[]) {
  queryClient.setQueryData<DadosDoHistorico>(["mensagens", atendimentoId], {
    pages: [{ mensagens, proximoCursor: "cursor-da-pagina-antiga" }],
    pageParams: [null],
  });
}

function doAtendente(
  id: string,
  statusEntrega: StatusEntrega,
  idempotencyKey: string | null = null,
): MensagemResposta {
  return {
    id,
    remetenteTipo: "ATENDENTE",
    remetenteId: "atendente-1",
    remetenteNome: "Ana",
    tipo: "TEXTO",
    conteudo: "orçamento enviado",
    midiaUrl: null,
    midiaMetadados: null,
    opcoes: null,
    statusEntrega,
    erroEntrega: null,
    enviadoEm: ENVIADO_EM,
    citacao: null,
    idempotencyKey,
  };
}

function status(atendimentoId: string, mensagemId: string, statusEntrega: StatusEntrega): EventoTempoReal {
  return {
    tipo: "STATUS",
    // Igual ao RelayDeTempoRealListener.aoMudarStatus: o STATUS do backend não leva idempotencyKey.
    dados: { atendimentoId, leadId: "lead-1", mensagemId, statusEntrega, ocorridoEm: ENVIADO_EM },
  };
}

function mensagemTempoReal(atendimentoId: string, mensagemId: string, chave: string): EventoTempoReal {
  return {
    tipo: "MENSAGEM",
    dados: {
      atendimentoId,
      leadId: "lead-1",
      mensagemId,
      remetenteTipo: "ATENDENTE",
      remetenteId: "atendente-1",
      tipo: "TEXTO",
      conteudo: "orçamento enviado",
      midiaUrl: null,
      midiaMetadados: null,
      opcoes: null,
      statusEntrega: "PENDENTE",
      enviadoEm: ENVIADO_EM,
      idempotencyKey: chave,
    },
  };
}

function conexaoFalsa() {
  const alvo: { receber?: (evento: EventoTempoReal) => void } = {};
  const conexao = {
    abrirConversa: vi.fn((_id: string, callback: (evento: EventoTempoReal) => void) => {
      alvo.receber = callback;
    }),
    fecharConversa: vi.fn(),
  } as unknown as ConexaoTempoReal;
  return { conexao, receber: (evento: EventoTempoReal) => alvo.receber?.(evento) };
}

interface PropsDaTela {
  estado: EstadoConexao;
  liberado: boolean;
}

function renderizarTela(atendimentoId: string, conexao: ConexaoTempoReal, inicial: PropsDaTela) {
  const { queryClient, Wrapper } = criarWrapper();
  return {
    queryClient,
    montar: () =>
      renderHook(
        ({ estado, liberado }: PropsDaTela) => ({
          envio: useEnviarMensagem(),
          historico: useMensagens(
            atendimentoId,
            conexao,
            estado,
            undefined,
            atendimentoId,
            undefined,
            undefined,
            liberado,
          ),
        }),
        { wrapper: Wrapper, initialProps: inicial },
      ),
  };
}

describe("status de entrega sem F5", () => {
  beforeEach(() => {
    vi.resetAllMocks();
    vi.mocked(api.mensagensDesde).mockResolvedValue([]);
    vi.mocked(api.paginaMensagens).mockResolvedValue({ mensagens: [], proximoCursor: null });
  });

  it("status que muda com o WebSocket fora é reconciliado ao reconectar, mesmo sem mudar enviadoEm", async () => {
    const { conexao } = conexaoFalsa();
    const tela = renderizarTela("at-queda", conexao, { estado: "reconectando", liberado: false });
    prepararHistorico(tela.queryClient, "at-queda", [doAtendente("msg-1", "PENDENTE", "clique-1")]);
    const { result, rerender } = tela.montar();

    // Enquanto o socket estava fora, a outbox confirmou o envio. O /desde segue a regra do backend
    // (`enviado_em > desde`) e não devolve a mensagem cujo enviadoEm É o último instante conhecido.
    vi.mocked(api.paginaMensagens).mockResolvedValue({
      mensagens: [doAtendente("msg-1", "ENVIADO", "clique-1")],
      proximoCursor: "cursor-da-pagina-antiga",
    });
    rerender({ estado: "conectado", liberado: true });

    await waitFor(() => expect(result.current.historico.data[0]?.statusEntrega).toBe("ENVIADO"));
    expect(api.mensagensDesde).toHaveBeenCalledWith("at-queda", ENVIADO_EM);
    expect(api.paginaMensagens).toHaveBeenCalledWith("at-queda", null);
    expect(result.current.historico.data).toHaveLength(1);
    const cache = tela.queryClient.getQueryData<DadosDoHistorico>(["mensagens", "at-queda"]);
    expect(cache?.pages[0].proximoCursor).toBe("cursor-da-pagina-antiga");
  });

  it("STATUS descartado na janela do snapshot é recuperado quando os incrementais são liberados", async () => {
    const { conexao, receber } = conexaoFalsa();
    const tela = renderizarTela("at-janela", conexao, { estado: "conectado", liberado: false });
    prepararHistorico(tela.queryClient, "at-janela", [doAtendente("msg-1", "PENDENTE", "clique-1")]);
    const { result, rerender } = tela.montar();

    act(() => receber(status("at-janela", "msg-1", "ENVIADO")));
    expect(result.current.historico.data[0].statusEntrega).toBe("PENDENTE");

    vi.mocked(api.paginaMensagens).mockResolvedValue({
      mensagens: [doAtendente("msg-1", "ENVIADO", "clique-1")],
      proximoCursor: null,
    });
    rerender({ estado: "conectado", liberado: true });

    await waitFor(() => expect(result.current.historico.data[0]?.statusEntrega).toBe("ENVIADO"));
  });

  it("sem envio pendente, a reconexão não busca a página recente — só a lacuna do /desde", async () => {
    const { conexao } = conexaoFalsa();
    const tela = renderizarTela("at-sem-pendente", conexao, { estado: "reconectando", liberado: false });
    prepararHistorico(tela.queryClient, "at-sem-pendente", [doAtendente("msg-1", "LIDO", "clique-1")]);
    const { result, rerender } = tela.montar();

    rerender({ estado: "conectado", liberado: true });

    await waitFor(() => expect(api.mensagensDesde).toHaveBeenCalledTimes(1));
    await waitFor(() => expect(result.current.historico.isFetching).toBe(false));
    expect(api.paginaMensagens).not.toHaveBeenCalled();
  });

  it("STATUS que chega antes da resposta HTTP (bolha ainda otimista) não se perde", async () => {
    let responderHttp!: (resposta: Awaited<ReturnType<typeof api.enviarMensagem>>) => void;
    vi.mocked(api.enviarMensagem).mockImplementation(
      () => new Promise((resolve) => (responderHttp = resolve)),
    );
    const { conexao, receber } = conexaoFalsa();
    const tela = renderizarTela("at-status-antes", conexao, { estado: "conectado", liberado: true });
    prepararHistorico(tela.queryClient, "at-status-antes", []);
    const { result } = tela.montar();
    await waitFor(() => expect(result.current.historico.isFetching).toBe(false));

    act(() => result.current.envio.mutate({
      atendimentoId: "at-status-antes",
      leadId: "lead-1",
      conteudo: "orçamento enviado",
    }));
    await waitFor(() => expect(responderHttp).toBeDefined());
    const chave = vi.mocked(api.enviarMensagem).mock.calls[0][4] as string;
    // O servidor já persistiu e a outbox já confirmou: a página recente reflete isso.
    vi.mocked(api.paginaMensagens).mockResolvedValue({
      mensagens: [doAtendente("msg-real", "ENVIADO", chave)],
      proximoCursor: null,
    });

    act(() => receber(status("at-status-antes", "msg-real", "ENVIADO")));
    await waitFor(() => expect(result.current.historico.data[0]?.statusEntrega).toBe("ENVIADO"));

    act(() => responderHttp({
      atendimentoId: "at-status-antes",
      mensagemId: "msg-real",
      statusEntrega: "PENDENTE",
      enviadoEm: ENVIADO_EM,
      transferiuOLead: false,
      idempotencyKey: chave,
    }));
    await waitFor(() => expect(result.current.envio.isSuccess).toBe(true));

    expect(result.current.historico.data).toHaveLength(1);
    expect(result.current.historico.data[0]).toMatchObject({ id: "msg-real", statusEntrega: "ENVIADO" });
  });

  it("MENSAGEM e STATUS pelo WebSocket antes do HTTP: a resposta PENDENTE não rebaixa a bolha", async () => {
    let responderHttp!: (resposta: Awaited<ReturnType<typeof api.enviarMensagem>>) => void;
    vi.mocked(api.enviarMensagem).mockImplementation(
      () => new Promise((resolve) => (responderHttp = resolve)),
    );
    const { conexao, receber } = conexaoFalsa();
    const tela = renderizarTela("at-http-tardio", conexao, { estado: "conectado", liberado: true });
    prepararHistorico(tela.queryClient, "at-http-tardio", []);
    const { result } = tela.montar();
    await waitFor(() => expect(result.current.historico.isFetching).toBe(false));

    act(() => result.current.envio.mutate({
      atendimentoId: "at-http-tardio",
      leadId: "lead-1",
      conteudo: "orçamento enviado",
    }));
    await waitFor(() => expect(responderHttp).toBeDefined());
    const chave = vi.mocked(api.enviarMensagem).mock.calls[0][4] as string;

    act(() => receber(mensagemTempoReal("at-http-tardio", "msg-real", chave)));
    act(() => receber(status("at-http-tardio", "msg-real", "ENVIADO")));
    act(() => responderHttp({
      atendimentoId: "at-http-tardio",
      mensagemId: "msg-real",
      statusEntrega: "PENDENTE",
      enviadoEm: ENVIADO_EM,
      transferiuOLead: false,
      idempotencyKey: chave,
    }));
    await waitFor(() => expect(result.current.envio.isSuccess).toBe(true));

    expect(result.current.historico.data).toHaveLength(1);
    expect(result.current.historico.data[0]).toMatchObject({ id: "msg-real", statusEntrega: "ENVIADO" });
  });

  it("leitura pendurada da página recente não trava as reconciliações das reconexões seguintes", async () => {
    const { conexao } = conexaoFalsa();
    const tela = renderizarTela("at-pendurada", conexao, { estado: "conectado", liberado: true });
    prepararHistorico(tela.queryClient, "at-pendurada", [doAtendente("msg-1", "PENDENTE", "clique-1")]);
    // A primeira leitura nunca responde (rede morta sem erro), como um fetch sem timeout.
    vi.mocked(api.paginaMensagens).mockImplementationOnce(() => new Promise(() => {}));
    const { result, rerender } = tela.montar();
    await waitFor(() => expect(api.paginaMensagens).toHaveBeenCalledTimes(1));

    vi.mocked(api.paginaMensagens).mockResolvedValue({
      mensagens: [doAtendente("msg-1", "ENVIADO", "clique-1")],
      proximoCursor: null,
    });
    rerender({ estado: "reconectando", liberado: false });
    rerender({ estado: "conectado", liberado: true });

    await waitFor(() => expect(result.current.historico.data[0]?.statusEntrega).toBe("ENVIADO"));
  });

  it("eventos repetidos e fora de ordem não duplicam a bolha nem regridem o status", async () => {
    const { conexao, receber } = conexaoFalsa();
    const tela = renderizarTela("at-repetidos", conexao, { estado: "conectado", liberado: true });
    prepararHistorico(tela.queryClient, "at-repetidos", [doAtendente("msg-1", "PENDENTE", "clique-1")]);
    vi.mocked(api.paginaMensagens).mockResolvedValue({
      mensagens: [doAtendente("msg-1", "ENVIADO", "clique-1")],
      proximoCursor: null,
    });
    const { result, rerender } = tela.montar();

    act(() => receber(mensagemTempoReal("at-repetidos", "msg-1", "clique-1")));
    act(() => receber(mensagemTempoReal("at-repetidos", "msg-1", "clique-1")));
    act(() => receber(status("at-repetidos", "msg-1", "ENTREGUE")));
    act(() => receber(status("at-repetidos", "msg-1", "ENVIADO")));
    act(() => receber(status("at-repetidos", "msg-1", "ENTREGUE")));
    // Uma reconexão com backfill mais antigo que o WebSocket também não pode rebaixar.
    rerender({ estado: "reconectando", liberado: false });
    rerender({ estado: "conectado", liberado: true });
    await waitFor(() => expect(api.mensagensDesde).toHaveBeenCalledTimes(2));

    expect(result.current.historico.data).toHaveLength(1);
    expect(result.current.historico.data[0].statusEntrega).toBe("ENTREGUE");
  });
});
