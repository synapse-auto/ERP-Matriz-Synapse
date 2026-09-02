package com.synapse.crm.sharedkernel.avaliacao;

/**
 * Escala unica de CSAT do CRM (E128).
 *
 * <p>Vive no shared-kernel porque {@code crm-relatorios} e {@code crm-atendimento} precisam do
 * mesmo teto — e o modulo de relatorios nao depende de atendimento. Qualquer outro consumidor da
 * escala (dashboard {@code escalaMaxima}, CHECK da migration, OpenAPI) aponta para ca.
 */
public final class EscalaDeAvaliacao {

    public static final int NOTA_MINIMA = 0;
    public static final int NOTA_MAXIMA = 10;

    private EscalaDeAvaliacao() {}
}
