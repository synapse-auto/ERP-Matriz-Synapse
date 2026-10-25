package com.synapse.crm.atendimento.infrastructure.canal;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.synapse.crm.atendimento.infrastructure.automacao.RepasseWebhookAutomacaoProperties;

/** Registro somente por opt-in; manter o modulo desligado nao exige segredo/configuracao. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "synapse.canal.z-api", name = "habilitado", havingValue = "true")
class ZApiEntradaConfig {
    @Bean
    @ConditionalOnProperty(prefix = "synapse.canal.z-api.webhook", name = "habilitado", havingValue = "true")
    ZApiWebhookTradutor tradutorZApi(ObjectMapper json, ZApiProperties api, ZApiWebhookProperties entrada,
            CanalProperties canal, RepasseWebhookAutomacaoProperties automacao) {
        entrada.validar();
        if (!api.configurada() || !api.instanciaId().equals(canal.numeroPrincipal()) || !"z-api".equals(canal.provedor())) {
            throw new IllegalArgumentException("Z_API_ENTRADA_CONFIGURACAO_DIVERGENTE");
        }
        if (entrada.repasseAutomacaoHabilitado() && (!automacao.configurado() || !automacao.temToken()
                || automacao.token().equals(entrada.segredo()))) {
            throw new IllegalArgumentException("Z_API_REPASSE_AUTOMACAO_SEM_AUTENTICACAO");
        }
        if (entrada.repasseAutomacaoHabilitado()) {
            try {
                var destino = java.net.URI.create(automacao.url());
                if (!"https".equalsIgnoreCase(destino.getScheme()) || destino.getHost() == null || destino.getUserInfo() != null)
                    throw new IllegalArgumentException();
            } catch (IllegalArgumentException e) { throw new IllegalArgumentException("Z_API_REPASSE_AUTOMACAO_EXIGE_HTTPS"); }
        }
        return new ZApiWebhookTradutor(json, api.instanciaId(), entrada.namespaceConfirmado(), entrada.repasseAutomacaoHabilitado());
    }
}
