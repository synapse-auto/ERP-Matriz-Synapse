import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, renderHook, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { ErroDeApi } from "@/lib/api/errors";

vi.mock("./api", () => ({
  enviarMidia: vi.fn(),
  enviarMensagem: vi.fn(),
  enviarTemplate: vi.fn(),
  paginaMensagens: vi.fn(),
  mensagensDesde: vi.fn(),
}));

import { enviarMensagem, enviarMidia, mensagensDesde, paginaMensagens } from "./api";
import type { DadosDoHistorico } from "./cache-mensagens";
import type { ConexaoTempoReal } from "./tempo-real";
import type {
  CitacaoMensagem,
  EnvioResposta,
  EventoTempoReal,
  MensagemResposta,
  MensagemTempoReal,
  StatusEntrega,
  TipoMensagem,
} from "./types";
import { useEnviarMensagem } from "./use-enviar-mensagem";
import { useEnviarMidia } from "./use-enviar-midia";
import { useMensagens } from "./use-mensagens";

/**
 * E217: HTTP e WebSocket confirmam o mesmo envio por caminhos independentes, sem ordem garantida.
 * Os hooks reais rodam juntos; o transporte STOMP é substituído por um ouvinte que o teste dispara
 * na ordem que quiser, e a resposta HTTP segue o contrato de `AtendimentoAcoesController.EnvioResposta`
 * — inclusive a `idempotencyKey`, que o backend sempre devolve.
 */

const ATENDENTE = { id: "atendente-1", nome: "Cris Atendente" };
const URL_ASSINADA = "https://storage.example/assinada/proposta.pdf";
const METADADOS_DO_SERVIDOR = JSON.stringify({
  nome: "proposta.pdf",
  mimetype: "application/pdf",
  tamanho: 8,
  legenda: "segue a proposta",
  paginas: 3,
});
const CITACAO: CitacaoMensagem = {
  origemId: "msg-antiga",
  tipoReferencia: "RESPOSTA",
  autor: "Cliente",
  tipoConteudo: "TEXTO",
  previa: "bom dia",
};
const ANTIGA: MensagemResposta = {
  id: "msg-antiga",
  remetenteTipo: "LEAD",
  remetenteId: null,
  remetenteNome: null,
  tipo: "TEXTO",
  conteudo: "bom dia",
  midiaUrl: null,
  midiaMetadados: null,
  opcoes: null,
  statusEntrega: "LIDO",
  erroEntrega: null,
  enviadoEm: "2026-01-01T00:00:00Z",
  citacao: null,
  idempotencyKey: null,
};

type Http = ReturnType<typeof respostaHttpControlada>;
type Montagem = Awaited<ReturnType<typeof prontoParaEnviar>>;

function transporteControlado() {
  let ouvinte: ((evento: EventoTempoReal) => void) | null = null;
  const conexao = {
    abrirConversa: vi.fn((_id: string, callback: (evento: EventoTempoReal) => void) => {
      ouvinte = callback;
    }),
    fecharConversa: vi.fn(() => {
      ouvinte = null;
    }),
  } as unknown as ConexaoTempoReal;
  return {
    conexao,
    emitir(evento: EventoTempoReal) {
      act(() => ouvinte?.(evento));
    },
  };
}

function respostaHttpControlada() {
  let resolver!: (valor: EnvioResposta) => void;
  let rejeitar!: (erro: unknown) => void;
  const promessa = new Promise<EnvioResposta>((resolve, reject) => {
    resolver = resolve;
    rejeitar = reject;
  });
  return { promessa, resolver, rejeitar };
}

async function prontoParaEnviar() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  queryClient.setQueryData(["me"], ATENDENTE);
  const transporte = transporteControlado();
  const Wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  );
  const hook = renderHook(
    ({ atendimentoId }: { atendimentoId: string }) => ({
      historico: useMensagens(atendimentoId, transporte.conexao, "desconectado"),
      midia: useEnviarMidia(),
      texto: useEnviarMensagem(),
    }),
    { wrapper: Wrapper, initialProps: { atendimentoId: "at-1" } },
  );
  await waitFor(() => expect(hook.result.current.historico.isSuccess).toBe(true));
  return { queryClient, transporte, hook };
}

function historico(queryClient: QueryClient, atendimentoId = "at-1"): MensagemResposta[] {
  return queryClient.getQueryData<DadosDoHistorico>(["mensagens", atendimentoId])
    ?.pages.flatMap((pagina) => pagina.mensagens) ?? [];
}

function enviadas(queryClient: QueryClient, atendimentoId = "at-1"): MensagemResposta[] {
  return historico(queryClient, atendimentoId).filter((mensagem) => mensagem.id !== ANTIGA.id);
}

function respostaDoBackend(
  mensagemId: string,
  chave: string,
  statusEntrega: StatusEntrega = "PENDENTE",
  enviadoEm = "2026-09-22T21:36:30Z",
): EnvioResposta {
  return {
    atendimentoId: "at-1",
    mensagemId,
    statusEntrega,
    enviadoEm,
    transferiuOLead: false,
    idempotencyKey: chave,
  };
}

function eventoMensagem(
  mensagemId: string,
  chave: string,
  extras: Partial<MensagemTempoReal> = {},
): EventoTempoReal {
  return {
    tipo: "MENSAGEM",
    dados: {
      atendimentoId: "at-1",
      leadId: "lead-1",
      mensagemId,
      remetenteTipo: "ATENDENTE",
      remetenteId: ATENDENTE.id,
      tipo: "DOCUMENTO",
      conteudo: null,
      midiaUrl: URL_ASSINADA,
      midiaMetadados: METADADOS_DO_SERVIDOR,
      opcoes: null,
      statusEntrega: "PENDENTE",
      enviadoEm: "2026-09-22T21:36:30Z",
      citacao: CITACAO,
      idempotencyKey: chave,
      ...extras,
    },
  };
}

function eventoStatus(mensagemId: string, chave: string, statusEntrega: StatusEntrega): EventoTempoReal {
  return {
    tipo: "STATUS",
    dados: {
      atendimentoId: "at-1",
      leadId: "lead-1",
      mensagemId,
      statusEntrega,
      ocorridoEm: "2026-09-22T21:36:31Z",
      idempotencyKey: chave,
    },
  };
}

async function enviarArquivo(
  montagem: Montagem,
  arquivo = new File(["conteudo"], "proposta.pdf", { type: "application/pdf" }),
  { citando = true } = {},
) {
  const antes = enviadas(montagem.queryClient).length;
  const http = respostaHttpControlada();
  vi.mocked(enviarMidia).mockImplementationOnce(() => http.promessa);
  act(() => {
    montagem.hook.result.current.midia.mutate({
      atendimentoId: "at-1",
      leadId: "lead-1",
      arquivo,
      legenda: "segue a proposta",
      ...(citando
        ? { resposta: { mensagemId: ANTIGA.id, enviadoEm: ANTIGA.enviadoEm }, citacao: CITACAO }
        : {}),
    });
  });
  await waitFor(() => expect(enviadas(montagem.queryClient)).toHaveLength(antes + 1));
  const chamadas = vi.mocked(enviarMidia).mock.calls;
  return { http, chave: chamadas[chamadas.length - 1][6] as string };
}

async function confirmarHttp(montagem: Montagem, http: Http, resposta: EnvioResposta) {
  await act(async () => {
    http.resolver(resposta);
    await http.promessa;
  });
  await waitFor(() => expect(montagem.hook.result.current.midia.isPending).toBe(false));
}

describe("confirmação do envio de mídia independente da ordem HTTP/WebSocket", () => {
  beforeEach(() => {
    vi.resetAllMocks();
    vi.mocked(paginaMensagens).mockResolvedValue({ mensagens: [ANTIGA], proximoCursor: null });
    vi.mocked(mensagensDesde).mockResolvedValue([]);
  });

  it("HTTP antes do WebSocket, ambos com a mesma chave real: a bolha não desaparece", async () => {
    const montagem = await prontoParaEnviar();
    const { http, chave } = await enviarArquivo(montagem);

    await confirmarHttp(montagem, http, respostaDoBackend("msg-real-1", chave));

    const aposHttp = enviadas(montagem.queryClient);
    expect(aposHttp).toHaveLength(1);
    expect(aposHttp[0]).toMatchObject({
      id: "msg-real-1",
      idempotencyKey: chave,
      tipo: "DOCUMENTO",
      remetenteTipo: "ATENDENTE",
      remetenteId: ATENDENTE.id,
      remetenteNome: ATENDENTE.nome,
      citacao: { origemId: ANTIGA.id },
    });
    expect(JSON.parse(aposHttp[0].midiaMetadados ?? "{}")).toMatchObject({
      nome: "proposta.pdf",
      legenda: "segue a proposta",
    });

    montagem.transporte.emitir(eventoMensagem("msg-real-1", chave));

    const aposSocket = enviadas(montagem.queryClient);
    expect(aposSocket).toHaveLength(1);
    expect(aposSocket[0]).toMatchObject({ id: "msg-real-1", midiaUrl: URL_ASSINADA });
    expect(historico(montagem.queryClient)[0]).toEqual(ANTIGA);
    expect(enviarMidia).toHaveBeenCalledTimes(1);
  });

  it("WebSocket antes do HTTP: preserva URL e metadados definitivos do servidor", async () => {
    const montagem = await prontoParaEnviar();
    const { http, chave } = await enviarArquivo(montagem);

    montagem.transporte.emitir(eventoMensagem("msg-real-2", chave));
    await confirmarHttp(montagem, http, respostaDoBackend("msg-real-2", chave));

    const mensagens = enviadas(montagem.queryClient);
    expect(mensagens).toHaveLength(1);
    expect(mensagens[0]).toMatchObject({
      id: "msg-real-2",
      midiaUrl: URL_ASSINADA,
      midiaMetadados: METADADOS_DO_SERVIDOR,
      remetenteNome: ATENDENTE.nome,
      idempotencyKey: chave,
      citacao: { origemId: ANTIGA.id },
    });
    expect(enviarMidia).toHaveBeenCalledTimes(1);
  });

  it("STATUS antes de MENSAGEM e antes do HTTP: não regride o status nem duplica", async () => {
    const montagem = await prontoParaEnviar();
    const { http, chave } = await enviarArquivo(montagem);

    montagem.transporte.emitir(eventoStatus("msg-real-3", chave, "ENVIADO"));
    await confirmarHttp(montagem, http, respostaDoBackend("msg-real-3", chave, "PENDENTE"));
    montagem.transporte.emitir(eventoMensagem("msg-real-3", chave, { statusEntrega: "PENDENTE" }));

    const mensagens = enviadas(montagem.queryClient);
    expect(mensagens).toHaveLength(1);
    expect(mensagens[0]).toMatchObject({
      id: "msg-real-3",
      statusEntrega: "ENVIADO",
      midiaUrl: URL_ASSINADA,
    });
    expect(enviarMidia).toHaveBeenCalledTimes(1);
  });

  it("evento duplicado e resposta HTTP tardia: uma bolha só, no status mais avançado", async () => {
    const montagem = await prontoParaEnviar();
    const { http, chave } = await enviarArquivo(montagem);

    montagem.transporte.emitir(eventoMensagem("msg-real-4", chave));
    montagem.transporte.emitir(eventoMensagem("msg-real-4", chave));
    montagem.transporte.emitir(eventoStatus("msg-real-4", chave, "ENTREGUE"));
    await confirmarHttp(montagem, http, respostaDoBackend("msg-real-4", chave, "PENDENTE"));
    montagem.transporte.emitir(eventoMensagem("msg-real-4", chave));

    const mensagens = enviadas(montagem.queryClient);
    expect(mensagens).toHaveLength(1);
    expect(mensagens[0]).toMatchObject({ statusEntrega: "ENTREGUE", midiaUrl: URL_ASSINADA });
    expect(enviarMidia).toHaveBeenCalledTimes(1);
  });

  it.each<[string, string, TipoMensagem]>([
    ["foto.jpg", "image/jpeg", "IMAGEM"],
    ["nota.ogg", "audio/ogg", "AUDIO"],
    ["proposta.pdf", "application/pdf", "DOCUMENTO"],
  ])("%s (%s) com HTTP primeiro permanece no histórico", async (nome, mimetype, tipo) => {
    const montagem = await prontoParaEnviar();
    const { http, chave } = await enviarArquivo(montagem, new File(["x"], nome, { type: mimetype }));

    await confirmarHttp(montagem, http, respostaDoBackend(`msg-${tipo}`, chave));

    const mensagens = enviadas(montagem.queryClient);
    expect(mensagens).toHaveLength(1);
    expect(mensagens[0]).toMatchObject({ id: `msg-${tipo}`, tipo });
    expect(enviarMidia).toHaveBeenCalledTimes(1);
  });

  it("dois arquivos em sequência, em ordens opostas, não apagam nem duplicam o anterior", async () => {
    const montagem = await prontoParaEnviar();
    const primeiro = await enviarArquivo(montagem);
    await confirmarHttp(montagem, primeiro.http, respostaDoBackend("msg-a", primeiro.chave));

    const segundo = await enviarArquivo(montagem, new File(["y"], "foto.jpg", { type: "image/jpeg" }));
    montagem.transporte.emitir(eventoMensagem("msg-b", segundo.chave, {
      tipo: "IMAGEM",
      midiaUrl: "https://storage.example/assinada/foto.jpg",
      midiaMetadados: null,
      enviadoEm: "2026-09-22T21:36:40Z",
    }));
    await confirmarHttp(
      montagem,
      segundo.http,
      respostaDoBackend("msg-b", segundo.chave, "PENDENTE", "2026-09-22T21:36:40Z"),
    );
    montagem.transporte.emitir(eventoMensagem("msg-a", primeiro.chave));

    const mensagens = enviadas(montagem.queryClient);
    expect(mensagens.map((mensagem) => mensagem.id)).toEqual(["msg-a", "msg-b"]);
    expect(mensagens[0].midiaUrl).toBe(URL_ASSINADA);
    expect(mensagens[1].midiaUrl).toBe("https://storage.example/assinada/foto.jpg");
    expect(new Set(mensagens.map((mensagem) => mensagem.idempotencyKey)).size).toBe(2);
    expect(enviarMidia).toHaveBeenCalledTimes(2);
  });

  it("troca de conversa durante o upload atualiza somente o cache da conversa de origem", async () => {
    const montagem = await prontoParaEnviar();
    const { http, chave } = await enviarArquivo(montagem);

    vi.mocked(paginaMensagens).mockResolvedValue({ mensagens: [], proximoCursor: null });
    montagem.hook.rerender({ atendimentoId: "at-2" });
    await waitFor(() => expect(montagem.queryClient.getQueryData(["mensagens", "at-2"])).toBeDefined());

    await confirmarHttp(montagem, http, respostaDoBackend("msg-real-5", chave));

    expect(enviadas(montagem.queryClient, "at-1")).toEqual([
      expect.objectContaining({ id: "msg-real-5", idempotencyKey: chave }),
    ]);
    expect(historico(montagem.queryClient, "at-2")).toEqual([]);
  });

  it("falha definitiva marca FALHOU sem reenviar", async () => {
    const montagem = await prontoParaEnviar();
    const { http } = await enviarArquivo(montagem, undefined, { citando: false });

    await act(async () => {
      http.rejeitar(new ErroDeApi(422, null, "arquivo recusado"));
      await http.promessa.catch(() => undefined);
    });

    await waitFor(() => expect(enviadas(montagem.queryClient)[0]?.statusEntrega).toBe("FALHOU"));
    expect(enviadas(montagem.queryClient)).toHaveLength(1);
    expect(enviarMidia).toHaveBeenCalledTimes(1);
  });

  it("falha ambígua reconcilia pela chave, sem novo POST, com uma bolha só", async () => {
    const montagem = await prontoParaEnviar();
    const { http, chave } = await enviarArquivo(montagem);
    const naBase: MensagemResposta = {
      ...ANTIGA,
      id: "msg-real-6",
      remetenteTipo: "ATENDENTE",
      remetenteId: ATENDENTE.id,
      tipo: "DOCUMENTO",
      conteudo: null,
      midiaUrl: URL_ASSINADA,
      midiaMetadados: METADADOS_DO_SERVIDOR,
      statusEntrega: "ENVIADO",
      enviadoEm: "2026-09-22T21:36:30Z",
      idempotencyKey: chave,
    };
    vi.mocked(paginaMensagens).mockResolvedValue({ mensagens: [ANTIGA, naBase], proximoCursor: null });

    await act(async () => {
      http.rejeitar(new TypeError("Failed to fetch"));
      await http.promessa.catch(() => undefined);
    });

    await waitFor(() => expect(montagem.hook.result.current.midia.isSuccess).toBe(true));
    const mensagens = enviadas(montagem.queryClient);
    expect(mensagens).toHaveLength(1);
    expect(mensagens[0]).toMatchObject({ id: "msg-real-6", midiaUrl: URL_ASSINADA, statusEntrega: "ENVIADO" });
    expect(enviarMidia).toHaveBeenCalledTimes(1);
  });

  it("a mensagem confirmada é recuperada após recarregar o histórico", async () => {
    const montagem = await prontoParaEnviar();
    const { http, chave } = await enviarArquivo(montagem);
    await confirmarHttp(montagem, http, respostaDoBackend("msg-real-7", chave));
    expect(enviadas(montagem.queryClient)).toHaveLength(1);
    const doServidor: MensagemResposta = {
      ...enviadas(montagem.queryClient)[0],
      midiaUrl: URL_ASSINADA,
      midiaMetadados: METADADOS_DO_SERVIDOR,
    };
    vi.mocked(paginaMensagens).mockResolvedValue({ mensagens: [ANTIGA, doServidor], proximoCursor: null });

    await act(async () => {
      await montagem.hook.result.current.historico.refetch();
    });

    const mensagens = enviadas(montagem.queryClient);
    expect(mensagens).toHaveLength(1);
    expect(mensagens[0]).toMatchObject({ id: "msg-real-7", idempotencyKey: chave, midiaUrl: URL_ASSINADA });
    expect(enviarMidia).toHaveBeenCalledTimes(1);
  });

  it("confirmação HTTP recoloca a mensagem se o histórico foi recarregado durante o upload", async () => {
    const montagem = await prontoParaEnviar();
    const { http, chave } = await enviarArquivo(montagem);
    await act(async () => {
      await montagem.hook.result.current.historico.refetch();
    });
    expect(enviadas(montagem.queryClient)).toHaveLength(0);

    await confirmarHttp(montagem, http, respostaDoBackend("msg-real-8", chave));

    expect(enviadas(montagem.queryClient)).toEqual([
      expect.objectContaining({ id: "msg-real-8", idempotencyKey: chave, tipo: "DOCUMENTO" }),
    ]);
  });
});

describe("confirmação do envio de texto independente da ordem HTTP/WebSocket", () => {
  beforeEach(() => {
    vi.resetAllMocks();
    vi.mocked(paginaMensagens).mockResolvedValue({ mensagens: [ANTIGA], proximoCursor: null });
    vi.mocked(mensagensDesde).mockResolvedValue([]);
  });

  async function enviarTexto(montagem: Montagem) {
    const http = respostaHttpControlada();
    vi.mocked(enviarMensagem).mockImplementationOnce(() => http.promessa);
    act(() => {
      montagem.hook.result.current.texto.mutate({
        atendimentoId: "at-1",
        leadId: "lead-1",
        conteudo: "orçamento enviado",
      });
    });
    await waitFor(() => expect(enviadas(montagem.queryClient)).toHaveLength(1));
    const chamadas = vi.mocked(enviarMensagem).mock.calls;
    return { http, chave: chamadas[chamadas.length - 1][4] as string };
  }

  async function confirmarTexto(montagem: Montagem, http: Http, resposta: EnvioResposta) {
    await act(async () => {
      http.resolver(resposta);
      await http.promessa;
    });
    await waitFor(() => expect(montagem.hook.result.current.texto.isPending).toBe(false));
  }

  const eventoTexto = { tipo: "TEXTO", conteudo: "orçamento enviado", midiaUrl: null, midiaMetadados: null, citacao: null } as const;

  it("HTTP antes do WebSocket mantém exatamente uma bolha", async () => {
    const montagem = await prontoParaEnviar();
    const { http, chave } = await enviarTexto(montagem);

    await confirmarTexto(montagem, http, respostaDoBackend("txt-1", chave));
    expect(enviadas(montagem.queryClient)).toHaveLength(1);
    montagem.transporte.emitir(eventoMensagem("txt-1", chave, eventoTexto));

    const mensagens = enviadas(montagem.queryClient);
    expect(mensagens).toHaveLength(1);
    expect(mensagens[0]).toMatchObject({ id: "txt-1", conteudo: "orçamento enviado", remetenteNome: ATENDENTE.nome });
    expect(enviarMensagem).toHaveBeenCalledTimes(1);
  });

  it("STATUS antes da resposta HTTP tardia não regride o status", async () => {
    const montagem = await prontoParaEnviar();
    const { http, chave } = await enviarTexto(montagem);

    montagem.transporte.emitir(eventoMensagem("txt-2", chave, eventoTexto));
    montagem.transporte.emitir(eventoStatus("txt-2", chave, "ENTREGUE"));
    await confirmarTexto(montagem, http, respostaDoBackend("txt-2", chave, "PENDENTE"));

    const mensagens = enviadas(montagem.queryClient);
    expect(mensagens).toHaveLength(1);
    expect(mensagens[0]).toMatchObject({ id: "txt-2", statusEntrega: "ENTREGUE" });
    expect(enviarMensagem).toHaveBeenCalledTimes(1);
  });
});
