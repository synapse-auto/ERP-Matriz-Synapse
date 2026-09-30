package com.synapse.crm.atendimento.infrastructure.canal;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuracao dos templates do WhatsApp desta instancia.
 *
 * @param termoRestrito trecho do nome que reserva o template a administradores. Vem de
 *     {@code TEMPLATES_TERMO_RESTRITO}; o default ({@code interno}) mora no {@code application.yml}
 *     e no stack, nao aqui. Vazio falha no boot — sem termo, a regra nao teria o que proteger.
 */
@ConfigurationProperties("synapse.templates")
public record TemplatesProperties(String termoRestrito) {}
