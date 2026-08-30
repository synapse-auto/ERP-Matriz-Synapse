package com.synapse.crm.atendimento.domain.mensagem;

/**
 * A origem nao pode virar resposta no WhatsApp: sem wamid, atendimento diferente, ou recusa de
 * politica do provedor. Nao e 404 — a mensagem existe e e visivel; o que falta e o vinculo externo.
 */
public class RespostaAoCanalIndevidaException extends RuntimeException {

    public RespostaAoCanalIndevidaException(String detalhe) {
        super(detalhe);
    }
}
