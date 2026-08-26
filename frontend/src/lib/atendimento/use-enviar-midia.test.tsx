import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { renderHook, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { ErroDeApi } from "@/lib/api/errors";

import type { DadosDoHistorico } from "./cache-mensagens";
import { mesclarMensagens } from "./tempo-real";
import { useEnviarMidia } from "./use-enviar-midia";
import type { MensagemResposta } from "./types";

vi.mock("./api", () => ({
  enviarMidia: vi.fn(),
}));

import { enviarMidia } from "./api";

function wrapper(queryClient: QueryClient) {
  return function Wrapper({ children }: { children: ReactNode }) {
    return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>;
  };
}

function arquivoFake(nome: string, tipo: string): File {
  return new File(["conteudo"], nome, { type: tipo });
}

function prepararHistorico(queryClient: QueryClient, atendimentoId: string) {
  queryClient.setQueryData<DadosDoHistorico>(["mensagens", atendimentoId], {
    pages: [{ mensagens: [], proximoCursor: null }],
    pageParams: [null],
  });
}

function mensagensDoHistorico(queryClient: QueryClient, atendimentoId: string) {
  return queryClient.getQueryData<DadosDoHistorico>(["mensagens", atendimentoId])?.pages[0]
    .mensagens;
}

describe("useEnviarMidia", () => {
  beforeEach(() => vi.resetAllMocks());

  it("adiciona bolha PENDENTE otimista assim que o mutate roda", async () => {
    const queryClient = new QueryClient();
    prepararHistorico(queryClient, "at-1");
    vi.mocked(enviarMidia).mockImplementation(() => new Promise(() => {}));

    const { result } = renderHook(() => useEnviarMidia(), { wrapper: wrapper(queryClient) });

    result.current.mutate({
      atendimentoId: "at-1",
      leadId: "lead-1",
      arquivo: arquivoFake("foto.png", "image/png"),
    });

    await waitFor(() => {
      const mensagens = mensagensDoHistorico(queryClient, "at-1");
      expect(mensagens).toHaveLength(1);
      expect(mensagens?.[0].statusEntrega).toBe("PENDENTE");
      expect(mensagens?.[0].tipo).toBe("IMAGEM");
    });
  });

  it("troca para FALHOU quando o upload falha", async () => {
    const queryClient = new QueryClient();
    prepararHistorico(queryClient, "at-2");
    vi.mocked(enviarMidia).mockRejectedValue(new ErroDeApi(500, null, "falhou"));

    const { result } = renderHook(() => useEnviarMidia(), { wrapper: wrapper(queryClient) });

    result.current.mutate({
      atendimentoId: "at-2",
      leadId: "lead-2",
      arquivo: arquivoFake("relatorio.pdf", "application/pdf"),
    });

    await waitFor(() => {
      const mensagens = mensagensDoHistorico(queryClient, "at-2");
      expect(mensagens?.[0].statusEntrega).toBe("FALHOU");
    });
  });

  it("no sucesso troca o id temporario pelo id real devolvido pelo backend", async () => {
    const queryClient = new QueryClient();
    prepararHistorico(queryClient, "at-3");
    vi.mocked(enviarMidia).mockResolvedValue({
      atendimentoId: "at-3",
      mensagemId: "msg-real-123",
      statusEntrega: "ENVIADO",
      enviadoEm: "2026-01-01T00:00:00Z",
      transferiuOLead: false,
    });

    const { result } = renderHook(() => useEnviarMidia(), { wrapper: wrapper(queryClient) });

    result.current.mutate({
      atendimentoId: "at-3",
      leadId: "lead-3",
      arquivo: arquivoFake("audio.ogg", "audio/ogg"),
    });

    await waitFor(() => {
      const mensagens = mensagensDoHistorico(queryClient, "at-3");
      expect(mensagens?.[0].id).toBe("msg-real-123");
      expect(mensagens?.[0].statusEntrega).toBe("ENVIADO");
      expect(mensagens?.[0].tipo).toBe("AUDIO");
    });
  });

  it("reporta progresso de upload via onProgresso", async () => {
    const queryClient = new QueryClient();
    prepararHistorico(queryClient, "at-4");
    let progressoCapturado: ((percentual: number) => void) | undefined;
    vi.mocked(enviarMidia).mockImplementation((_id, _arquivo, _legenda, onProgresso) => {
      progressoCapturado = onProgresso as (percentual: number) => void;
      return new Promise(() => {});
    });

    const { result } = renderHook(() => useEnviarMidia(), { wrapper: wrapper(queryClient) });
    const onProgresso = vi.fn();

    result.current.mutate({
      atendimentoId: "at-4",
      leadId: "lead-4",
      arquivo: arquivoFake("foto.png", "image/png"),
      onProgresso,
    });

    await waitFor(() => expect(progressoCapturado).toBeDefined());
    progressoCapturado?.(42);
    expect(onProgresso).toHaveBeenCalledWith(42);
  });

  it("concilia upload de mídia com WebSocket sem duplicar a mensagem", async () => {
    let resolver!: (resposta: Awaited<ReturnType<typeof enviarMidia>>) => void;
    vi.mocked(enviarMidia).mockImplementation(() => new Promise((resolve) => (resolver = resolve)));
    const queryClient = new QueryClient();
    prepararHistorico(queryClient, "at-midia-corrida");
    queryClient.setQueryData(["me"], { id: "atendente-midia", nome: "Cris Atendente" });
    const { result } = renderHook(() => useEnviarMidia(), { wrapper: wrapper(queryClient) });

    result.current.mutate({
      atendimentoId: "at-midia-corrida",
      leadId: "lead-midia",
      arquivo: arquivoFake("audio.ogg", "audio/ogg"),
    });
    await waitFor(() => expect(resolver).toBeDefined());
    const temporaria = mensagensDoHistorico(queryClient, "at-midia-corrida")?.[0];
    expect(temporaria).toBeDefined();
    queryClient.setQueryData<DadosDoHistorico>(["mensagens", "at-midia-corrida"], (atual) =>
      atual
        ? {
            ...atual,
            pages: [{
              ...atual.pages[0],
              mensagens: mesclarMensagens(atual.pages[0].mensagens, [{
                ...temporaria!,
                id: "msg-midia-real",
                remetenteId: "atendente-midia",
                remetenteNome: "Cris Atendente",
              } as MensagemResposta]),
            }],
          }
        : atual,
    );
    resolver({
      atendimentoId: "at-midia-corrida",
      mensagemId: "msg-midia-real",
      statusEntrega: "ENVIADO",
      enviadoEm: "2026-01-01T00:00:03Z",
      transferiuOLead: false,
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    const mensagens = mensagensDoHistorico(queryClient, "at-midia-corrida");
    expect(mensagens).toHaveLength(1);
    expect(mensagens?.[0]).toMatchObject({
      id: "msg-midia-real",
      remetenteId: "atendente-midia",
      remetenteNome: "Cris Atendente",
    });
  });
});
