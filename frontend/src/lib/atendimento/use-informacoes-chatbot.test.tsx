import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, renderHook, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";

const api = vi.hoisted(() => ({ paginaInformacoesDoChatbot: vi.fn() }));
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
  type JanelaDoHistorico,
  useInformacoesDoChatbot,
} from "./use-informacoes-chatbot";

const ATENDIMENTO = "c0c00000-0000-4000-8000-0000000000a1";
const TUDO: JanelaDoHistorico = { desde: null };

function cartao(ordem: number): CartaoInformacoesChatbot {
  return {
    id: `c-${ordem}`,
    atendimentoId: ATENDIMENTO,
    conteudo: `card ${ordem}`,
    origem: "AUTOMACAO",
    registradoEm: `2026-10-08T14:0${ordem}:00Z`,
  };
}

function montar(atendimentoId: string | null, estado: EstadoConexao, janela: JanelaDoHistorico | null) {
  const cliente = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={cliente}>{children}</QueryClientProvider>
  );
  const hook = renderHook(
    (p: { id: string | null; conexao: EstadoConexao; janela: JanelaDoHistorico | null }) =>
      useInformacoesDoChatbot(p.id, p.conexao, p.janela),
    { wrapper, initialProps: { id: atendimentoId, conexao: estado, janela } },
  );
  return { cliente, ...hook };
}

describe("useInformacoesDoChatbot", () => {
  beforeEach(() => {
    api.paginaInformacoesDoChatbot
      .mockReset()
      .mockResolvedValue({ itens: [cartao(1)], proximoCursor: null });
    funcionalidades.data = [FUNCIONALIDADE_INFORMACOES_CHATBOT];
  });

  it("flag desligada: nenhuma requisição e lista vazia", async () => {
    funcionalidades.data = ["chat_interno"];

    const { result } = montar(ATENDIMENTO, "conectado", TUDO);
    await act(async () => {
      await Promise.resolve();
    });

    expect(api.paginaInformacoesDoChatbot).not.toHaveBeenCalled();
    expect(result.current).toEqual([]);
  });

  it("flags ainda carregando: também não consulta", async () => {
    funcionalidades.data = undefined;

    const { result } = montar(ATENDIMENTO, "conectado", TUDO);
    await act(async () => {
      await Promise.resolve();
    });

    expect(api.paginaInformacoesDoChatbot).not.toHaveBeenCalled();
    expect(result.current).toEqual([]);
  });

  it("sem conversa aberta ou com as mensagens ainda carregando (janela não pronta) não consulta", async () => {
    montar(null, "conectado", TUDO);
    montar(ATENDIMENTO, "conectado", null);
    await act(async () => {
      await Promise.resolve();
    });

    expect(api.paginaInformacoesDoChatbot).not.toHaveBeenCalled();
  });

  it("flag ligada: busca a primeira página da janela e devolve os cards", async () => {
    const { result } = montar(ATENDIMENTO, "desconectado", { desde: "2026-10-08T14:00:00Z" });

    await waitFor(() => expect(result.current).toEqual([cartao(1)]));
    expect(api.paginaInformacoesDoChatbot).toHaveBeenCalledWith(ATENDIMENTO, null, "2026-10-08T14:00:00Z");
  });

  it("segue o cursor até o fim da janela, uma página por vez, sem perder nem repetir card, em ordem cronológica", async () => {
    // O servidor entrega a MAIS RECENTE primeiro; cada página já vem em ordem cronológica.
    api.paginaInformacoesDoChatbot.mockReset().mockImplementation(async (_id: string, cursor: string | null) => {
      if (cursor === null) return { itens: [cartao(5), cartao(6), cartao(7)], proximoCursor: "p2" };
      if (cursor === "p2") return { itens: [cartao(2), cartao(3), cartao(4)], proximoCursor: "p3" };
      return { itens: [cartao(1)], proximoCursor: null };
    });

    const { result } = montar(ATENDIMENTO, "desconectado", TUDO);

    await waitFor(() => expect(result.current).toHaveLength(7));
    expect(result.current.map((c) => c.conteudo)).toEqual([
      "card 1", "card 2", "card 3", "card 4", "card 5", "card 6", "card 7",
    ]);
    expect(api.paginaInformacoesDoChatbot.mock.calls.map((chamada) => chamada[1])).toEqual([null, "p2", "p3"]);
  });

  it("ao carregar mensagens mais antigas a janela cresce e a consulta é refeita para o novo trecho", async () => {
    const { rerender, result } = montar(ATENDIMENTO, "desconectado", { desde: "2026-10-08T14:03:00Z" });
    await waitFor(() => expect(result.current).toEqual([cartao(1)]));

    api.paginaInformacoesDoChatbot.mockResolvedValue({ itens: [cartao(1), cartao(2)], proximoCursor: null });
    rerender({ id: ATENDIMENTO, conexao: "desconectado", janela: { desde: "2026-10-08T14:00:00Z" } });

    await waitFor(() => expect(api.paginaInformacoesDoChatbot).toHaveBeenCalledWith(ATENDIMENTO, null, "2026-10-08T14:00:00Z"));
    await waitFor(() => expect(result.current).toHaveLength(2));
  });

  it("revalida ao conectar e a cada reconexão: card criado antes de o canal estar assinado não se perde", async () => {
    const { result, rerender } = montar(ATENDIMENTO, "conectando", TUDO);
    await waitFor(() => expect(result.current).toEqual([cartao(1)]));
    expect(api.paginaInformacoesDoChatbot).toHaveBeenCalledTimes(1);

    rerender({ id: ATENDIMENTO, conexao: "conectado", janela: TUDO });
    await waitFor(() => expect(api.paginaInformacoesDoChatbot).toHaveBeenCalledTimes(2));

    rerender({ id: ATENDIMENTO, conexao: "reconectando", janela: TUDO });
    await act(async () => {
      await Promise.resolve();
    });
    expect(api.paginaInformacoesDoChatbot).toHaveBeenCalledTimes(2);

    rerender({ id: ATENDIMENTO, conexao: "conectado", janela: TUDO });
    await waitFor(() => expect(api.paginaInformacoesDoChatbot).toHaveBeenCalledTimes(3));
  });

  it("enquanto desconectado não revalida por conexão", async () => {
    montar(ATENDIMENTO, "desconectado", TUDO);
    await waitFor(() => expect(api.paginaInformacoesDoChatbot).toHaveBeenCalledTimes(1));
    await act(async () => {
      await Promise.resolve();
    });

    expect(api.paginaInformacoesDoChatbot).toHaveBeenCalledTimes(1);
  });

  it("a chave de cache é por atendimento, para o evento invalidar só a conversa certa", () => {
    expect(chaveInformacoesChatbot(ATENDIMENTO)).toEqual(["informacoes-chatbot", ATENDIMENTO]);
    expect(chaveInformacoesChatbot("outro")).not.toEqual(chaveInformacoesChatbot(ATENDIMENTO));
  });
});
