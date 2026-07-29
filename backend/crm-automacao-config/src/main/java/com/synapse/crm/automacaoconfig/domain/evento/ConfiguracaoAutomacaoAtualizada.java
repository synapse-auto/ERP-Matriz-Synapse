package com.synapse.crm.automacaoconfig.domain.evento;

import java.time.Instant;
import java.util.UUID;

/**
 * Fato: um parametro de automacao mudou de valor.
 *
 * <p>Duas reacoes diferentes escutam este evento — invalidar o cache Redis e gravar em
 * {@code audit_log} — o motivo exato pelo qual CLAUDE.md pede Domain Events em vez de as duas
 * chamadas ficarem hardcoded dentro do caso de uso que fez a alteracao.
 */
public record ConfiguracaoAutomacaoAtualizada(
        String chave, String valorAntes, String valorDepois, UUID atualizadoPorId, Instant ocorridoEm) {}
