package com.synapse.crm.campanhas.application;

/**
 * Quem pode o que em campanhas. Leitura para a gestao; disparar, pausar, cancelar, alterar limite e
 * configuracao so o administrador: uma campanha mal feita e um incidente de qualidade do numero.
 */
public final class PermissoesDeCampanha {

    public static final String LEITURA = "hasAnyRole('GESTOR', 'SUBGESTOR', 'ADMINISTRADOR')";
    public static final String ESCRITA = "hasRole('ADMINISTRADOR')";

    private PermissoesDeCampanha() {}
}
