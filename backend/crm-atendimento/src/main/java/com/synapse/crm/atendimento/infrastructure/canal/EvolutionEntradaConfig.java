package com.synapse.crm.atendimento.infrastructure.canal;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.synapse.crm.atendimento.infrastructure.automacao.RepasseWebhookAutomacaoProperties;

@Configuration(proxyBeanMethods=false)
@ConditionalOnProperty(prefix="synapse.canal.evolution",name="habilitado",havingValue="true")
class EvolutionEntradaConfig {
    @Bean EvolutionWebhookTradutor tradutorEvolution(EvolutionProperties p,CanalProperties canal,ObjectMapper json,
            RepasseWebhookAutomacaoProperties automacao) {
        p.validar();
        if(!"evolution".equals(canal.provedor())||!p.instancia().equals(canal.numeroPrincipal()))
            throw new IllegalArgumentException("EVOLUTION_INSTANCIA_DIVERGENTE");
        if(p.repasseAutomacaoHabilitado()) {
            if(!automacao.configurado()||!automacao.temToken()||p.apiKey().equals(automacao.token())||p.segredoWebhook().equals(automacao.token()))
                throw new IllegalArgumentException("EVOLUTION_REPASSE_SEM_AUTENTICACAO");
            var destino=java.net.URI.create(automacao.url());
            if(!"https".equals(destino.getScheme())||destino.getHost()==null||destino.getUserInfo()!=null)
                throw new IllegalArgumentException("EVOLUTION_REPASSE_EXIGE_HTTPS");
        }
        return new EvolutionWebhookTradutor(json,p);
    }
}
