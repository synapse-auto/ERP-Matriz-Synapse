package com.synapse.crm.campanhas.domain;

import java.util.EnumSet;
import java.util.Set;

/**
 * Ciclo de vida de uma campanha de template. Tipo fechado com transicoes explicitas: ninguem muda o
 * status por atribuicao, e uma transicao fora da tabela abaixo e erro, nao no-op.
 */
public enum StatusDaCampanha {
    RASCUNHO,
    AGENDADA,
    EM_ANDAMENTO,
    PAUSADA,
    CONCLUIDA,
    CANCELADA,
    /** O sistema parou a campanha (falha, template ou limite da Meta). Exige acao de um administrador. */
    PAUSADA_AUTOMATICAMENTE;

    private static final Set<StatusDaCampanha> TERMINAIS = EnumSet.of(CONCLUIDA, CANCELADA);

    public boolean terminal() {
        return TERMINAIS.contains(this);
    }

    /** Somente EM_ANDAMENTO envia; AGENDADA passa a EM_ANDAMENTO quando a hora chega. */
    public boolean envia() {
        return this == EM_ANDAMENTO;
    }

    public boolean pausada() {
        return this == PAUSADA || this == PAUSADA_AUTOMATICAMENTE;
    }

    public boolean podeIrPara(StatusDaCampanha destino) {
        return switch (this) {
            case RASCUNHO -> destino == AGENDADA || destino == EM_ANDAMENTO || destino == CANCELADA;
            case AGENDADA -> destino == EM_ANDAMENTO || destino == PAUSADA || destino == CANCELADA;
            case EM_ANDAMENTO ->
                destino == PAUSADA
                        || destino == PAUSADA_AUTOMATICAMENTE
                        || destino == CONCLUIDA
                        || destino == CANCELADA;
            case PAUSADA, PAUSADA_AUTOMATICAMENTE -> destino == EM_ANDAMENTO || destino == CANCELADA;
            case CONCLUIDA, CANCELADA -> false;
        };
    }
}
