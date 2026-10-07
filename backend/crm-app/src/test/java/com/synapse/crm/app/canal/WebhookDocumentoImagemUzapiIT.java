package com.synapse.crm.app.canal;

import static com.synapse.crm.app.seguranca.ApoioAutenticacao.EMAIL_ANA;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.EMAIL_BRUNO;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.SENHA_ATENDENTE;
import static org.assertj.core.api.Assertions.assertThat;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import javax.imageio.ImageIO;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import com.synapse.crm.app.PostgresIT;
import com.synapse.crm.app.seguranca.ApoioAutenticacao;
import com.synapse.crm.atendimento.infrastructure.webhook.ProcessadorDeWebhookEntrada;
import com.synapse.crm.sharedkernel.midia.ArmazenamentoDeMidia;

/**
 * POST autenticado, job de producao, PostgreSQL, MinIO e download JWT reais. Apenas a Uzapi/CDN
 * e representada por um servidor HTTP local; isto nao comprova disponibilidade na conta real.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Import(WebhookDocumentoImagemUzapiIT.StorageReal.class)
@TestPropertySource(properties = {
    "synapse.canal.whatsapp.provedor=uzapi-autotic",
    "synapse.canal.whatsapp.numero-principal=phone-documento-uzapi",
    "synapse.canal.whatsapp.token=token-documento-teste",
    "synapse.canal.whatsapp.versao-api=v1",
    "synapse.canal.whatsapp.webhook-secret=segredo-documento-teste",
    "synapse.canal.foto-perfil.habilitado=false",
    "synapse.automacao.repasse-webhook.url=http://127.0.0.1:1/nao-sera-chamado"
})
class WebhookDocumentoImagemUzapiIT extends PostgresIT {
    private static final String DESTINO = "phone-documento-uzapi";
    private static final String SEGREDO = "segredo-documento-teste";
    private static final String TELEFONE = "5561977700011";
    private static final GenericContainer<?> MINIO = new GenericContainer<>(DockerImageName.parse(
            "cgr.dev/chainguard/minio@sha256:bd014394a80898e68c149f2311fdf8d5a2c2f3bb2c33b9327ae6d02b4b065ae1"))
            .withEnv("MINIO_ROOT_USER", "minioadmin")
            .withEnv("MINIO_ROOT_PASSWORD", "minioadmin")
            .withCommand("server", "/data")
            .withExposedPorts(9000)
            .waitingFor(Wait.forHttp("/minio/health/ready").forPort(9000));
    private static HttpServer provedor;
    private static final List<String> requisicoes = new CopyOnWriteArrayList<>();
    private static final AtomicInteger statusResolvedor = new AtomicInteger(200);
    private static final AtomicInteger statusDownload = new AtomicInteger(200);
    private static final AtomicBoolean autenticacaoCorreta = new AtomicBoolean(true);
    private static byte[] arquivo;
    private static String mime;
    private static String midiaId;

    @Autowired private TestRestTemplate http;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper json;
    @Autowired private ProcessadorDeWebhookEntrada processador;
    @Autowired private ArmazenamentoDeMidia storage;
    @Autowired private CircuitBreakerRegistry breakers;
    private UUID canalId;
    private UUID credencialId;
    private UUID leadId;
    private UUID atendimentoId;
    private String entradaId;

    @DynamicPropertySource
    static void configurarInfra(DynamicPropertyRegistry registry) throws IOException {
        MINIO.start();
        provedor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        provedor.createContext("/", troca -> {
            String path = troca.getRequestURI().getPath();
            requisicoes.add(troca.getRequestMethod() + " " + path);
            boolean resolvedor = path.equals("/v1/" + midiaId);
            String authorization = troca.getRequestHeaders().getFirst("Authorization");
            if (resolvedor ? !"Bearer token-documento-teste".equals(authorization) : authorization != null) {
                autenticacaoCorreta.set(false);
            }
            byte[] body = resolvedor
                    ? ("{\"id\":\"" + midiaId + "\",\"url\":\"http://127.0.0.1:"
                            + provedor.getAddress().getPort() + "/arquivo/" + midiaId + "\"}")
                            .getBytes(StandardCharsets.UTF_8)
                    : arquivo;
            int status = resolvedor ? statusResolvedor.get() : statusDownload.get();
            troca.getResponseHeaders().set("Content-Type", resolvedor ? "application/json" : mime);
            troca.sendResponseHeaders(status, body.length == 0 ? -1 : body.length);
            if (body.length > 0) {
                troca.getResponseBody().write(body);
            }
            troca.close();
        });
        provedor.start();
        registry.add("synapse.canal.whatsapp.url-base", () -> "http://127.0.0.1:" + provedor.getAddress().getPort());
        registry.add("synapse.midia.endpoint", () -> "http://" + MINIO.getHost() + ":" + MINIO.getMappedPort(9000));
        registry.add("synapse.midia.url-publica", () -> "http://" + MINIO.getHost() + ":" + MINIO.getMappedPort(9000));
        registry.add("synapse.midia.bucket", () -> "documento-imagem-uzapi-teste");
        registry.add("synapse.midia.access-key", () -> "minioadmin");
        registry.add("synapse.midia.secret-key", () -> "minioadmin");
    }

    @TestConfiguration
    static class StorageReal {
        @Bean
        static BeanFactoryPostProcessor selecionarMinioReal() {
            return beans -> {
                beans.getBeanDefinition("armazenamentoDeMidiaFake").setPrimary(false);
                beans.getBeanDefinition("minioArmazenamentoDeMidia").setPrimary(true);
            };
        }
    }

    @BeforeEach
    void preparar() throws IOException {
        assertThat(storage.getClass().getSimpleName()).isEqualTo("MinioArmazenamentoDeMidia");
        requisicoes.clear();
        statusResolvedor.set(200);
        statusDownload.set(200);
        autenticacaoCorreta.set(true);
        breakers.circuitBreaker("canal-uzapi-autotic-midia").reset();
        arquivo = imagem("png");
        mime = "image/png";
        midiaId = "media-documento-" + UUID.randomUUID();
        entradaId = "DOC-IMAGEM-UZAPI-" + UUID.randomUUID();
        canalId = UUID.randomUUID();
        credencialId = UUID.randomUUID();
        leadId = UUID.randomUUID();
        atendimentoId = UUID.randomUUID();
        UUID ana = jdbc.queryForObject("SELECT id FROM usuario WHERE email=?", UUID.class, EMAIL_ANA);
        jdbc.update("INSERT INTO canal (id, nome, tipo) VALUES (?, 'Documento imagem teste', 'WHATSAPP')", canalId);
        jdbc.update("INSERT INTO canal_credencial (id, canal_id, numero, identificador_externo, token_ref, ativo)"
                + " VALUES (?, ?, '5543900000000', ?, 'token-teste', true)", credencialId, canalId, DESTINO);
        jdbc.update("INSERT INTO lead (id, nome, telefone, status_basico, atendente_responsavel_id)"
                + " VALUES (?, 'Documento imagem teste', ?, 'EM_ATENDIMENTO', ?)", leadId, TELEFONE, ana);
        jdbc.update("INSERT INTO atendimento (id, lead_id, canal_id, canal_credencial_id, status, atendente_id, iniciado_em)"
                + " VALUES (?, ?, ?, ?, 'EM_ATENDIMENTO', ?, now())", atendimentoId, leadId, canalId, credencialId, ana);
    }

    @AfterEach
    void limpar() {
        // O banco é compartilhado entre classes; remover apenas eventos desta fixture.
        jdbc.update("DELETE FROM outbox_evento WHERE payload->>'leadId'=? OR payload->>'atendimentoId'=?",
                leadId.toString(), atendimentoId.toString());
        jdbc.queryForList("SELECT midia_url FROM mensagem WHERE atendimento_id=? AND midia_url IS NOT NULL",
                String.class, atendimentoId).forEach(storage::remover);
        jdbc.update("DELETE FROM mensagem_id_externo WHERE atendimento_id=?", atendimentoId);
        jdbc.update("DELETE FROM mensagem WHERE atendimento_id=?", atendimentoId);
        jdbc.update("DELETE FROM atendimento WHERE id=?", atendimentoId);
        jdbc.update("DELETE FROM lead WHERE id=?", leadId);
        jdbc.update("DELETE FROM webhook_entrada WHERE id_externo=?", entradaId);
        jdbc.update("DELETE FROM mensagem_recebida_idempotencia WHERE wamid=?", entradaId);
        jdbc.update("DELETE FROM canal_credencial WHERE id=?", credencialId);
        jdbc.update("DELETE FROM canal WHERE id=?", canalId);
    }

    @AfterAll
    static void pararInfra() {
        provedor.stop(0);
        MINIO.stop();
    }

    static Stream<Arguments> anexos() {
        return Stream.of(
                Arguments.of("document", "Convenio.JPG", "jpeg", "image/jpeg", "DOCUMENTO"),
                Arguments.of("document", "foto.jpeg", "jpeg", "image/jpeg", "DOCUMENTO"),
                Arguments.of("document", "captura.png", "png", "image/png", "DOCUMENTO"),
                Arguments.of("image", "foto.jpg", "jpeg", "image/jpeg", "IMAGEM"),
                Arguments.of("image", "foto.png", "png", "image/png", "IMAGEM"),
                Arguments.of("document", "contrato.pdf", "pdf", "application/pdf", "DOCUMENTO"));
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("anexos")
    void webhookJobStorageHistoricoEDownloadPreservamTipoNomeEBytes(
            String tipo, String nome, String formato, String mimetype, String tipoCrm) throws IOException {
        arquivo = formato.equals("pdf") ? pdf() : imagem(formato);
        mime = mimetype;
        String payload = payload(tipo, nome, midiaId);
        assertThat(postar(payload, "incorreto")).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(contarMensagens()).isZero();
        assertThat(postar(payload, SEGREDO)).isEqualTo(HttpStatus.OK);
        assertThat(postar(payload, SEGREDO)).isEqualTo(HttpStatus.OK);
        processador.processarPendentes();
        assertThat(postar(payload, SEGREDO)).isEqualTo(HttpStatus.OK);
        processador.processarPendentes();

        assertThat(contarMensagens()).isEqualTo(1);
        assertThat(requisicoes).containsExactly("GET /v1/" + midiaId, "GET /arquivo/" + midiaId);
        assertThat(autenticacaoCorreta).isTrue();
        UUID mensagemId = mensagemId();
        JsonNode metadados = json.readTree(jdbc.queryForObject(
                "SELECT midia_metadados::text FROM mensagem WHERE id=?", String.class, mensagemId));
        assertThat(metadados.path("nome").asText()).isEqualTo(nome);
        assertThat(metadados.path("mimetype").asText()).isEqualTo(mimetype);
        assertThat(metadados.path("tamanho").asInt()).isEqualTo(arquivo.length);
        String referencia = jdbc.queryForObject("SELECT midia_url FROM mensagem WHERE id=?", String.class, mensagemId);
        assertThat(storage.baixar(referencia)).isEqualTo(arquivo);
        assertThat(jdbc.queryForObject("SELECT tipo::text FROM mensagem WHERE id=?", String.class, mensagemId))
                .isEqualTo(tipoCrm);
        String token = ApoioAutenticacao.login(http, EMAIL_ANA, SENHA_ATENDENTE).accessToken();
        for (int leitura = 0; leitura < 2; leitura++) {
            var historico = ApoioAutenticacao.comToken(http, token, HttpMethod.GET,
                    "/api/v1/atendimentos/" + atendimentoId + "/mensagens", String.class);
            assertThat(historico.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(json.readTree(historico.getBody()).path("mensagens").get(0).path("tipo").asText()).isEqualTo(tipoCrm);
            ResponseEntity<byte[]> download = baixar(token, leadId, mensagemId);
            assertThat(download.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(download.getBody()).isEqualTo(arquivo);
            assertThat(download.getHeaders().getContentType()).isEqualTo(MediaType.parseMediaType(mimetype));
            assertThat(download.getHeaders().getFirst("X-Content-Type-Options")).isEqualTo("nosniff");
            ContentDisposition disposition = download.getHeaders().getContentDisposition();
            assertThat(disposition.getType()).isEqualTo("attachment");
            assertThat(disposition.getFilename()).isEqualTo(nome);
        }
        String bruno = ApoioAutenticacao.login(http, EMAIL_BRUNO, SENHA_ATENDENTE).accessToken();
        assertThat(baixar(bruno, leadId, mensagemId).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(baixar(token, UUID.randomUUID(), mensagemId).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(http.getForEntity(rotaDownload(leadId, mensagemId), byte[].class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void resolvedor410RegistraDocumentoSemArquivoSemDownloadNemRetry() throws IOException {
        statusResolvedor.set(410);
        postar(payload("document", "foto.png", midiaId), SEGREDO);
        processador.processarPendentes();
        processador.processarPendentes();
        assertThat(contarMensagens()).isEqualTo(1);
        assertThat(requisicoes).containsExactly("GET /v1/" + midiaId);
        assertThat(jdbc.queryForObject("SELECT midia_url FROM mensagem WHERE id=?", String.class, mensagemId())).isNull();
        assertThat(jdbc.queryForObject("SELECT midia_metadados::jsonb->>'indisponivel' FROM mensagem WHERE id=?",
                String.class, mensagemId())).isEqualTo("true");
        assertThat(jdbc.queryForObject("SELECT processado_em IS NOT NULL FROM webhook_entrada WHERE id_externo=?",
                Boolean.class, entradaId)).isTrue();
    }

    @Test
    void download503RetentaDocumentoEPersisteUmaUnicaMensagemQuandoRecupera() throws IOException {
        statusDownload.set(503);
        postar(payload("document", "foto.png", midiaId), SEGREDO);
        processador.processarPendentes();
        assertThat(contarMensagens()).isZero();
        assertThat(jdbc.queryForObject("SELECT ultimo_erro FROM webhook_entrada WHERE id_externo=?", String.class, entradaId))
                .contains("etapa=download respondeu HTTP 503");
        statusDownload.set(200);
        jdbc.update("UPDATE webhook_entrada SET proxima_tentativa_em=now() WHERE id_externo=?", entradaId);
        processador.processarPendentes();
        processador.processarPendentes();
        assertThat(contarMensagens()).isEqualTo(1);
        assertThat(storage.baixar(jdbc.queryForObject("SELECT midia_url FROM mensagem WHERE id=?", String.class, mensagemId())))
                .isEqualTo(arquivo);
        assertThat(requisicoes).hasSize(4);
    }

    @Test
    void downloadVazioNaoViraArquivoValidoNemFalhaDefinitiva() throws IOException {
        arquivo = new byte[0];
        postar(payload("document", "foto.png", midiaId), SEGREDO);
        processador.processarPendentes();
        assertThat(contarMensagens()).isZero();
        assertThat(jdbc.queryForObject("SELECT ultimo_erro FROM webhook_entrada WHERE id_externo=?", String.class, entradaId))
                .contains("etapa=download respondeu sem bytes");
        assertThat(jdbc.queryForObject("SELECT esgotado_em IS NULL AND processado_em IS NULL"
                + " AND proxima_tentativa_em IS NOT NULL FROM webhook_entrada WHERE id_externo=?", Boolean.class, entradaId))
                .isTrue();
    }

    @Test
    void documentoSemReferenciaNaoUsaIdDaMensagemComoFallback() throws IOException {
        postar(payload("document", "foto.png", ""), SEGREDO);
        processador.processarPendentes();
        assertThat(contarMensagens()).isZero();
        assertThat(requisicoes).isEmpty();
        assertThat(jdbc.queryForObject("SELECT itens_descartados FROM webhook_entrada WHERE id_externo=?",
                Integer.class, entradaId)).isEqualTo(1);
    }

    private String payload(String tipo, String nome, String mediaId) throws IOException {
        var message = json.createObjectNode();
        message.put("from", TELEFONE).put("id", entradaId).put("type", tipo).put("timestamp", "1791381600");
        message.putObject(tipo).put("id", mediaId).put("mime_type", mime).put("filename", nome);
        var value = json.createObjectNode();
        value.putObject("metadata").put("phone_number_id", DESTINO);
        value.putArray("messages").add(message);
        var root = json.createObjectNode();
        root.put("object", "whatsapp_business_account");
        root.putArray("entry").addObject().putArray("changes").addObject().set("value", value);
        return json.writeValueAsString(root);
    }

    private HttpStatus postar(String payload, String segredo) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return HttpStatus.valueOf(http.postForEntity("/webhook/canal?secret={secret}",
                new HttpEntity<>(payload, headers), String.class, segredo).getStatusCode().value());
    }

    private int contarMensagens() {
        return jdbc.queryForObject("SELECT count(*) FROM mensagem WHERE atendimento_id=?", Integer.class, atendimentoId);
    }

    private UUID mensagemId() {
        return jdbc.queryForObject("SELECT id FROM mensagem WHERE atendimento_id=?", UUID.class, atendimentoId);
    }

    private ResponseEntity<byte[]> baixar(String token, UUID lead, UUID mensagem) {
        return ApoioAutenticacao.comToken(http, token, HttpMethod.GET, rotaDownload(lead, mensagem), byte[].class);
    }

    private String rotaDownload(UUID lead, UUID mensagem) {
        return "/api/v1/leads/" + lead + "/midias/" + mensagem + "/download";
    }

    private static byte[] imagem(String formato) throws IOException {
        var source = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        var output = new ByteArrayOutputStream();
        assertThat(ImageIO.write(source, formato, output)).isTrue();
        byte[] bytes = output.toByteArray();
        // A fixture e uma imagem completa, nao apenas uma assinatura nem um arquivo de cliente.
        assertThat(ImageIO.read(new ByteArrayInputStream(bytes)).getWidth()).isEqualTo(2);
        return bytes;
    }

    private static byte[] pdf() {
        var text = new StringBuilder("%PDF-1.4\n");
        String[] objects = {
            "<< /Type /Catalog /Pages 2 0 R >>",
            "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
            "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 100 100] /Resources << >> >>"
        };
        var offsets = new java.util.ArrayList<Integer>();
        for (int i = 0; i < objects.length; i++) {
            offsets.add(text.length());
            text.append(i + 1).append(" 0 obj\n").append(objects[i]).append("\nendobj\n");
        }
        int xref = text.length();
        text.append("xref\n0 4\n0000000000 65535 f \n");
        offsets.forEach(offset -> text.append("%010d 00000 n \n".formatted(offset)));
        text.append("trailer\n<< /Size 4 /Root 1 0 R >>\nstartxref\n").append(xref).append("\n%%EOF\n");
        return text.toString().getBytes(StandardCharsets.US_ASCII);
    }
}
