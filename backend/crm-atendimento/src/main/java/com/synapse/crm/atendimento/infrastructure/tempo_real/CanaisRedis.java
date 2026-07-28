package com.synapse.crm.atendimento.infrastructure.tempo_real;

import java.util.UUID;

/**
 * Nomes de canal do backplane Redis.
 *
 * <p>Um canal por atendimento — nao um canal unico para todo o sistema — porque o padrao de
 * assinatura do {@code RedisMessageListenerContainer} ({@code synapse:atendimento:*}) e o que permite
 * cada instancia ficar surda para atendimentos que ninguem conectado a ela esta olhando, sem ter que
 * filtrar mensagem por mensagem depois de recebida.
 */
final class CanaisRedis {

    private static final String PREFIXO = "synapse:atendimento:";

    /** Padrao de assinatura do container: casa com {@link #doAtendimento(UUID)} de qualquer id. */
    static final String PADRAO = PREFIXO + "*";

    /** Presenca e transversal: um canal so, sem dado de lead nenhum dentro. */
    static final String PRESENCA = "synapse:presenca";

    static String doAtendimento(UUID atendimentoId) {
        return PREFIXO + atendimentoId;
    }

    /** @return o atendimentoId embutido no nome do canal, ou {@code null} se nao for deste padrao */
    static UUID atendimentoDoCanal(String canal) {
        if (canal == null || !canal.startsWith(PREFIXO)) {
            return null;
        }
        try {
            return UUID.fromString(canal.substring(PREFIXO.length()));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private CanaisRedis() {}
}
