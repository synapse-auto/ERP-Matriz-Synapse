package com.synapse.crm.atendimento.domain.finalizacaomassa;

/** Por que um item foi ignorado ou falhou. Codigos estaveis: aparecem no resultado e na API. */
public enum MotivoDoItemDeFinalizacao {
    /** Ja estava finalizado quando o worker chegou nele (outra pessoa, ou a automacao). */
    JA_FINALIZADO,
    /** Mudou de dono depois do pedido: a pessoa avisada seria a errada. */
    TRANSFERIDO,
    /** Chegou mensagem depois do pedido e a ultima atividade saiu da janela: conversa voltou a andar. */
    ATIVIDADE_POSTERIOR,
    /** O atendimento deixou de existir ou de ser acessivel. */
    INDISPONIVEL,
    /** Erro inesperado (so em FALHA). */
    ERRO_INESPERADO
}
