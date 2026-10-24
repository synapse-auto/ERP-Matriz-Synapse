package com.synapse.crm.atendimento.infrastructure.canal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import com.synapse.crm.app.PostgresIT;
import com.synapse.crm.app.SynapseCrmApplication;
import com.synapse.crm.app.seguranca.ApoioAutenticacao;
import com.synapse.crm.atendimento.domain.canal.TradutorDeCanal;
import com.synapse.crm.atendimento.infrastructure.outbox.PublicadorDaOutbox;
import com.synapse.crm.sharedkernel.midia.ArmazenamentoDeMidia;
import com.synapse.crm.sharedkernel.midia.LimiteDeAnexoRepositorio;

/** API real -> outbox -> job agendado -> adaptador real -> HTTP simulado -> Postgres real. */
@SpringBootTest(classes = SynapseCrmApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
@Import(ZApiSaidaIT.TransporteDeTeste.class)
@TestPropertySource(properties = {
        "synapse.canal.whatsapp.provedor=z-api", "synapse.canal.whatsapp.numero-principal=instancia-teste",
        "synapse.canal.z-api.habilitado=false", "synapse.canal.outbox.intervalo-ms=3600000",
        "synapse.canal.webhook.intervalo-ms=3600000"})
class ZApiSaidaIT extends PostgresIT {
    private static final UUID CREDENCIAL = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final String PREFIXO = "ZAPI-SAIDA-";
    private static final String URL = "https://api.z-api.io/instances/instancia-teste/token/segredo-teste/send-text";
    private static final GenericContainer<?> MINIO = new GenericContainer<>(DockerImageName.parse(
            "cgr.dev/chainguard/minio@sha256:bd014394a80898e68c149f2311fdf8d5a2c2f3bb2c33b9327ae6d02b4b065ae1"))
            .withEnv("MINIO_ROOT_USER", "zapi-test-access")
            .withEnv("MINIO_ROOT_PASSWORD", "zapi-test-secret-storage")
            .withCommand("server", "/data").withExposedPorts(9000)
            .waitingFor(Wait.forHttp("/minio/health/ready").forPort(9000));
    static { MINIO.start(); }

    @DynamicPropertySource
    static void storageReal(DynamicPropertyRegistry registro) {
        registro.add("synapse.midia.endpoint", () -> "http://" + MINIO.getHost() + ":" + MINIO.getMappedPort(9000));
        registro.add("synapse.midia.url-publica", () -> "http://" + MINIO.getHost() + ":" + MINIO.getMappedPort(9000));
        registro.add("synapse.midia.bucket", () -> "zapi-saida-teste");
        registro.add("synapse.midia.access-key", () -> "zapi-test-access");
        registro.add("synapse.midia.secret-key", () -> "zapi-test-secret-storage");
    }
    @Autowired private JdbcTemplate jdbc;
    @Autowired private TestRestTemplate http;
    @Autowired private PublicadorDaOutbox publicador;
    @Autowired private Transporte transporte;
    @Autowired private ArmazenamentoDeMidia storage;
    @Autowired private ObjectMapper json;
    private UUID lead;
    private UUID atendimento;
    private HttpHeaders headers;

    record Transporte(MockRestServiceServer servidor, ZApiAdapter adapter) {}

    @TestConfiguration(proxyBeanMethods = false)
    static class TransporteDeTeste {
        @Bean static BeanFactoryPostProcessor selecionarStorageRealSomenteNesteContexto() {
            return fabrica -> {
                fabrica.getBeanDefinition("armazenamentoDeMidiaFake").setPrimary(false);
                fabrica.getBeanDefinition("minioArmazenamentoDeMidia").setPrimary(true);
            };
        }
        @Bean Transporte transporte(ObjectMapper json, CircuitBreakerRegistry breakers, CanalProperties canal,
                ArmazenamentoDeMidia storage, LimiteDeAnexoRepositorio limites) {
            RestClient.Builder cliente = RestClient.builder();
            MockRestServiceServer servidor = MockRestServiceServer.bindTo(cliente).build();
            return new Transporte(servidor, new ZApiAdapter(cliente,
                    new ZApiProperties(true, "instancia-teste", "segredo-teste", "cliente-teste", CREDENCIAL,
                            Duration.ofSeconds(10), 4096, 1), json, breakers, canal.numeroPrincipal(),
                    new ZApiMidiaSaida(storage, limites, json)));
        }
        @Bean ZApiAdapter saidaZApi(Transporte transporte) { return transporte.adapter(); }
        @Bean TradutorDeCanal entradaInexistenteSomenteNoTeste() {
            // Apenas permite subir o teste de saida. Nega toda assinatura; NAO e entrada implementada.
            TradutorDeCanal negado = mock(TradutorDeCanal.class);
            when(negado.provedor()).thenReturn("z-api");
            return negado;
        }
    }

    @BeforeEach
    void preparar() {
        limpar();
        transporte.servidor().reset();
        UUID ana = jdbc.queryForObject("SELECT id FROM usuario WHERE email = 'ana@dev.local'", UUID.class);
        UUID canal = UUID.randomUUID();
        lead = UUID.randomUUID();
        atendimento = UUID.randomUUID();
        jdbc.update("INSERT INTO canal (id, nome, tipo) VALUES (?, ?, 'WHATSAPP')", canal, PREFIXO + canal);
        jdbc.update("INSERT INTO canal_credencial (id, canal_id, numero, identificador_externo, token_ref, ativo) VALUES (?, ?, ?, ?, ?, true)",
                CREDENCIAL, canal, "5561999999999", "instancia-teste", "secret://teste/zapi");
        jdbc.update("INSERT INTO lead (id, nome, telefone, atendente_responsavel_id, status_basico, ultima_interacao_em, ultima_mensagem_do_lead_em) VALUES (?, ?, ?, ?, 'EM_ATENDIMENTO', now(), now())",
                lead, PREFIXO + lead, "5561999999999", ana);
        jdbc.update("INSERT INTO atendimento (id, lead_id, canal_credencial_id, atendente_id, status) VALUES (?, ?, ?, ?, 'EM_ATENDIMENTO')",
                atendimento, lead, CREDENCIAL, ana);
        headers = new HttpHeaders();
        headers.setBearerAuth(ApoioAutenticacao.login(http, ApoioAutenticacao.EMAIL_ANA, ApoioAutenticacao.SENHA_ATENDENTE).accessToken());
        headers.setContentType(MediaType.APPLICATION_JSON);
    }

    @AfterEach
    void depois() { transporte.servidor().verify(); limpar(); }

    @Test
    void cliqueDuplicadoSaiUmaVezGuardaIdsENaoExibeFalsaEntrega() {
        transporte.servidor().expect(requestTo(URL)).andExpect(header("Client-Token", "cliente-teste"))
                .andExpect(jsonPath("$.phone").value("5561999999999"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Olá 🧪\nsegunda linha")))
                .andRespond(withSuccess("{\"zaapId\":\"processo-z\",\"messageId\":\"mensagem-w\",\"id\":\"mensagem-w\"}", MediaType.APPLICATION_JSON));
        headers.set("Idempotency-Key", UUID.randomUUID().toString());
        UUID primeira = enviar();
        assertThat(enviar()).isEqualTo(primeira);
        publicador.publicarPendentes();
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(jdbc.queryForObject(
                "SELECT payload->'aceiteNaFila'->>'idDaMensagem' FROM outbox_evento WHERE payload->>'mensagemId' = ?",
                String.class, primeira.toString())).isEqualTo("mensagem-w"));
        assertThat(jdbc.queryForObject("SELECT status_entrega::text FROM mensagem WHERE id = ?", String.class, primeira)).isEqualTo("PENDENTE");
        assertThat(jdbc.queryForObject("SELECT payload->'aceiteNaFila'->>'idDoProcessamento' FROM outbox_evento WHERE payload->>'mensagemId' = ?",
                String.class, primeira.toString())).isEqualTo("processo-z");
        assertThat(jdbc.queryForObject("SELECT publicado_em IS NOT NULL FROM outbox_evento WHERE payload->>'mensagemId' = ?",
                Boolean.class, primeira.toString())).isTrue();
        // Simula lease vencido/reinicio do job e repeticao HTTP depois do aceite.
        // PENDENTE e estado da mensagem, nao autorizacao para novo despacho da outbox.
        jdbc.update("UPDATE outbox_evento SET proxima_tentativa_em = now() - interval '1 hour' WHERE payload->>'mensagemId' = ?",
                primeira.toString());
        assertThat(enviar()).isEqualTo(primeira);
        publicador.publicarPendentes();
        publicador.publicarPendentes();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM mensagem WHERE atendimento_id = ?", Integer.class, atendimento)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT status_entrega::text FROM mensagem WHERE id = ?", String.class, primeira)).isEqualTo("PENDENTE");
    }

    @Test
    void timeoutNoTransporteRealDoAdapterSuspendeOutboxSemReenviar() {
        transporte.servidor().expect(requestTo(URL)).andRespond(withException(new IOException("timeout sintetico")));
        UUID mensagem = enviar();
        publicador.publicarPendentes();
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(jdbc.queryForObject(
                "SELECT despacho_ambiguo_em IS NOT NULL FROM outbox_evento WHERE payload->>'mensagemId' = ?",
                Boolean.class, mensagem.toString())).isTrue());
        publicador.publicarPendentes();
        assertThat(jdbc.queryForObject("SELECT erro_entrega->>'titulo' FROM mensagem WHERE id = ?", String.class, mensagem))
                .isEqualTo("Não foi possível confirmar o envio");
    }

    @Test
    void usuarioSemAcessoNaoEnfileiraNemChamaFornecedor() {
        headers.setBearerAuth(ApoioAutenticacao.login(http, ApoioAutenticacao.EMAIL_BRUNO, ApoioAutenticacao.SENHA_ATENDENTE).accessToken());
        var resposta = http.postForEntity("/api/v1/atendimentos/mensagens", pedido(), String.class);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM mensagem WHERE atendimento_id = ?", Integer.class, atendimento)).isZero();
        publicador.publicarPendentes();
    }

    @ParameterizedTest
    @ValueSource(strings = {"imagem", "audio", "video", "documento"})
    void uploadAutorizadoPersisteNoMinioDespachaEBaixaSemPerderBytes(String tipo) throws Exception {
        byte[] bytes = switch (tipo) {
            case "imagem" -> png();
            case "audio" -> new byte[] {
                    0, 0, 0, 24, 'f', 't', 'y', 'p', 'M', '4', 'A', ' ',
                    0, 0, 0, 0, 'M', '4', 'A', ' ', 'i', 's', 'o', 'm',
                    0, 0, 0, 8, 'm', 'o', 'o', 'v',
                    0, 0, 0, 20, 'm', 'd', 'a', 't',
                    (byte) 0xff, (byte) 0xf1, 0x50, (byte) 0x80, 0x02, 0x3f, (byte) 0xfc,
                    0x21, 0x10, 0x04, 0x60, (byte) 0x8c};
            case "video" -> getClass().getResourceAsStream("/midia/chat-interno/video-validacao.mp4").readAllBytes();
            default -> "%PDF-1.4\nDocumento sintetico\n%%EOF".getBytes(StandardCharsets.US_ASCII);
        };
        String mime = switch (tipo) {
            case "imagem" -> "image/png";
            case "audio" -> "audio/mp4";
            case "video" -> "video/mp4";
            default -> "application/pdf";
        };
        String extensao = switch (tipo) { case "imagem" -> "png"; case "audio" -> "m4a"; case "video" -> "mp4"; default -> "pdf"; };
        String campo = switch (tipo) { case "imagem" -> "image"; case "audio" -> "audio"; case "video" -> "video"; default -> "document"; };
        String rota = tipo.equals("documento") ? "send-document/pdf" : "send-" + campo;
        esperarMidia(rota, campo, mime, bytes);
        HttpHeaders uploadHeaders = new HttpHeaders();
        uploadHeaders.setBearerAuth(headers.getFirst(HttpHeaders.AUTHORIZATION).substring(7));
        uploadHeaders.setContentType(MediaType.MULTIPART_FORM_DATA);
        uploadHeaders.set("Idempotency-Key", UUID.randomUUID().toString());
        var partes = new LinkedMultiValueMap<String, Object>();
        partes.add("arquivo", new ByteArrayResource(bytes) {
            @Override public String getFilename() { return "teste." + extensao; }
        });
        var resposta = http.postForEntity("/api/v1/atendimentos/" + atendimento + "/mensagens/midia",
                new HttpEntity<>(partes, uploadHeaders), Map.class);
        assertThat(resposta.getStatusCode()).withFailMessage("upload sintetico: %s", resposta.getBody()).isEqualTo(HttpStatus.OK);
        UUID mensagem = UUID.fromString(resposta.getBody().get("mensagemId").toString());
        String referencia = jdbc.queryForObject("SELECT midia_url FROM mensagem WHERE id = ?", String.class, mensagem);
        try (var entrada = storage.abrirLeitura(referencia)) { assertThat(entrada.readAllBytes()).isEqualTo(bytes); }
        publicador.publicarPendentes();
        assertThat(jdbc.queryForObject("SELECT publicado_em IS NOT NULL FROM outbox_evento WHERE payload->>'mensagemId' = ?",
                Boolean.class, mensagem.toString())).isTrue();
        conferirDownload(mensagem, bytes, mime, "teste." + extensao);
    }

    @ParameterizedTest
    @ValueSource(strings = {"image/png", "image/jpeg"})
    void imagemRecebidaComoDocumentoSegueDocumentoAoEncaminhar(String mime) throws Exception {
        byte[] bytes = mime.equals("image/png") ? png() : new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff, (byte) 0xe0, 0, 16, 'J', 'F', 'I', 'F', 0};
        String extensao = mime.equals("image/png") ? "png" : "jpg";
        String nome = "documento." + extensao;
        String referencia = storage.salvar(bytes, nome, mime);
        UUID origem = UUID.randomUUID();
        Instant horario = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        jdbc.update("INSERT INTO mensagem (id, atendimento_id, remetente_tipo, tipo, midia_url, midia_metadados, enviado_em, status_entrega) VALUES (?, ?, 'LEAD', 'DOCUMENTO', ?, ?::jsonb, ?, 'ENTREGUE')",
                origem, atendimento, referencia, json.writeValueAsString(Map.of("nome", nome, "mimetype", mime, "tamanho", bytes.length)), java.sql.Timestamp.from(horario));
        UUID origemAtendimento = atendimento;
        UUID ana = jdbc.queryForObject("SELECT id FROM usuario WHERE email = 'ana@dev.local'", UUID.class);
        lead = UUID.randomUUID();
        atendimento = UUID.randomUUID();
        jdbc.update("INSERT INTO lead (id, nome, telefone, atendente_responsavel_id, status_basico, ultima_interacao_em, ultima_mensagem_do_lead_em) VALUES (?, ?, '5561888888888', ?, 'EM_ATENDIMENTO', now(), now())",
                lead, PREFIXO + lead, ana);
        jdbc.update("INSERT INTO atendimento (id, lead_id, canal_credencial_id, atendente_id, status) VALUES (?, ?, ?, ?, 'EM_ATENDIMENTO')",
                atendimento, lead, CREDENCIAL, ana);
        esperarMidia("send-document/" + extensao, "document", mime, bytes);
        headers.set("Idempotency-Key", UUID.randomUUID().toString());
        var resposta = http.postForEntity("/api/v1/atendimentos/" + origemAtendimento + "/mensagens/" + origem + "/encaminhamentos?enviadoEm=" + horario,
                new HttpEntity<>(Map.of("destinoAtendimentoId", atendimento), headers), Map.class);
        assertThat(resposta.getStatusCode()).withFailMessage("encaminhamento sintetico: %s", resposta.getBody()).isEqualTo(HttpStatus.OK);
        UUID mensagem = UUID.fromString(resposta.getBody().get("mensagemId").toString());
        assertThat(jdbc.queryForObject("SELECT tipo::text FROM mensagem WHERE id = ?", String.class, mensagem)).isEqualTo("DOCUMENTO");
        publicador.publicarPendentes();
        conferirDownload(mensagem, bytes, mime, nome);
    }

    private void esperarMidia(String rota, String campo, String mime, byte[] bytes) {
        transporte.servidor().expect(requestTo(URL.replace("send-text", rota)))
                .andExpect(header("Client-Token", "cliente-teste"))
                .andExpect(pedido -> {
                    String valor = json.readTree(((MockClientHttpRequest) pedido).getBodyAsBytes()).path(campo).asText();
                    assertThat(valor).startsWith("data:" + mime + ";base64,");
                    assertThat(Base64.getDecoder().decode(valor.substring(valor.indexOf(',') + 1))).isEqualTo(bytes);
                }).andRespond(withSuccess("{\"zaapId\":\"processo-midia\",\"messageId\":\"mensagem-midia\"}", MediaType.APPLICATION_JSON));
    }

    private void conferirDownload(UUID mensagem, byte[] bytes, String mime, String nome) {
        String rota = "/api/v1/leads/" + lead + "/midias/" + mensagem + "/download";
        var resposta = http.exchange(rota, HttpMethod.GET, new HttpEntity<>(headers), byte[].class);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resposta.getBody()).isEqualTo(bytes);
        assertThat(resposta.getHeaders().getContentType().toString()).isEqualTo(mime);
        assertThat(resposta.getHeaders().getContentDisposition().getType()).isEqualTo("attachment");
        assertThat(resposta.getHeaders().getContentDisposition().getFilename()).isEqualTo(nome);
        assertThat(resposta.getHeaders().getFirst("X-Content-Type-Options")).isEqualTo("nosniff");
        HttpHeaders outro = new HttpHeaders();
        outro.setBearerAuth(ApoioAutenticacao.login(http, ApoioAutenticacao.EMAIL_BRUNO, ApoioAutenticacao.SENHA_ATENDENTE).accessToken());
        assertThat(http.exchange(rota, HttpMethod.GET, new HttpEntity<>(outro), byte[].class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    private static byte[] png() {
        return Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+juxkAAAAASUVORK5CYII=");
    }

    private UUID enviar() {
        var resposta = http.postForEntity("/api/v1/atendimentos/mensagens", pedido(), Map.class);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        return UUID.fromString(resposta.getBody().get("mensagemId").toString());
    }

    private HttpEntity<Map<String, Object>> pedido() {
        return new HttpEntity<>(Map.of("leadId", lead, "atendimentoId", atendimento, "conteudo", "Olá 🧪\nsegunda linha"), headers);
    }

    private void limpar() {
        // Banco descartavel dos ITs, sem endpoints/segredos/registro em ambiente externo.
        jdbc.update("DELETE FROM outbox_evento");
        for (String tabela : List.of("evento_timeline", "audit_log")) {
            jdbc.update("DELETE FROM " + tabela + " WHERE lead_id IN (SELECT id FROM lead WHERE nome LIKE ?)", PREFIXO + "%");
        }
        jdbc.update("DELETE FROM mensagem WHERE atendimento_id IN (SELECT a.id FROM atendimento a JOIN lead l ON l.id = a.lead_id WHERE l.nome LIKE ?)", PREFIXO + "%");
        jdbc.update("DELETE FROM mensagem_envio_idempotencia WHERE atendimento_id IN (SELECT a.id FROM atendimento a JOIN lead l ON l.id = a.lead_id WHERE l.nome LIKE ?)", PREFIXO + "%");
        jdbc.update("DELETE FROM atendimento WHERE lead_id IN (SELECT id FROM lead WHERE nome LIKE ?)", PREFIXO + "%");
        jdbc.update("DELETE FROM lead WHERE nome LIKE ?", PREFIXO + "%");
        jdbc.update("DELETE FROM canal_credencial WHERE id = ?", CREDENCIAL);
        jdbc.update("DELETE FROM canal WHERE nome LIKE ?", PREFIXO + "%");
    }
}
