package com.synapse.crm.atendimento.infrastructure.canal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import com.synapse.crm.app.PostgresIT;
import com.synapse.crm.app.seguranca.ApoioAutenticacao;
import com.synapse.crm.atendimento.infrastructure.webhook.ProcessadorDeWebhookEntrada;

/** Meta HTTP autenticado -> worker -> PostgreSQL/MinIO -> download JWT; só transporte Meta simulado. */
@SpringBootTest(classes=com.synapse.crm.app.SynapseCrmApplication.class,webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
@Import(WordMetaRecebimentoIT.StorageReal.class)
@TestPropertySource(properties={"synapse.canal.whatsapp.provedor=meta-cloud",
        "synapse.canal.whatsapp.webhook-secret=segredo-meta-word-sintetico"})
class WordMetaRecebimentoIT extends PostgresIT {
    private static final String SEGREDO = "segredo-meta-word-sintetico";
    private static final GenericContainer<?> MINIO = new GenericContainer<>(DockerImageName.parse(
            "cgr.dev/chainguard/minio@sha256:bd014394a80898e68c149f2311fdf8d5a2c2f3bb2c33b9327ae6d02b4b065ae1"))
            .withEnv("MINIO_ROOT_USER","word-test-access").withEnv("MINIO_ROOT_PASSWORD","word-test-secret-storage")
            .withCommand("server","/data").withExposedPorts(9000).waitingFor(Wait.forHttp("/minio/health/ready").forPort(9000));
    static { MINIO.start(); }
    @DynamicPropertySource static void storage(DynamicPropertyRegistry r) {
        r.add("synapse.midia.endpoint",()->"http://"+MINIO.getHost()+":"+MINIO.getMappedPort(9000));
        r.add("synapse.midia.url-publica",()->"http://"+MINIO.getHost()+":"+MINIO.getMappedPort(9000));
        r.add("synapse.midia.bucket",()->"word-meta-test");
        r.add("synapse.midia.access-key",()->"word-test-access");
        r.add("synapse.midia.secret-key",()->"word-test-secret-storage");
    }
    @TestConfiguration(proxyBeanMethods=false) static class StorageReal {
        @Bean static BeanFactoryPostProcessor selecionarStorage() { return fabrica -> {
            fabrica.getBeanDefinition("armazenamentoDeMidiaFake").setPrimary(false);
            fabrica.getBeanDefinition("minioArmazenamentoDeMidia").setPrimary(true);
        }; }
    }
    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate db;
    @Autowired ObjectMapper json;
    @Autowired MetaCloudApiAdapter adapter;
    @Autowired ProcessadorDeWebhookEntrada processador;
    private RestClient anterior;
    private MockRestServiceServer servidor;
    private UUID lead, atendimento, canal, credencial;
    private String telefone, evento, destino;

    @BeforeEach void preparar() {
        anterior = (RestClient)ReflectionTestUtils.getField(adapter,"http");
        var builder = RestClient.builder().baseUrl("https://meta.example.test/v99");
        servidor = MockRestServiceServer.bindTo(builder).build();
        ReflectionTestUtils.setField(adapter,"http",builder.build());
        lead=UUID.randomUUID(); atendimento=UUID.randomUUID(); canal=UUID.randomUUID(); credencial=UUID.randomUUID();
        telefone="5561"+String.format("%09d",Math.floorMod(lead.getLeastSignificantBits(),1_000_000_000L));
        evento="wamid.word-"+lead; destino="meta-word-"+credencial;
        UUID ana=db.queryForObject("SELECT id FROM usuario WHERE email='ana@dev.local'",UUID.class);
        db.update("INSERT INTO canal(id,nome,tipo) VALUES (?,'Word sintético','WHATSAPP')",canal);
        db.update("INSERT INTO canal_credencial(id,canal_id,numero,identificador_externo,token_ref) VALUES (?,?,'5561999990000',?,'env://TESTE_META_WORD')",credencial,canal,destino);
        db.update("INSERT INTO lead(id,nome,telefone,status_basico,atendente_responsavel_id) VALUES (?,'Word sintético',?,'EM_ATENDIMENTO',?)",lead,telefone,ana);
        db.update("INSERT INTO atendimento(id,lead_id,canal_id,canal_credencial_id,atendente_id,status) VALUES (?,?,?,?,?,'EM_ATENDIMENTO')",atendimento,lead,canal,credencial,ana);
    }
    @AfterEach void limpar() {
        ReflectionTestUtils.setField(adapter,"http",anterior);
        db.update("DELETE FROM outbox_evento WHERE payload->>'atendimentoId'=?",atendimento.toString());
        db.update("DELETE FROM mensagem WHERE atendimento_id=?",atendimento);
        db.update("DELETE FROM atendimento WHERE id=?",atendimento);
        db.update("DELETE FROM lead WHERE id=?",lead);
        db.update("DELETE FROM webhook_entrada WHERE id_externo=?",evento);
        db.update("DELETE FROM mensagem_recebida_idempotencia WHERE wamid=?",evento);
        db.update("DELETE FROM canal_credencial WHERE id=?",credencial);
        db.update("DELETE FROM canal WHERE id=?",canal);
        servidor.verify();
    }

    @ParameterizedTest @ValueSource(strings={"doc","docx"})
    void callbackWordAssinadoComMimeGenericoRecarregaEBaixaSemDuplicar(String extensao) throws Exception {
        byte[] bytes;
        try(var entrada=getClass().getResourceAsStream(extensao.equals("doc")?"/word/vazio.doc":"/word/sintetico.docx")){bytes=entrada.readAllBytes();}
        servidor.expect(requestTo("https://meta.example.test/v99/media-word"))
                .andRespond(withSuccess("{\"url\":\"https://cdn.example.test/word\",\"mime_type\":\"application/octet-stream\"}",MediaType.APPLICATION_JSON));
        servidor.expect(requestTo("https://cdn.example.test/word")).andRespond(withSuccess(bytes,MediaType.APPLICATION_OCTET_STREAM));
        String nome="Relatório recebido."+extensao.toUpperCase();
        var mensagem=json.createObjectNode().put("id",evento).put("from",telefone).put("type","document").put("timestamp","1793100000");
        mensagem.putObject("document").put("id","media-word").put("mime_type","application/octet-stream").put("filename",nome);
        var value=json.createObjectNode(); value.putObject("metadata").put("phone_number_id",destino); value.putArray("messages").add(mensagem);
        var payload=json.createObjectNode(); payload.put("object","whatsapp_business_account"); payload.putArray("entry").addObject().putArray("changes").addObject().set("value",value);
        String corpo=payload.toString();
        var h=new HttpHeaders(); h.setContentType(MediaType.APPLICATION_JSON); h.set("X-Hub-Signature-256","sha256=00");
        assertThat(http.postForEntity("/webhook/canal",new HttpEntity<>(corpo,h),String.class).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        var mac=Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(SEGREDO.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
        h.set("X-Hub-Signature-256","sha256="+HexFormat.of().formatHex(mac.doFinal(corpo.getBytes(StandardCharsets.UTF_8))));
        for(int i=0;i<2;i++) assertThat(http.postForEntity("/webhook/canal",new HttpEntity<>(corpo,h),String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
        processador.processarPendentes(); processador.processarPendentes();
        var ids=db.queryForList("SELECT id FROM mensagem WHERE atendimento_id=?",UUID.class,atendimento); assertThat(ids).hasSize(1);
        String token=ApoioAutenticacao.login(http,ApoioAutenticacao.EMAIL_ANA,ApoioAutenticacao.SENHA_ATENDENTE).accessToken();
        for(int i=0;i<2;i++) {
            var download=ApoioAutenticacao.comToken(http,token,HttpMethod.GET,"/api/v1/leads/"+lead+"/midias/"+ids.getFirst()+"/download",byte[].class);
            assertThat(download.getStatusCode()).isEqualTo(HttpStatus.OK); assertThat(download.getBody()).containsExactly(bytes);
            assertThat(download.getHeaders().getContentDisposition().getFilename()).isEqualTo(nome);
            assertThat(download.getHeaders().getContentType().toString()).isEqualTo(extensao.equals("doc")?"application/msword":"application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        }
        String bruno=ApoioAutenticacao.login(http,ApoioAutenticacao.EMAIL_BRUNO,ApoioAutenticacao.SENHA_ATENDENTE).accessToken();
        assertThat(ApoioAutenticacao.comToken(http,bruno,HttpMethod.GET,"/api/v1/leads/"+lead+"/midias/"+ids.getFirst()+"/download",byte[].class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
