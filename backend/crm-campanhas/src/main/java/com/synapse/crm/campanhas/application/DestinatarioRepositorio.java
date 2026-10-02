package com.synapse.crm.campanhas.application;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.synapse.crm.campanhas.domain.MotivoDoDestinatario;
import com.synapse.crm.campanhas.domain.PoliticaDePausa;
import com.synapse.crm.campanhas.domain.StatusDoDestinatario;

/** Porta de {@code campanha_template_destinatario} para o ciclo de envio e para as atualizacoes por evento. */
public interface DestinatarioRepositorio {

    /**
     * Proximo pendente da campanha ({@code FOR UPDATE SKIP LOCKED}), com os dados do lead que o
     * mapeamento de variaveis enxerga. Vazio = nao ha mais ninguem para enviar.
     */
    Optional<Pendente> proximoPendente(UUID campanhaId);

    /** PENDENTE -> ENFILEIRADO. Falso = outro ciclo ja mexeu nele: nao conte nada. */
    boolean marcarEnfileirado(
            UUID destinatarioId, UUID mensagemId, Instant mensagemEnviadaEm, UUID atendimentoId, Instant agora);

    /** PENDENTE -> IGNORADO com o motivo. Falso = ja nao estava PENDENTE. */
    boolean marcarIgnorado(UUID destinatarioId, MotivoDoDestinatario motivo, Instant agora);

    /** Trava o destinatario dono da mensagem, se for de campanha; a maioria das mensagens nao e. */
    Optional<Alvo> bloquearPorMensagem(UUID mensagemId);

    void aplicarStatus(
            UUID destinatarioId,
            StatusDoDestinatario novo,
            MotivoDoDestinatario motivo,
            Integer codigoDeErro,
            Instant quando,
            AcaoDeConferencia conferencia);

    /** O que fazer com a marca de conferencia manual ao mudar o status. */
    enum AcaoDeConferencia {
        MANTER,
        SINALIZAR,
        LIMPAR
    }

    /** O motivo que o provedor (ou o CRM) gravou na mensagem que falhou. */
    Optional<ErroDaMensagem> erroDaMensagem(UUID mensagemId, Instant enviadoEm);

    /**
     * Marca a resposta do lead no ultimo destinatario enviado dentro da janela de resposta
     * ({@code campanhas.respondeu_janela_dias}, lida na propria consulta), uma vez so. Devolve a campanha
     * dele para quem chamou incrementar o contador. Roda a cada mensagem recebida: tem de ser uma consulta so.
     */
    Optional<UUID> registrarResposta(UUID leadId, Instant quando);

    /** ENFILEIRADO ha mais que o corte, sem confirmacao, vai para a conferencia. Devolve quantos. */
    int sinalizarParaConferencia(UUID campanhaId, Instant corte);

    /** Tira da conferencia (a pessoa verificou no provedor). Devolve se havia o que tirar. */
    boolean resolverConferencia(UUID campanhaId, UUID destinatarioId);

    PoliticaDePausa.Desfechos desfechosRecentes(UUID campanhaId, int janela);

    /** Campanhas com algum destinatario ENFILEIRADO ha mais que o corte: candidatas a reconciliar e conferir. */
    java.util.List<UUID> campanhasComEnfileiradoAntesDe(Instant corte, int limite);

    /** Os ENFILEIRADO/ENVIADO/ENTREGUE enfileirados ha mais que o corte, para reconciliar com a mensagem. */
    java.util.List<Alvo> aReconciliar(UUID campanhaId, Instant enfileiradosAntesDe, int limite);

    /** Status de entrega atual da mensagem, lido pela chave de particao. */
    Optional<StatusDoDestinatario> statusDaMensagem(UUID mensagemId, Instant enviadoEm);

    record Pendente(
            UUID id, UUID campanhaId, UUID leadId, String telefone, String nome, String empresa, String localizacao) {}

    record Alvo(
            UUID id,
            UUID campanhaId,
            UUID leadId,
            StatusDoDestinatario status,
            UUID mensagemId,
            Instant mensagemEnviadaEm,
            boolean emConferencia) {}

    record ErroDaMensagem(Integer codigo, String titulo) {}
}
