package com.synapse.crm.atendimento.domain.evento;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.synapse.crm.core.domain.timeline.OrigemEvento;

/**
 * Fatos consumados do modulo de atendimento.
 *
 * <p>Sao <b>fatos</b>, no passado: quem recebe nao pode recusar nem alterar o que aconteceu. Todos
 * sao consumidos com {@code @TransactionalEventListener(AFTER_COMMIT)}, entao existir um evento
 * significa que a transacao commitou.
 *
 * <p>Essa escolha e a regra de precedencia do produto aplicada ao codigo: timeline, auditoria e — na
 * E06 — a notificacao WebSocket sao <em>reacoes</em> ao registro da mensagem, nao parte dele. Um
 * listener lento dentro da transacao viraria latencia no caminho que nao pode ficar indisponivel
 * entre 08:00 e 18:30.
 *
 * <p>Selado pelo motivo de sempre: um evento novo quebra o build dos listeners que casam sobre este
 * tipo, em vez de passar despercebido e nunca ser tratado.
 */
public sealed interface EventoDeAtendimento {

    /** Todo evento sabe de que lead ele fala — e o que a timeline usa para agrupar. */
    UUID leadId();

    UUID atendimentoId();

    Instant ocorridoEm();

    /** O cliente mandou mensagem. */
    record MensagemRecebida(
            UUID leadId,
            UUID atendimentoId,
            UUID mensagemId,
            boolean abriuAtendimento,
            Instant ocorridoEm)
            implements EventoDeAtendimento {}

    /**
     * Alguem da equipe mandou mensagem.
     *
     * @param donoAnterior de quem era o lead antes de a RN-CRM-06 agir; vazio se nao tinha dono
     * @param transferiu se a RN-CRM-06 de fato mudou o dono
     */
    record MensagemEnviada(
            UUID leadId,
            String leadNome,
            UUID atendimentoId,
            UUID mensagemId,
            UUID remetenteId,
            Optional<UUID> donoAnterior,
            boolean transferiu,
            Instant ocorridoEm)
            implements EventoDeAtendimento {}

    /** A Automação respondeu pela IA; não é uma mensagem de atendente e não transfere o lead. */
    record MensagemEnviadaPelaAutomacao(
            UUID leadId, UUID atendimentoId, UUID mensagemId, Instant ocorridoEm)
            implements EventoDeAtendimento {}

    /**
     * O atendimento mudou de responsavel.
     *
     * @param paraAtendenteId {@code null} quando voltou para a IA
     * @param atorId usuario humano que executou; nulo quando {@code atorTipo} e AUTOMACAO
     */
    record AtendimentoTransferido(
            UUID leadId,
            String leadNome,
            UUID atendimentoId,
            UUID deAtendenteId,
            UUID paraAtendenteId,
            UUID atorId,
            OrigemEvento atorTipo,
            Instant ocorridoEm)
            implements EventoDeAtendimento {

        public AtendimentoTransferido {
            if (atorTipo == OrigemEvento.USUARIO && atorId == null) {
                throw new IllegalArgumentException("transferencia de usuario exige atorId");
            }
            if (atorTipo == OrigemEvento.AUTOMACAO && atorId != null) {
                throw new IllegalArgumentException("transferencia da Automacao nao possui usuario");
            }
        }
    }

    record AtendimentoFinalizado(
            UUID leadId, UUID atendimentoId, UUID quemFinalizou, Instant ocorridoEm)
            implements EventoDeAtendimento {}

    record PedidoEntradaSolicitado(UUID leadId, UUID atendimentoId, UUID solicitanteId,
            String solicitanteNome, UUID donoId, Instant ocorridoEm) implements EventoDeAtendimento {}

    record PedidoEntradaRespondido(UUID leadId, UUID atendimentoId, UUID solicitanteId,
            UUID donoId, boolean aprovado, Instant ocorridoEm) implements EventoDeAtendimento {}

    record ParticipanteSaiu(UUID leadId, UUID atendimentoId, UUID participanteId,
            Instant ocorridoEm) implements EventoDeAtendimento {}

    record ParticipanteEntrou(UUID leadId, UUID atendimentoId, UUID participanteId,
            Instant ocorridoEm) implements EventoDeAtendimento {}
}
