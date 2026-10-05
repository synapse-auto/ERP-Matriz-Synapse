package com.synapse.crm.atendimento.domain.finalizacaomassa;

/** Resultado de um atendimento da lista congelada. So PENDENTE e processado; os demais sao finais. */
public enum StatusDoItemDeFinalizacao {
    PENDENTE,
    FINALIZADO,
    /** Nao precisava ou nao podia mais ser finalizado (ver {@link MotivoDoItemDeFinalizacao}). */
    IGNORADO,
    /** Erro inesperado ao finalizar; o atendimento segue como estava. */
    FALHA
}
