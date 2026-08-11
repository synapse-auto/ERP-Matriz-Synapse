package com.synapse.crm.relatorios.domain.dashboard;

/** Parâmetros temporais que não descrevem uma janela válida do dashboard. */
public class FiltroDashboardInvalidoException extends RuntimeException {

    public FiltroDashboardInvalidoException(String mensagem) {
        super(mensagem);
    }
}
