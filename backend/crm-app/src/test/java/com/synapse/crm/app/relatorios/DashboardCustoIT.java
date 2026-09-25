package com.synapse.crm.app.relatorios;

import static com.synapse.crm.app.seguranca.ApoioAutenticacao.EMAIL_ADMINISTRADOR;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.EMAIL_ANA;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.EMAIL_GESTOR;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.EMAIL_SUBGESTOR;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.SENHA_ADMINISTRADOR;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.SENHA_ATENDENTE;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.SENHA_GESTOR;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.SENHA_SUBGESTOR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementSetter;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import com.synapse.crm.app.PostgresIT;
import com.synapse.crm.app.seguranca.ApoioAutenticacao;

/** Medicao reproduzivel do ponto de entrada HTTP com PostgreSQL e Redis de teste. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
class DashboardCustoIT extends PostgresIT {

    private static final String URL = "/api/v1/dashboard/visao-geral?ano=2040&meses=8";

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private StringRedisTemplate redis;

    @MockitoSpyBean
    private JdbcTemplate jdbc;

    @Test
    void custoDeDuasLeiturasEquivalentes() throws Exception {
        String token = ApoioAutenticacao.login(http, EMAIL_GESTOR, SENHA_GESTOR).accessToken();
        Medicao primeira = medir(token, URL, "primeira");
        Medicao segunda = medir(token, URL, "segunda");

        assertThat(primeira.consultas()).isEqualTo(20);
        assertThat(segunda.consultas()).isEqualTo(4);
        assertThat(segunda.resposta().path("periodo")).isEqualTo(primeira.resposta().path("periodo"));
        assertThat(segunda.resposta().path("atendimentos"))
                .isEqualTo(primeira.resposta().path("atendimentos"));

        String chave = redis.keys("dashboard:visao-geral:v2:*").stream()
                .filter(valor -> !valor.endsWith(":lock"))
                .findFirst()
                .orElseThrow();
        assertThat(redis.getExpire(chave)).isPositive();
        redis.expire(chave, Duration.ofMillis(1));
        await().until(() -> !Boolean.TRUE.equals(redis.hasKey(chave)));
        assertThat(medir(token, URL, "apos-expiracao").consultas()).isEqualTo(20);
    }

    @Test
    void cacheIsolaFiltroUsuarioEPapelSemAutorizarAtendente() throws Exception {
        String gestor = ApoioAutenticacao.login(http, EMAIL_GESTOR, SENHA_GESTOR).accessToken();
        String subgestor = ApoioAutenticacao.login(http, EMAIL_SUBGESTOR, SENHA_SUBGESTOR).accessToken();
        String administrador = ApoioAutenticacao.login(
                        http, EMAIL_ADMINISTRADOR, SENHA_ADMINISTRADOR)
                .accessToken();
        String atendente = ApoioAutenticacao.login(http, EMAIL_ANA, SENHA_ATENDENTE).accessToken();

        assertThat(medir(gestor, URL, "gestor-frio").consultas()).isEqualTo(20);
        assertThat(medir(gestor, URL, "gestor-quente").consultas()).isEqualTo(4);
        assertThat(medir(gestor, "/api/v1/dashboard/visao-geral?ano=2040&meses=9", "outro-filtro")
                        .consultas())
                .isEqualTo(20);
        assertThat(medir(
                                gestor,
                                URL + "&origemInicio=2040-01-01&origemFim=2040-12-31",
                                "outra-coorte")
                        .consultas())
                .isEqualTo(21);
        assertThat(medir(subgestor, URL, "subgestor").consultas()).isEqualTo(20);
        assertThat(medir(administrador, URL, "administrador").consultas()).isEqualTo(20);

        Mockito.clearInvocations(jdbc);
        var negada = ApoioAutenticacao.comToken(
                http, atendente, HttpMethod.GET, URL, String.class);
        assertThat(negada.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(consultasJdbc()).isZero();
    }

    @Test
    void statusAoVivoMudaMesmoQuandoAgregadosEstaoEmCache() throws Exception {
        String token = ApoioAutenticacao.login(http, EMAIL_GESTOR, SENHA_GESTOR).accessToken();
        String presencaAnterior = jdbc.queryForObject(
                "SELECT status_presenca::text FROM usuario WHERE email=?", String.class, EMAIL_ANA);
        String presencaNova = "ONLINE".equals(presencaAnterior) ? "OFFLINE" : "ONLINE";
        try {
            Medicao antes = medir(token, URL, "antes-da-presenca");
            jdbc.update(
                    "UPDATE usuario SET status_presenca=CAST(? AS status_presenca) WHERE email=?",
                    presencaNova,
                    EMAIL_ANA);
            Medicao depois = medir(token, URL, "depois-da-presenca");
            assertThat(depois.consultas()).isEqualTo(4);
            assertThat(depois.resposta().path("atendimentos"))
                    .isEqualTo(antes.resposta().path("atendimentos"));
            long variacao = "ONLINE".equals(presencaNova) ? 1 : -1;
            assertThat(depois.resposta().at("/statusAoVivo/atendentesOnline/online").asLong())
                    .isEqualTo(antes.resposta().at("/statusAoVivo/atendentesOnline/online").asLong()
                            + variacao);
        } finally {
            jdbc.update(
                    "UPDATE usuario SET status_presenca=CAST(? AS status_presenca) WHERE email=?",
                    presencaAnterior,
                    EMAIL_ANA);
        }
    }

    @Test
    void duasRequisicoesSimultaneasCompartilhamOAgregadoFrio() throws Exception {
        String token = ApoioAutenticacao.login(http, EMAIL_GESTOR, SENHA_GESTOR).accessToken();
        CountDownLatch partida = new CountDownLatch(1);
        try (var executores = Executors.newFixedThreadPool(2)) {
            var primeira = executores.submit(() -> {
                partida.await();
                return ApoioAutenticacao.comToken(http, token, HttpMethod.GET, URL, String.class);
            });
            var segunda = executores.submit(() -> {
                partida.await();
                return ApoioAutenticacao.comToken(http, token, HttpMethod.GET, URL, String.class);
            });
            Mockito.clearInvocations(jdbc);
            partida.countDown();
            assertThat(primeira.get(30, TimeUnit.SECONDS).getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(segunda.get(30, TimeUnit.SECONDS).getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(consultasJdbc()).isEqualTo(24);
        }
    }

    private Medicao medir(String token, String url, String etapa) throws Exception {
        Mockito.clearInvocations(jdbc);
        long inicio = System.nanoTime();
        var resposta = ApoioAutenticacao.comToken(http, token, HttpMethod.GET, url, String.class);
        long nanos = System.nanoTime() - inicio;
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        long consultas = consultasJdbc();
        System.out.printf(
                "DASHBOARD_MEDICAO %s consultas=%d latencia_ms=%d%n",
                etapa, consultas, Duration.ofNanos(nanos).toMillis());
        return new Medicao(consultas, json.readTree(resposta.getBody()));
    }

    private long consultasJdbc() {
        return Mockito.mockingDetails(jdbc).getInvocations().stream()
                .filter(invocacao -> invocacao.getMethod().getName().equals("query")
                        && invocacao.getArguments().length >= 2
                        && invocacao.getArguments()[0] instanceof String
                        && ((invocacao.getMethod().getParameterCount() == 3
                                        && invocacao.getMethod().getParameterTypes()[1]
                                                == PreparedStatementSetter.class
                                        && invocacao.getMethod().getParameterTypes()[2]
                                                == ResultSetExtractor.class)
                                || (invocacao.getMethod().getParameterCount() == 2
                                        && invocacao.getMethod().getParameterTypes()[1]
                                                == ResultSetExtractor.class)))
                .count();
    }

    private record Medicao(long consultas, JsonNode resposta) {}
}
