package com.synapse.crm.app.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Identidade da API, configuravel por instancia sem condicional por cliente no codigo. */
@ConfigurationProperties("synapse.openapi")
public record OpenApiProperties(String title, String description, String version) {}
