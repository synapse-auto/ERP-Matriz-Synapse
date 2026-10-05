package com.synapse.crm.atendimento.application.encaminhamentodochat;

import java.time.Instant;
import java.util.UUID;

/**
 * Resultado de um encaminhamento do Chat Interno ao cliente. É também o "depois" da auditoria
 * ({@code @Auditable}): os campos permitidos estão em {@code SerializadorAuditavel}, e {@code leadId}
 * alimenta {@code audit_log.lead_id}.
 *
 * @param statusEntrega estado atual da mensagem externa ({@code PENDENTE} logo após o aceite)
 * @param reutilizado true quando a mesma {@code Idempotency-Key} devolveu um encaminhamento já feito
 */
public record EncaminhamentoDoChatParaCliente(
        UUID id,
        UUID usuarioId,
        UUID conversaId,
        UUID mensagemInternaId,
        UUID atendimentoId,
        UUID leadId,
        UUID mensagemExternaId,
        Instant mensagemExternaEnviadaEm,
        String tipo,
        boolean transferiuOLead,
        boolean conviteCriado,
        String statusEntrega,
        boolean reutilizado) {

    public EncaminhamentoDoChatParaCliente comoReutilizado(String statusAtual) {
        return new EncaminhamentoDoChatParaCliente(
                id, usuarioId, conversaId, mensagemInternaId, atendimentoId, leadId, mensagemExternaId,
                mensagemExternaEnviadaEm, tipo, transferiuOLead, conviteCriado, statusAtual, true);
    }
}
