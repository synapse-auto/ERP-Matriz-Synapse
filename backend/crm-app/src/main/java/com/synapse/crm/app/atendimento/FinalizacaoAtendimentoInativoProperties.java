package com.synapse.crm.app.atendimento;

import jakarta.validation.constraints.Positive;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Limite operacional do job; a regra de negócio de horas vive na configuração da instância. */
@Validated
@ConfigurationProperties("synapse.atendimento.finalizar-inativos")
public record FinalizacaoAtendimentoInativoProperties(@Positive int lote) {

    public FinalizacaoAtendimentoInativoProperties {
        if (lote < 1 || lote > 500) {
            throw new IllegalArgumentException("lote de finalização deve estar entre 1 e 500");
        }
    }
}
