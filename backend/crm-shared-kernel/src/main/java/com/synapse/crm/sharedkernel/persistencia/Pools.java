package com.synapse.crm.sharedkernel.persistencia;

/**
 * Nomes dos beans de pool e de gerente de transacao.
 *
 * <p>Moram no shared kernel por necessidade, nao por gosto: os beans sao construidos em crm-app, mas
 * quem precisa <em>pedir</em> por eles sao os adaptadores dos modulos — e o teste de arquitetura
 * proibe qualquer modulo de importar {@code com.synapse.crm.app}. Sem estas constantes, cada
 * adaptador repetiria a string {@code "chatDataSource"} na mao, e um erro de digitacao devolveria
 * silenciosamente o pool geral: o bulkhead do caminho critico viraria decoracao sem nenhum sinal.
 *
 * <p>Sao apenas {@code String} — Java puro, sem Spring —, entao a regra de pureza do shared kernel
 * continua valendo.
 */
public final class Pools {

    /** Pool reservado ao caminho critico de mensagens (RNF-CRM-01). Nunca e o {@code @Primary}. */
    public static final String CHAT_DATA_SOURCE = "chatDataSource";

    /** Pool de todo o resto. E o {@code @Primary}: esquecer de qualificar degrada relatorio, nao chat. */
    public static final String GENERAL_DATA_SOURCE = "generalDataSource";

    /**
     * Gerente de transacao do pool do chat.
     *
     * <p>Todo caso de uso do caminho de mensagem declara este nome em {@code @Transactional}. Omitir
     * o nome pegaria o gerente {@code @Primary} — e a escrita da mensagem e a atualizacao do lead
     * cairiam em conexoes diferentes, ou seja, em transacoes diferentes.
     */
    public static final String CHAT_TRANSACTION_MANAGER = "chatTransactionManager";

    private Pools() {}
}
