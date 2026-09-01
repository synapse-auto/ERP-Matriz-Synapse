package com.synapse.crm.equipe.domain.chat;

/** Eventos de sistema do chat interno de grupo (conteudo JSON em mensagem SISTEMA). */
public enum EventoDeSistemaChat {
    GRUPO_CRIADO,
    PARTICIPANTE_ADICIONADO,
    PARTICIPANTE_REMOVIDO,
    PARTICIPANTE_SAIU,
    NOME_ALTERADO
}
