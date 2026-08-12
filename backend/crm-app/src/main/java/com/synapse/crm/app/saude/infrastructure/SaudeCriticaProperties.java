package com.synapse.crm.app.saude.infrastructure;

import java.time.Duration;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Limites operacionais externos ao codigo e ajustaveis por instancia. */
@Validated
@ConfigurationProperties("synapse.saude.critica")
public record SaudeCriticaProperties(
        @NotNull Duration filaSemConsumoMaximo,
        @NotNull Duration outboxIdadeMaxima,
        @Positive long outboxPendentesMaximo,
        @Positive int falhasConsecutivasParaAlertar) {}
