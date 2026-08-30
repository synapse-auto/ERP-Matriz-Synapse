package com.synapse.crm.core.application.lead.foto;

/**
 * O que aconteceu com a foto, do ponto de vista de quem chamou o contrato interno.
 *
 * <p>{@code INALTERADA} nao e erro nem "nada aconteceu por engano": e a resposta esperada do
 * polling, que reenvia a mesma foto de tempos em tempos. Ele significa "reconheci o hash, nao
 * escrevi no storage nem no banco".
 */
public enum ResultadoDaFotoDoLead {
    ATUALIZADA,
    INALTERADA,
    REMOVIDA
}
