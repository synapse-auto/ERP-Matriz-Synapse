package com.synapse.crm.app.saude.application;

public record PoliticaDeMonitoramento(int falhasConsecutivasParaAlertar) {

    public PoliticaDeMonitoramento {
        if (falhasConsecutivasParaAlertar < 1) {
            throw new IllegalArgumentException("falhas consecutivas deve ser positivo");
        }
    }
}
