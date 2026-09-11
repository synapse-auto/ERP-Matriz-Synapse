import { describe, expect, it } from "vitest";

import { ServicoDeNotificacoesTempoReal } from "./servico-notificacoes-tempo-real";
import type { NotificacaoTempoReal } from "./types";

const contexto = {
  usuarioId: "usuario-atual",
  conversaAtiva: null,
  somHabilitado: true,
};

function mensagemExterna(overrides: Partial<Extract<NotificacaoTempoReal, { tipo: "NOVA_MENSAGEM" }>> = {}): Extract<NotificacaoTempoReal, { tipo: "NOVA_MENSAGEM" }> {
  return {
    tipo: "NOVA_MENSAGEM",
    eventoId: "evento-1",
    dados: {
      atendimentoId: "atendimento-1",
      leadId: "lead-1",
      leadNome: "Maria",
      mensagemId: "mensagem-1",
      remetenteTipo: "LEAD",
      remetenteId: "lead-1",
      tipo: "TEXTO",
      conteudo: "Olá",
      midiaMetadados: null,
      enviadoEm: "2026-09-10T12:00:00Z",
    },
    ...overrides,
  };
}

function mensagemInterna(overrides: Partial<Extract<NotificacaoTempoReal, { tipo: "CHAT_INTERNO_MENSAGEM" }>> = {}): NotificacaoTempoReal {
  return {
    tipo: "CHAT_INTERNO_MENSAGEM",
    eventoId: "evento-interno-1",
    dados: {
      conversaId: "conversa-1",
      mensagemId: "mensagem-interna-1",
      remetenteId: "outro-usuario",
      remetenteNome: "João",
      tipo: "TEXTO",
      conteudo: "Preciso de ajuda",
      midiaMetadados: null,
      enviadoEm: "2026-09-10T12:00:00Z",
    },
    ...overrides,
  };
}

function dadosDaMensagemInterna() {
  return {
    conversaId: "conversa-1",
    mensagemId: "mensagem-interna-1",
    remetenteId: "outro-usuario",
    remetenteNome: "João",
    tipo: "TEXTO",
    conteudo: "Preciso de ajuda",
    midiaMetadados: null,
    enviadoEm: "2026-09-10T12:00:00Z",
  } as const;
}

describe("ServicoDeNotificacoesTempoReal", () => {
  it("exibe e toca para nova mensagem externa fora da conversa ativa", () => {
    const decisao = new ServicoDeNotificacoesTempoReal().decidir(mensagemExterna(), contexto);

    expect(decisao).toMatchObject({
      exibir: true,
      tocar: true,
      atualizarAtendimentos: true,
      atualizarChatInterno: false,
    });
  });

  it("deduplica pelo id técnico do evento", () => {
    const servico = new ServicoDeNotificacoesTempoReal();

    expect(servico.decidir(mensagemExterna(), contexto)).not.toBeNull();
    expect(servico.decidir(mensagemExterna(), contexto)).toBeNull();
  });

  it("não transforma mensagem de saída em notificação, mas mantém a atualização do atendimento", () => {
    const enviada = mensagemExterna({
      dados: { ...mensagemExterna().dados, remetenteTipo: "ATENDENTE", remetenteId: "usuario-atual" },
    });

    expect(new ServicoDeNotificacoesTempoReal().decidir(enviada, contexto)).toMatchObject({
      exibir: false,
      tocar: false,
      atualizarAtendimentos: true,
    });
  });

  it("não exibe nem toca para mensagem interna do próprio usuário", () => {
    const notificacao = mensagemInterna({
      dados: { ...dadosDaMensagemInterna(), remetenteId: "usuario-atual" },
    });

    expect(new ServicoDeNotificacoesTempoReal().decidir(notificacao, contexto)).toMatchObject({
      exibir: false,
      tocar: false,
      atualizarChatInterno: true,
    });
  });

  it("silencia a conversa aberta, mas ainda solicita atualização do cache", () => {
    const notificacao = mensagemInterna();
    const decisao = new ServicoDeNotificacoesTempoReal().decidir(notificacao, {
      ...contexto,
      conversaAtiva: { origem: "CHAT_INTERNO", id: "conversa-1" },
    });

    expect(decisao).toMatchObject({
      exibir: false,
      tocar: false,
      atualizarAtendimentos: false,
      atualizarChatInterno: true,
    });
  });

  it("coalesce o som de uma rajada da mesma origem", () => {
    const servico = new ServicoDeNotificacoesTempoReal();
    const primeira = servico.decidir(mensagemInterna(), { ...contexto, agora: 1000 });
    const segunda = servico.decidir(mensagemInterna({
      eventoId: "evento-interno-2",
      dados: { ...dadosDaMensagemInterna(), mensagemId: "mensagem-interna-2" },
    }), { ...contexto, agora: 1200 });

    expect(primeira?.tocar).toBe(true);
    expect(segunda?.exibir).toBe(true);
    expect(segunda?.tocar).toBe(false);
  });

  it("não cria aviso visual para reações ou remoções", () => {
    const servico = new ServicoDeNotificacoesTempoReal();
    const reacao: NotificacaoTempoReal = {
      tipo: "CHAT_INTERNO_REACAO",
      dados: { conversaId: "conversa-1", mensagemId: "mensagem-1", atorId: "outro-usuario", emojiDoAtor: "👍", reacoes: [] },
    };
    const removida: NotificacaoTempoReal = {
      tipo: "CHAT_INTERNO_MENSAGEM_REMOVIDA",
      dados: { conversaId: "conversa-1", mensagemId: "mensagem-1" },
    };

    expect(servico.decidir(reacao, contexto)).toMatchObject({ exibir: false, tocar: false, atualizarChatInterno: true });
    expect(servico.decidir(removida, contexto)).toMatchObject({ exibir: false, tocar: false, atualizarChatInterno: true });
  });

  it("atualiza o cache sem criar alerta para evento sistêmico do grupo", () => {
    const evento: NotificacaoTempoReal = {
      tipo: "CHAT_INTERNO_MENSAGEM",
      dados: {
        conversaId: "conversa-1",
        mensagemId: "evento-sistema-1",
        remetenteId: "outro-usuario",
        tipo: null,
        conteudo: "João entrou no grupo",
        enviadoEm: "2026-09-10T12:00:00Z",
      },
    };

    expect(new ServicoDeNotificacoesTempoReal().decidir(evento, contexto)).toMatchObject({
      exibir: false,
      tocar: false,
      atualizarChatInterno: true,
    });
  });
});
