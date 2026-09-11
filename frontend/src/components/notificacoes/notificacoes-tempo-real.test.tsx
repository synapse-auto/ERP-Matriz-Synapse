import { act, fireEvent, render, screen } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { describe, expect, it, vi } from "vitest";

import type { NotificacaoTempoReal } from "@/lib/atendimento/types";

const mocks = vi.hoisted(() => ({
  callback: undefined as ((notificacao: NotificacaoTempoReal) => void) | undefined,
  push: vi.fn(),
}));

vi.mock("@/lib/atendimento/tempo-real", () => ({
  useConexaoTempoReal: vi.fn((_token: unknown, _revogacao: unknown, callback: (notificacao: NotificacaoTempoReal) => void) => {
    mocks.callback = callback;
    return { conexao: {}, estado: "conectado" };
  }),
}));

vi.mock("@/lib/config/textos-provider", () => ({
  useTextos: () => ({
    notificacoes: {
      mensagemExterna: "Nova mensagem de {nome}", mensagemInterna: "Mensagem interna de {nome}",
      origemExterna: "Mensagem recebida no atendimento", origemInterna: "Mensagem recebida no chat interno",
      equipe: "Equipe", midia: "Nova mídia recebida", previewContinua: "…", abrir: "Abrir conversa",
      fechar: "Fechar aviso", somTitulo: "Som das notificações", somDescricao: "Som discreto",
      somAtivado: "Som ativado", somDesativado: "Som desativado",
    },
    atendimentos: {
      tempoReal: { transferenciaRecebida: "Transferência recebida", transferenciaRecebidaDescricao: "Transferido por {nome}", atendimentoDevolvidoParaIa: "Devolvido para IA", atendimentoDevolvidoParaIaDescricao: "IA retomou {nome}" },
      media: { imagem: "Imagem", audio: "Áudio", documento: "Documento", localizacao: "Localização", visualizador: { video: "Vídeo" } },
    },
  }),
}));

vi.mock("@/lib/atendimento/preferencias-notificacoes", () => ({
  usePreferenciaSomDeNotificacao: () => ({ somHabilitado: false, definirSomHabilitado: vi.fn() }),
}));

vi.mock("@/lib/atendimento/som-de-notificacao", () => ({
  tocarSomDeNotificacao: vi.fn(),
  registrarDesbloqueioDeAudio: vi.fn(() => vi.fn()),
}));

vi.mock("@/lib/auth/auth-store", () => {
  const estado = { usuarioId: "usuario-atual", accessToken: "token" };
  const useAuthStore = Object.assign((selecionar: (valor: typeof estado) => unknown) => selecionar(estado), {
    getState: () => estado,
  });
  return { useAuthStore };
});

vi.mock("next/navigation", () => ({
  usePathname: () => "/dashboard",
  useRouter: () => ({ push: mocks.push }),
}));

import { NotificacoesTempoReal } from "./notificacoes-tempo-real";

function renderizar() {
  return render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <NotificacoesTempoReal />
    </QueryClientProvider>,
  );
}

function mensagem(): Extract<NotificacaoTempoReal, { tipo: "NOVA_MENSAGEM" }> {
  return {
    tipo: "NOVA_MENSAGEM",
    eventoId: "evento-1",
    dados: {
      atendimentoId: "atendimento-1", leadId: "lead-1", leadNome: "Maria", mensagemId: "mensagem-1",
      remetenteTipo: "LEAD", remetenteId: "lead-1", tipo: "TEXTO", conteudo: "Olá", midiaMetadados: null,
      enviadoEm: "2026-09-10T12:00:00Z",
    },
  };
}

describe("NotificacoesTempoReal", () => {
  it("renderiza aviso com nome acessível e navega para o atendimento correto", () => {
    renderizar();

    act(() => mocks.callback?.(mensagem()));

    expect(screen.getByRole("status")).toHaveTextContent("Nova mensagem de Maria");
    expect(screen.getByRole("button", { name: "Fechar aviso" })).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Abrir conversa" }));
    expect(mocks.push).toHaveBeenCalledWith("/atendimentos?leadId=lead-1&atendimentoId=atendimento-1&visao=ATIVOS");
  });

  it("fecha o aviso sem interferir na mensagem recebida", () => {
    renderizar();

    act(() => mocks.callback?.(mensagem()));
    fireEvent.click(screen.getByRole("button", { name: "Fechar aviso" }));

    expect(screen.queryByRole("status")).not.toBeInTheDocument();
  });

  it("resume a mídia recebida sem expor URL ou token", () => {
    renderizar();

    act(() => mocks.callback?.({
      ...mensagem(),
      eventoId: "evento-audio",
      dados: {
        ...mensagem().dados,
        mensagemId: "mensagem-audio",
        tipo: "AUDIO",
        conteudo: null,
        midiaMetadados: JSON.stringify({ mimetype: "audio/ogg", url: "https://privado.invalid/token" }),
      },
    }));

    expect(screen.getByRole("status")).toHaveTextContent("Áudio");
    expect(screen.getByRole("status")).not.toHaveTextContent("privado.invalid");
    expect(screen.getByRole("status")).not.toHaveTextContent("token");
  });

  it("não cria aviso para mensagem de saída, embora o cache possa ser atualizado", () => {
    renderizar();
    act(() => mocks.callback?.({
      ...mensagem(),
      eventoId: "evento-saida",
      dados: { ...mensagem().dados, mensagemId: "mensagem-saida", remetenteTipo: "ATENDENTE", remetenteId: "usuario-atual" },
    }));

    expect(screen.queryByRole("status")).not.toBeInTheDocument();
  });

  it("mantém as ações do aviso focáveis para navegação por teclado", () => {
    renderizar();
    act(() => mocks.callback?.(mensagem()));

    const fechar = screen.getByRole("button", { name: "Fechar aviso" });
    const abrir = screen.getByRole("button", { name: "Abrir conversa" });
    fechar.focus();
    expect(fechar).toHaveFocus();
    abrir.focus();
    expect(abrir).toHaveFocus();
  });
});
