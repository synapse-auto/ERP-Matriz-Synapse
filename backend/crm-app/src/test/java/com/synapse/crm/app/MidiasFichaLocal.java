package com.synapse.crm.app;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

import javax.imageio.ImageIO;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.boot.SpringApplication;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import com.synapse.crm.sharedkernel.midia.ArmazenamentoDeMidia;

/** Validação visual opt-in: aplicação/DB/Redis/MinIO reais e provedor externo fake, só em test-classes. */
public final class MidiasFichaLocal {
    private MidiasFichaLocal() {}

    public static void main(String[] args) throws Exception {
        if (!"true".equals(System.getenv("MIDIAS_FICHA_LOCAL"))) {
            throw new IllegalStateException("fixture exclusiva para validação local opt-in");
        }
        var postgres = new PostgreSQLContainer<>("postgres:15-alpine").withDatabaseName("midias_ficha");
        var redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);
        var minio = new GenericContainer<>("cgr.dev/chainguard/minio@sha256:bd014394a80898e68c149f2311fdf8d5a2c2f3bb2c33b9327ae6d02b4b065ae1")
                .withEnv("MINIO_ROOT_USER", "midias-local")
                .withEnv("MINIO_ROOT_PASSWORD", "midias-local-somente-fixture")
                .withCommand("server", "/data").withExposedPorts(9000)
                .waitingFor(Wait.forHttp("/minio/health/ready").forPort(9000));
        postgres.start();
        redis.start();
        minio.start();
        Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration", "classpath:db/seed")
                .placeholders(Map.of("telefone_ddi_padrao", "55")).load().migrate();
        for (String pool : new String[] {"general", "chat"}) {
            System.setProperty("synapse.datasource." + pool + ".url", postgres.getJdbcUrl());
            System.setProperty("synapse.datasource." + pool + ".username", postgres.getUsername());
            System.setProperty("synapse.datasource." + pool + ".password", postgres.getPassword());
            System.setProperty("synapse.datasource." + pool + ".hikari.maximum-pool-size", "2");
        }
        System.setProperty("spring.data.redis.host", redis.getHost());
        System.setProperty("spring.data.redis.port", redis.getMappedPort(6379).toString());
        System.setProperty("synapse.midia.endpoint", "http://localhost:" + minio.getMappedPort(9000));
        System.setProperty("synapse.midia.url-publica", "http://localhost:" + minio.getMappedPort(9000));
        System.setProperty("synapse.midia.bucket", "midias-ficha-local");
        System.setProperty("synapse.midia.access-key", "midias-local");
        System.setProperty("synapse.midia.secret-key", "midias-local-somente-fixture");
        System.setProperty("synapse.seguranca.jwt-segredo", PostgresIT.SEGREDO_JWT_DE_TESTE);
        System.setProperty("synapse.canal.whatsapp.provedor", "fake");
        System.setProperty("synapse.agendamento.habilitado", "false");
        System.setProperty("synapse.saude.critica.monitoramento-habilitado", "false");
        System.setProperty("synapse.tempo-real.origens-permitidas", "http://localhost:3012");
        System.setProperty("synapse.seguranca.frontend-origem", "http://localhost:3012");
        var app = new SpringApplication(SynapseCrmApplication.class);
        app.addInitializers(contexto -> contexto.addBeanFactoryPostProcessor(factory ->
                ((BeanDefinitionRegistry) factory).removeBeanDefinition("armazenamentoDeMidiaFake")));
        var contexto = app.run("--spring.profiles.active=dev", "--server.address=127.0.0.1", "--server.port=8092");
        var jdbc = contexto.getBean(JdbcTemplate.class);
        if (!"midias_ficha".equals(jdbc.queryForObject("SELECT current_database()", String.class))) {
            throw new IllegalStateException("banco fora do fixture");
        }
        UUID ana = jdbc.queryForObject("SELECT id FROM usuario WHERE email='ana@dev.local'", UUID.class);
        for (int i = 1; i <= 2; i++) {
            UUID lead = UUID.fromString("d0c00000-0000-4000-8000-00000000000" + i);
            UUID atendimento = UUID.fromString("d0c00000-0000-4000-8000-0000000000a" + i);
            jdbc.update("INSERT INTO lead (id,nome,telefone,status_basico,atendente_responsavel_id,num_atendimentos)"
                    + " VALUES (?,?,?,'EM_ATENDIMENTO',?,1)", lead, "Cliente Arquivos Sintético " + i, "556199990088" + i, ana);
            jdbc.update("INSERT INTO atendimento (id,lead_id,canal_id,atendente_id,status,iniciado_em)"
                    + " VALUES (?,?,'ca000000-0000-4000-8000-000000000001',?,'EM_ATENDIMENTO',now())", atendimento, lead, ana);
        }
        UUID atendimento = UUID.fromString("d0c00000-0000-4000-8000-0000000000a1");
        var storage = contexto.getBean(ArmazenamentoDeMidia.class);
        var json = contexto.getBean(ObjectMapper.class);
        var imagem = new BufferedImage(160, 90, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < 160; x++) for (int y = 0; y < 90; y++) imagem.setRGB(x, y, (x << 16) | (y << 8));
        var png = new ByteArrayOutputStream();
        var jpg = new ByteArrayOutputStream();
        ImageIO.write(imagem, "png", png);
        ImageIO.write(imagem, "jpg", jpg);
        for (int i = 1; i <= 22; i++) registrar(jdbc, storage, json, atendimento, ana, "DOCUMENTO", "arquivo-%02d.txt".formatted(i), "text/plain", ("Documento sintético " + i).getBytes(StandardCharsets.UTF_8));
        registrar(jdbc, storage, json, atendimento, ana, "DOCUMENTO", "imagem-como-documento.jpg", "image/jpeg", jpg.toByteArray());
        registrar(jdbc, storage, json, atendimento, ana, "DOCUMENTO", "imagem-como-documento.png", "image/png", png.toByteArray());
        registrar(jdbc, storage, json, atendimento, ana, "IMAGEM", "foto-sintetica.png", "image/png", png.toByteArray());
        registrar(jdbc, storage, json, atendimento, ana, "AUDIO", "audio-sintetico.wav", "audio/wav", audio());
        registrar(jdbc, storage, json, atendimento, ana, "VIDEO", "video-sintetico.webm", "video/webm",
                Files.readAllBytes(Path.of("..", "frontend", "e2e", "fixtures", "video-ficha.webm")));
        jdbc.update("INSERT INTO mensagem (atendimento_id,remetente_tipo,tipo,midia_metadados,enviado_em,status_entrega)"
                + " VALUES (?,'LEAD','DOCUMENTO','{\"indisponivel\":true,\"nome\":\"ausente.png\"}',now(),'ENTREGUE')", atendimento);
        Files.createDirectories(Path.of("..", "output"));
        Files.writeString(Path.of("..", "output", "midias-local-runtime.json"),
                "{\"containerPostgres\":\"" + postgres.getContainerId() + "\",\"usuarioPostgres\":\"" + postgres.getUsername() + "\"}");
    }

    private static void registrar(JdbcTemplate jdbc, ArmazenamentoDeMidia storage, ObjectMapper json,
            UUID atendimento, UUID ana, String tipo, String nome, String mime, byte[] bytes) throws Exception {
        String referencia = storage.salvar(bytes, nome, mime);
        String metadados = json.writeValueAsString(Map.of("nome", nome, "mimetype", mime, "tamanho", bytes.length));
        jdbc.update("INSERT INTO mensagem (atendimento_id,remetente_tipo,remetente_id,tipo,midia_url,midia_metadados,enviado_em,status_entrega)"
                + " VALUES (?,'ATENDENTE',?,?::tipo_mensagem,?,?::jsonb,now(),'ENVIADO')", atendimento, ana, tipo, referencia, metadados);
    }

    private static byte[] audio() {
        var bytes = ByteBuffer.allocate(32044).order(ByteOrder.LITTLE_ENDIAN);
        bytes.put("RIFF".getBytes(StandardCharsets.US_ASCII)).putInt(32036).put("WAVEfmt ".getBytes(StandardCharsets.US_ASCII));
        bytes.putInt(16).putShort((short) 1).putShort((short) 1).putInt(16000).putInt(32000).putShort((short) 2).putShort((short) 16);
        bytes.put("data".getBytes(StandardCharsets.US_ASCII)).putInt(32000);
        return bytes.array();
    }
}
