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
      mensagemExterna: "Nova mensagem de {nome}", mensagemInterna: "Mensagem de {nome}",
      origemExterna: "Mensagem recebida no atendimento", origemInterna: "Mensagem recebida no chat interno",
      equipe: "Equipe", midia: "Nova mídia recebida", previewContinua: "…", abrir: "Abrir conversa",
      fechar: "Fechar aviso", somTitulo: "Som das notificações", somDescricao: "Som discreto",
      somAtivado: "Som ativado", somDesativado: "Som desativado",
      visualTitulo: "Notificações visuais", visualDescricao: "Avisos na tela", visualAtivado: "Visuais ativadas", visualDesativado: "Visuais desativadas",
      chatInternoTitulo: "Notificações do chat interno", chatInternoDescricao: "Avisos internos", chatInternoAtivado: "Internas ativadas", chatInternoDesativado: "Internas desativadas",
      duracaoTitulo: "Duração", duracaoDescricao: "Duração dos avisos", duracaoOpcao: "{segundos} segundos",
      posicaoTitulo: "Posição", posicaoDescricao: "Posição dos avisos", posicaoTopo: "Em cima", posicaoBaixo: "Embaixo",
    },
    atendimentos: {
      tempoReal: {
        transferenciaRecebida: "Transferência recebida", transferenciaRecebidaDescricao: "Transferido por {nome}",
        atendimentoDevolvidoParaIa: "Devolvido para IA", atendimentoDevolvidoParaIaDescricao: "IA retomou {nome}",
        conviteRecebido: "Convite para atendimento", conviteRecebidoDescricao: "Você foi convidado para participar de um atendimento.",
        abrirTransferencia: "Abrir atendimento", abrirConvite: "Abrir convite", fechar: "Fechar aviso",
      },
      media: { imagem: "Imagem", audio: "Áudio", documento: "Documento", localizacao: "Localização", visualizador: { video: "Vídeo" } },
    },
  }),
}));

vi.mock("@/lib/atendimento/preferencias-notificacoes", () => ({
  usePreferenciaSomDeNotificacao: () => ({ somHabilitado: false, definirSomHabilitado: vi.fn() }),
  usePreferenciaVisualDeNotificacao: () => ({ visualHabilitado: true, definirVisualHabilitado: vi.fn() }),
  usePreferenciaChatInternoDeNotificacao: () => ({ chatInternoHabilitado: true, definirChatInternoHabilitado: vi.fn() }),
  usePreferenciaDuracaoDeNotificacao: () => ({ duracaoSegundos: 3, definirDuracaoSegundos: vi.fn() }),
  usePreferenciaPosicaoDeNotificacao: () => ({ posicao: "TOPO", definirPosicao: vi.fn() }),
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
    fireEvent.click(screen.getByRole("button", { name: /Nova mensagem de Maria/ }));
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

  it("mantém o card e o fechamento focáveis para navegação por teclado", () => {
    renderizar();
    act(() => mocks.callback?.(mensagem()));

    const fechar = screen.getByRole("button", { name: "Fechar aviso" });
    const abrir = screen.getByRole("button", { name: /Nova mensagem de Maria/ });
    fechar.focus();
    expect(fechar).toHaveFocus();
    abrir.focus();
    expect(abrir).toHaveFocus();
  });

  it("não anuncia a origem do chat interno e navega ao clicar no card", () => {
    renderizar();
    act(() => mocks.callback?.({
      tipo: "CHAT_INTERNO_MENSAGEM",
      eventoId: "evento-interno",
      dados: {
        conversaId: "conversa-1", mensagemId: "mensagem-interna", remetenteId: "outro", remetenteNome: "João",
        tipo: "TEXTO", conteudo: "Olá equipe", midiaMetadados: null, enviadoEm: "2026-09-10T12:00:00Z",
      },
    }));
    expect(screen.getByRole("status")).toHaveTextContent("Mensagem de João");
    expect(screen.getByRole("status")).toHaveTextContent("Olá equipe");
    expect(screen.getByRole("status")).not.toHaveTextContent("Mensagem recebida no chat interno");
    fireEvent.click(screen.getByRole("button", { name: /Mensagem de João/ }));
    expect(mocks.push).toHaveBeenCalledWith("/chat-interno?conversaId=conversa-1");
  });

  it("exibe convite, oferece abrir convite e navega para o atendimento pendente", () => {
    renderizar();
    act(() => mocks.callback?.({
      tipo: "CONVITE_ATENDIMENTO",
      eventoId: "convite-1",
      dados: {
        atendimentoId: "atendimento-convite",
        leadId: "lead-convite",
        convidadorId: "atendente-1",
        ocorridoEm: "2026-09-20T12:00:00Z",
      },
    }));

    expect(screen.getByRole("status")).toHaveTextContent("Convite para atendimento");
    expect(screen.getByRole("status")).toHaveTextContent("Você foi convidado para participar de um atendimento.");
    fireEvent.click(screen.getByRole("button", { name: "Abrir convite" }));

    expect(mocks.push).toHaveBeenCalledWith("/atendimentos?leadId=lead-convite&atendimentoId=atendimento-convite&visao=PENDENTES");
    expect(screen.queryByRole("status")).not.toBeInTheDocument();
  });

  it("não duplica o aviso quando o mesmo convite chega novamente", () => {
    renderizar();
    const convite: NotificacaoTempoReal = {
      tipo: "CONVITE_ATENDIMENTO",
      eventoId: "convite-duplicado",
      dados: {
        atendimentoId: "atendimento-convite",
        leadId: "lead-convite",
        convidadorId: "atendente-1",
        ocorridoEm: "2026-09-20T12:00:00Z",
      },
    };
    act(() => {
      mocks.callback?.(convite);
      mocks.callback?.(convite);
    });

    expect(screen.getAllByRole("status")).toHaveLength(1);
  });
});
