package com.synapse.crm.atendimento.domain.mensagem;

/**
 * Ciclo de entrega da mensagem. Espelha o ENUM {@code status_entrega} do banco.
 *
 * <p>A ordem conta uma historia:
 *
 * <pre>
 *   PENDENTE --(provedor aceitou)--&gt; ENVIADO --&gt; ENTREGUE --&gt; LIDO
 *            --(desistiu)----------&gt; FALHOU
 * </pre>
 *
 * <p>Ate a E04 a mensagem enviada nascia {@link #ENVIADO}, antes de qualquer provedor ter aceitado. O
 * atendente via o tique de enviado numa mensagem que talvez nunca tivesse saido — e {@link #FALHOU}
 * nao existia, entao "nao saiu" era indistinguivel de "saiu e ninguem leu". Sao dois problemas
 * diferentes: um se resolve reenviando, o outro nao se resolve.
 */
public enum StatusEntrega {

    /** Gravada no CRM, ainda na outbox. Nenhum provedor viu esta mensagem. */
    PENDENTE,

    /** O provedor aceitou. A partir daqui a entrega e problema dele. */
    ENVIADO,

    ENTREGUE,

    LIDO,

    /** Esgotou as tentativas ou foi recusada em definitivo. Nao vai sair sozinha. */
    FALHOU;

    /** Se ainda ha o que fazer por esta mensagem — ou seja, se ela esta em transito. */
    public boolean emTransito() {
        return this == PENDENTE;
    }
}
