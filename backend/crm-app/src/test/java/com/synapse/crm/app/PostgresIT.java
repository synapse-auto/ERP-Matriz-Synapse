package com.synapse.crm.app;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Base dos testes de integracao: Postgres e Redis reais, compartilhados.
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
    private static final DockerImageName IMAGEM_REDIS = DockerImageName.parse("redis:7-alpine");

    protected static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(IMAGEM_POSTGRES);
    private static final GenericContainer<?> REDIS =
            new GenericContainer<>(IMAGEM_REDIS).withExposedPorts(6379);

    static {
        POSTGRES.start();
        REDIS.start();
    }

    @Autowired
    private StringRedisTemplate redis;

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
        // synapse.seguranca.jwt-segredo nao tem default em application.yml de
        // proposito: a aplicacao nao sobe sem ele. Os testes fornecem o seu.
        registro.add("synapse.seguranca.jwt-segredo", () -> SEGREDO_JWT_DE_TESTE);
        registro.add("spring.data.redis.host", REDIS::getHost);
        registro.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));

        // O container e compartilhado por todas as suites, e as que rodam com o perfil
        // dev aplicam R__seed_dev nele. Para uma suite sem esse perfil, essa migration
        // repetivel aparece como "aplicada, mas ausente das locations" e o Flyway
        // reprova a validacao. O sintoma depende da ORDEM em que as suites rodam, que e
        // o pior tipo de teste intermitente. Aqui — e so aqui — ignoramos ausentes.
        registro.add("spring.flyway.ignore-migration-patterns", () -> "*:missing");

        // O perfil dev configura o publisher da outbox para rodar a cada 200ms (bom para
        // ver PENDENTE virar ENVIADO rapido em desenvolvimento). Mas o Spring cacheia
        // ApplicationContext por assinatura de configuracao: uma suite que nao sobrescreve
        // este valor (a maioria) fica com um @Scheduled de verdade disparando em background
        // contra o MESMO Postgres compartilhado, mesmo depois de a suite terminar — e esse
        // publisher usa o adaptador real (meta-cloud), nao o fake. Quando outra suite (ex.:
        // CanalWhatsAppIT) insere uma linha na outbox, esse agendador de um contexto alheio
        // pode pega-la primeiro e falhar contra a API real. @DynamicPropertySource tem
        // prioridade maior que @TestPropertySource, entao isto vence o perfil dev em
        // qualquer suite — a suite que precisa do efeito do publisher chama o metodo
        // @Scheduled na mao (ver CanalWhatsAppIT, TempoRealIT), nunca espera o timer real.
        registro.add("synapse.canal.outbox.intervalo-ms", () -> "3600000");
        registro.add("synapse.canal.webhook.intervalo-ms", () -> "3600000");
    }

    /**
     * O container e singleton para o CI nao pagar uma inicializacao por classe, mas cada teste recebe
     * um cache vazio. Sem a limpeza, uma chave gravada por um ApplicationContext anterior poderia
     * esconder uma leitura do banco e tornar o resultado dependente da ordem das suites.
     */
    @BeforeEach
    void limparRedisCompartilhado() {
        // BootSemParticaoIT sobe o Spring manualmente e, por isso, nao recebe injecao no
        // objeto de teste. A aplicacao dele falha antes de usar Redis, exatamente no verificador
        // de particoes que a suite existe para exercitar.
        if (redis == null) {
            return;
        }
        redis.execute((RedisCallback<Void>) conexao -> {
            conexao.serverCommands().flushAll();
            return null;
        });
    }

    /** Segredo exclusivo dos testes. Longo o bastante para HMAC-SHA256. */
    protected static final String SEGREDO_JWT_DE_TESTE =
            "segredo-de-teste-do-synapse-crm-com-mais-de-32-caracteres";
}
