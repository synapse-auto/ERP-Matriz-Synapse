package com.synapse.crm.atendimento.domain.finalizacaomassa;

/** Ciclo da operacao: PENDENTE (criada) -> EM_ANDAMENTO (worker pegou) -> CONCLUIDA. Nao ha volta. */
public enum StatusDaFinalizacaoEmMassa {
    PENDENTE,
    EM_ANDAMENTO,
    CONCLUIDA;

    public boolean ativa() {
        return this != CONCLUIDA;
    }
}
