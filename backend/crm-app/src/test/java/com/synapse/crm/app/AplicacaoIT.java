package com.synapse.crm.app;

import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;

import com.synapse.crm.app.config.DataSourceConfig;
import com.synapse.crm.app.config.SynapseProperties;

/**
 * Fumaca da fundacao: a instancia sobe contra um Postgres de verdade e responde ao liveness.
 *
 * <p>Postgres real via Testcontainers (ver {@link PostgresIT}), na mesma versao do docker-compose.
 * H2 mentiria justamente sobre o que importa aqui (tipos, JSONB, indices parciais), e o CLAUDE.md
 * pede banco real.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AplicacaoIT extends PostgresIT {

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private SynapseProperties propriedades;

    @Autowired
    @Qualifier(DataSourceConfig.GENERAL_DATA_SOURCE) private DataSource poolGeral;

    @Autowired
    @Qualifier(DataSourceConfig.CHAT_DATA_SOURCE) private DataSource poolChat;

    @Test
    @DisplayName("/health/liveness responde UP com a aplicacao no ar")
    void liveness_aplicacaoNoAr_respondeUp() {
        var resposta = http.getForEntity("/health/liveness", String.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resposta.getBody()).contains("UP");
    }

    @Test
    @DisplayName("/health/readiness responde UP quando o banco esta acessivel")
    void readiness_bancoAcessivel_respondeUp() {
        var resposta = http.getForEntity("/health/readiness", String.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resposta.getBody()).contains("UP");
    }

    @Test
    @DisplayName("bulkhead: chat e geral sao pools distintos, com limites proprios")
    void dataSources_aplicacaoNoAr_saoDoisPoolsIndependentes() {
        assertThat(poolGeral).isNotSameAs(poolChat);

        var geral = (HikariDataSource) poolGeral;
        var chat = (HikariDataSource) poolChat;

        assertThat(geral.getPoolName()).isEqualTo("synapse-geral");
        assertThat(chat.getPoolName()).isEqualTo("synapse-chat");
        assertThat(chat.getMaximumPoolSize()).isPositive();
        // O caminho critico falha rapido em vez de enfileirar o atendente.
        assertThat(chat.getConnectionTimeout()).isLessThan(geral.getConnectionTimeout());
    }

    @Test
    @DisplayName("a configuracao da instancia e carregada do bloco synapse")
    void propriedades_contextoIniciado_carregaBlocoDaInstancia() {
        assertThat(propriedades.tenant().codigo()).isNotBlank();
        assertThat(propriedades.tenant().timezone()).isNotBlank();
        assertThat(propriedades.features()).isNotEmpty();
    }
}
