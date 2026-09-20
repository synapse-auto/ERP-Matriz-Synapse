package com.synapse.crm.core.domain.evento;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Fato imutavel de que a ficha de um lead foi zerada pelo comando de teste {@code #resetgeral}.
 *
 * <p>Evento proprio, e nao {@link EtapaDoLeadAlterada}: aquele exige etapa nova e ator usuario, e
 * aqui a etapa vira nula e quem age e o sistema reagindo a uma mensagem — sem usuario autenticado,
 * como no {@code #reset}.
 *
 * @param etapaAnteriorId etapa antes de zerar; vazio se o lead ja estava sem etapa
 * @param tinhaResumo se havia resumo de IA antes. O texto do resumo nao entra no evento: a timeline
 *     registra que a ficha foi limpa, nao o conteudo que estava nela
 */
public record FichaDoLeadResetada(
        UUID leadId, Optional<UUID> etapaAnteriorId, boolean tinhaResumo, Instant ocorridoEm) {

    public FichaDoLeadResetada {
        Objects.requireNonNull(leadId, "lead do reset geral e obrigatorio");
        Objects.requireNonNull(etapaAnteriorId, "etapa anterior e obrigatoria (use Optional.empty)");
        Objects.requireNonNull(ocorridoEm, "instante do reset geral e obrigatorio");
    }
}
