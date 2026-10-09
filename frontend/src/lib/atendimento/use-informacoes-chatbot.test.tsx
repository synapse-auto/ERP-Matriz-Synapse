import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, renderHook, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";

const api = vi.hoisted(() => ({ listarInformacoesDoChatbot: vi.fn() }));
vi.mock("./api", () => api);

const funcionalidades = vi.hoisted(() => ({ data: [] as string[] | undefined }));
vi.mock("@/lib/config/use-funcionalidades", () => ({
  useFuncionalidadesHabilitadas: () => ({ data: funcionalidades.data }),
}));

import type { EstadoConexao } from "./tempo-real";
import type { CartaoInformacoesChatbot } from "./types";
import {
  chaveInformacoesChatbot,
  FUNCIONALIDADE_INFORMACOES_CHATBOT,
  useInformacoesDoChatbot,
} from "./use-informacoes-chatbot";

const ATENDIMENTO = "c0c00000-0000-4000-8000-0000000000a1";

const CARTAO: CartaoInformacoesChatbot = {
  id: "c-1",
  atendimentoId: ATENDIMENTO,
  conteudo: "Nome: Maria",
  origem: "AUTOMACAO",
  registradoEm: "2026-10-08T14:05:00Z",
};

function montar(atendimentoId: string | null, estado: EstadoConexao) {
  const cliente = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={cliente}>{children}</QueryClientProvider>
  );
  const hook = renderHook(
    ({ id, conexao }: { id: string | null; conexao: EstadoConexao }) => useInformacoesDoChatbot(id, conexao),
    { wrapper, initialProps: { id: atendimentoId, conexao: estado } },
  );
  return { cliente, ...hook };
}

describe("useInformacoesDoChatbot", () => {
  beforeEach(() => {
    api.listarInformacoesDoChatbot.mockReset().mockResolvedValue({ itens: [CARTAO] });
    funcionalidades.data = [FUNCIONALIDADE_INFORMACOES_CHATBOT];
  });

  it("flag desligada: nenhuma requisição e lista vazia", async () => {
    funcionalidades.data = ["chat_interno"];

    const { result } = montar(ATENDIMENTO, "conectado");
    await act(async () => {
      await Promise.resolve();
    });

    expect(api.listarInformacoesDoChatbot).not.toHaveBeenCalled();
    expect(result.current).toEqual([]);
  });

  it("flags ainda carregando: também não consulta", async () => {
    funcionalidades.data = undefined;

    const { result } = montar(ATENDIMENTO, "conectado");
    await act(async () => {
      await Promise.resolve();
    });

    expect(api.listarInformacoesDoChatbot).not.toHaveBeenCalled();
    expect(result.current).toEqual([]);
  });

  it("sem conversa aberta não consulta", async () => {
    montar(null, "conectado");
    await act(async () => {
      await Promise.resolve();
    });

    expect(api.listarInformacoesDoChatbot).not.toHaveBeenCalled();
  });

  it("flag ligada: uma consulta por conversa, devolvendo os cards", async () => {
    const { result } = montar(ATENDIMENTO, "conectado");

    await waitFor(() => expect(result.current).toEqual([CARTAO]));
    expect(api.listarInformacoesDoChatbot).toHaveBeenCalledTimes(1);
    expect(api.listarInformacoesDoChatbot).toHaveBeenCalledWith(ATENDIMENTO);
  });

  it("revalida ao conectar e a cada reconexão: card criado antes de o canal estar assinado não se perde", async () => {
    const { result, rerender } = montar(ATENDIMENTO, "conectando");
    await waitFor(() => expect(result.current).toEqual([CARTAO]));
    expect(api.listarInformacoesDoChatbot).toHaveBeenCalledTimes(1);

    rerender({ id: ATENDIMENTO, conexao: "conectado" });
    await waitFor(() => expect(api.listarInformacoesDoChatbot).toHaveBeenCalledTimes(2));

    rerender({ id: ATENDIMENTO, conexao: "reconectando" });
    await act(async () => {
      await Promise.resolve();
    });
    expect(api.listarInformacoesDoChatbot).toHaveBeenCalledTimes(2);

    rerender({ id: ATENDIMENTO, conexao: "conectado" });
    await waitFor(() => expect(api.listarInformacoesDoChatbot).toHaveBeenCalledTimes(3));
  });

  it("enquanto desconectado não revalida por conexão", async () => {
    montar(ATENDIMENTO, "desconectado");
    await waitFor(() => expect(api.listarInformacoesDoChatbot).toHaveBeenCalledTimes(1));
    await act(async () => {
      await Promise.resolve();
    });

    expect(api.listarInformacoesDoChatbot).toHaveBeenCalledTimes(1);
  });

  it("a chave de cache é por atendimento, para o evento invalidar só a conversa certa", () => {
    expect(chaveInformacoesChatbot(ATENDIMENTO)).toEqual(["informacoes-chatbot", ATENDIMENTO]);
    expect(chaveInformacoesChatbot("outro")).not.toEqual(chaveInformacoesChatbot(ATENDIMENTO));
  });
});
