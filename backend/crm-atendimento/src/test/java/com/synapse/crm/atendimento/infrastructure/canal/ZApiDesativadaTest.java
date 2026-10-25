package com.synapse.crm.atendimento.infrastructure.canal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import com.synapse.crm.atendimento.domain.canal.CanalGateway;
import com.synapse.crm.atendimento.domain.canal.TradutorDeCanal;

class ZApiDesativadaTest {
    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(ZApiProperties.class)
    static class Propriedades {}

    private ApplicationContextRunner contexto(String ativo) {
        CanalGateway meta = mock(CanalGateway.class);
        when(meta.provedor()).thenReturn("meta-cloud");
        TradutorDeCanal entrada = mock(TradutorDeCanal.class);
        when(entrada.provedor()).thenReturn("meta-cloud");
        return new ApplicationContextRunner()
                .withUserConfiguration(Propriedades.class, ZApiAdapter.class, SeletorDeCanalGateway.class)
                .withBean(ObjectMapper.class, ObjectMapper::new)
                .withBean(CircuitBreakerRegistry.class, CircuitBreakerRegistry::ofDefaults)
                .withBean(ZApiMidiaSaida.class, () -> mock(ZApiMidiaSaida.class))
                .withBean(ZApiMidiaEntrada.class, () -> mock(ZApiMidiaEntrada.class))
                .withBean(CanalProperties.class, () -> new CanalProperties(ativo, null, null, null,
                        null, null, null, null, null, null, null))
                .withBean("meta", CanalGateway.class, () -> meta)
                .withBean("entradaMeta", TradutorDeCanal.class, () -> entrada);
    }

    @Test
    void configuracaoAusenteNaoInstanciaAdapterNemMudaCanalAtual() {
        contexto("meta-cloud").withPropertyValues("synapse.canal.z-api.credencial-id=").run(ctx -> {
            assertThat(ctx).hasNotFailed().doesNotHaveBean(ZApiAdapter.class);
            assertThat(ctx.getBean("canalAtivo", CanalGateway.class).provedor()).isEqualTo("meta-cloud");
        });
    }

    @Test
    void habilitarSaidaSemTrocarCanalNaoSubstituiMeta() {
        contexto("meta-cloud").withPropertyValues("synapse.canal.z-api.habilitado=true").run(ctx -> {
            assertThat(ctx).hasNotFailed().hasSingleBean(ZApiAdapter.class);
            assertThat(ctx.getBean("canalAtivo", CanalGateway.class).provedor()).isEqualTo("meta-cloud");
        });
    }

    @Test
    void tentativaDeSelecionarZApiSemEntradaFalhaFechadaSemInventarTradutor() {
        contexto("z-api").withPropertyValues("synapse.canal.z-api.habilitado=true").run(ctx -> {
            assertThat(ctx).hasFailed();
            assertThat(ctx.getStartupFailure()).hasRootCauseMessage(
                    "nenhum tradutor de webhook para o provedor z-api. Disponiveis: [meta-cloud]");
        });
    }
}
