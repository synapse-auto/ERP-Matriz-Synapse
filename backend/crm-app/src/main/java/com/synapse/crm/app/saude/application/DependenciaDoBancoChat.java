package com.synapse.crm.app.saude.application;

/** Permite evitar cinco timeouts em cascata quando o Postgres inteiro caiu. */
public enum DependenciaDoBancoChat {
    VERIFICA_O_BANCO,
    DEPENDE_DO_BANCO,
    INDEPENDENTE
}
