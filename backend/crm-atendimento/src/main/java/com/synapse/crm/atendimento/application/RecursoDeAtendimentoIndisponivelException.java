package com.synapse.crm.atendimento.application;

import java.util.UUID;

/**
 * O lead ou o atendimento nao existe — <em>ou</em> nao e de quem pediu.
 *
 * <p>Os dois casos usam esta mesma excecao de proposito, e a mensagem nao distingue um do outro. Uma
 * resposta diferente para "existe mas e do seu colega" contaria ao atendente que o lead existe e com
 * quem esta, que e exatamente a informacao que a RN-CRM-01 protege. Vira 404, nunca 403.
 */
public class RecursoDeAtendimentoIndisponivelException extends RuntimeException {

    public RecursoDeAtendimentoIndisponivelException(String recurso, UUID id) {
        super(recurso + " " + id + " nao encontrado");
    }
}
