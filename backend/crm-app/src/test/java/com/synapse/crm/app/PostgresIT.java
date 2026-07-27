package com.synapse.crm.app;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base dos testes de integracao: um Postgres real, compartilhado.
 *
 * <p>O container e iniciado num bloco estatico e nunca parado explicitamente — e o padrao
 * "singleton container" do Testcontainers. Sem isso, cada classe de teste subiria o proprio
 * Postgres e o CI pagaria por N inicializacoes de banco. O Ryuk derruba o container ao fim da JVM.
 *
 * <p>O container escolhe uma porta livre aleatoria, entao o conflito com o Postgres nativo da
 * maquina (que ocupa a 5432) nao afeta os testes.
 *
 * <p>A versao acompanha a do docker-compose de proposito: testar contra uma versao diferente da que
 * roda em desenvolvimento e uma forma barata de descobrir incompatibilidade tarde demais.
 */
public abstract class PostgresIT {

    private static final String IMAGEM_POSTGRES = "postgres:15-alpine";

    protected static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(IMAGEM_POSTGRES);

    static {
        POSTGRES.start();
    }

    /**
     * Aponta os dois pools para o container.
     *
     * <p>{@code @ServiceConnection} nao serve aqui porque preenche {@code spring.datasource.*}, e
     * esta aplicacao nao usa esse prefixo: as conexoes vem de {@code synapse.datasource.*}, um
     * bloco por pool.
     */
    @DynamicPropertySource
    static void apontarPoolsParaOContainer(DynamicPropertyRegistry registro) {
        for (String pool : new String[] {"general", "chat"}) {
            registro.add("synapse.datasource." + pool + ".url", POSTGRES::getJdbcUrl);
            registro.add("synapse.datasource." + pool + ".username", POSTGRES::getUsername);
            registro.add("synapse.datasource." + pool + ".password", POSTGRES::getPassword);
        }
    }
}
