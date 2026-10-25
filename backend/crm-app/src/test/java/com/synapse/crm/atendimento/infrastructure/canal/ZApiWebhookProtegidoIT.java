package com.synapse.crm.atendimento.infrastructure.canal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import okhttp3.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import com.synapse.crm.app.PostgresIT;
import com.synapse.crm.app.SynapseCrmApplication;
import com.synapse.crm.app.seguranca.ApoioAutenticacao;
import com.synapse.crm.atendimento.domain.evento.MensagemParaTempoReal;
import com.synapse.crm.atendimento.infrastructure.outbox.PublicadorDaOutbox;
import com.synapse.crm.atendimento.infrastructure.webhook.ProcessadorDeWebhookEntrada;
import com.synapse.crm.sharedkernel.midia.*;

/** Rota HTTP real + wiring de producao + PostgreSQL/MinIO. Somente o transporte CDN e simulado. */
@SpringBootTest(classes=SynapseCrmApplication.class, webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev") @RecordApplicationEvents @Import(ZApiWebhookProtegidoIT.TransporteCdn.class)
@TestPropertySource(properties={
    "synapse.canal.whatsapp.provedor=z-api", "synapse.canal.whatsapp.numero-principal=ZAPI-PROTEGIDA",
    "synapse.canal.z-api.habilitado=true", "synapse.canal.z-api.instancia-id=ZAPI-PROTEGIDA",
    "synapse.canal.z-api.token=token-api-teste", "synapse.canal.z-api.client-token=client-api-teste",
    "synapse.canal.z-api.credencial-id=33333333-3333-3333-3333-333333333333",
    "synapse.canal.z-api.webhook.habilitado=true", "synapse.canal.z-api.webhook.autenticacao=SEGREDO_URL",
    "synapse.canal.z-api.webhook.segredo=segredo-sintetico-exclusivo-webhook-com-mais-de-43-caracteres",
    "synapse.canal.z-api.webhook.limite-corpo-bytes=4096", "synapse.canal.z-api.webhook.namespace-status=WHATSAPP",
    // O servidor reconhece apenas o proxy local de teste. O filtro nunca le X-Forwarded-Proto cru.
    "server.forward-headers-strategy=NATIVE"})
class ZApiWebhookProtegidoIT extends PostgresIT {
    static final String SEGREDO="segredo-sintetico-exclusivo-webhook-com-mais-de-43-caracteres";
    static final String PREFIXO="ZAPI-PROT-";
    static final UUID CREDENCIAL=UUID.fromString("33333333-3333-3333-3333-333333333333");
    static final GenericContainer<?> MINIO=new GenericContainer<>(DockerImageName.parse("cgr.dev/chainguard/minio@sha256:bd014394a80898e68c149f2311fdf8d5a2c2f3bb2c33b9327ae6d02b4b065ae1"))
        .withEnv("MINIO_ROOT_USER","zapi-protegida-access").withEnv("MINIO_ROOT_PASSWORD","zapi-protegida-secret-storage")
        .withCommand("server","/data").withExposedPorts(9000).waitingFor(Wait.forHttp("/minio/health/ready").forPort(9000));
    static { MINIO.start(); }
    @DynamicPropertySource static void storage(DynamicPropertyRegistry r) {
        r.add("synapse.midia.endpoint",()->"http://"+MINIO.getHost()+":"+MINIO.getMappedPort(9000));
        r.add("synapse.midia.url-publica",()->"http://"+MINIO.getHost()+":"+MINIO.getMappedPort(9000));
        r.add("synapse.midia.bucket",()->"zapi-protegida"); r.add("synapse.midia.access-key",()->"zapi-protegida-access");
        r.add("synapse.midia.secret-key",()->"zapi-protegida-secret-storage");
    }
    @TestConfiguration(proxyBeanMethods=false) static class TransporteCdn {
        @Bean static BeanFactoryPostProcessor storageReal() { return f->{ f.getBeanDefinition("armazenamentoDeMidiaFake").setPrimary(false); f.getBeanDefinition("minioArmazenamentoDeMidia").setPrimary(true); }; }
        @Bean OkHttpClient cdnSimulada() { return mock(OkHttpClient.class); }
        @Bean @org.springframework.context.annotation.Primary ZApiMidiaEntrada midiaParaTeste(OkHttpClient cdnSimulada, LimiteDeAnexoRepositorio limites, DetectorDeTipoReal detector, CircuitBreakerRegistry breakers) {
            return new ZApiMidiaEntrada(cdnSimulada,new ZApiMidiaEntradaProperties(java.util.Set.of("cdn.exemplo.test"),1),limites,detector,breakers);
        }
    }
    @Autowired JdbcTemplate jdbc; @Autowired TestRestTemplate http; @Autowired ObjectMapper json;
    @Autowired ProcessadorDeWebhookEntrada processador; @Autowired PublicadorDaOutbox publicador;
    @Autowired OkHttpClient cdn; @Autowired ApplicationEvents eventos;
    UUID lead, atendimento; HttpHeaders headers;
    @BeforeEach void preparar() {
        limpar(); reset(cdn); lead=UUID.randomUUID(); atendimento=UUID.randomUUID(); UUID canal=UUID.randomUUID();
        UUID ana=jdbc.queryForObject("SELECT id FROM usuario WHERE email='ana@dev.local'",UUID.class);
        jdbc.update("INSERT INTO canal(id,nome,tipo) VALUES (?,?,'WHATSAPP')",canal,PREFIXO+canal);
        jdbc.update("INSERT INTO canal_credencial(id,canal_id,numero,identificador_externo,token_ref,ativo) VALUES (?,?,'5561999990000','ZAPI-PROTEGIDA','secret://teste',true)",CREDENCIAL,canal);
        jdbc.update("INSERT INTO lead(id,nome,telefone,atendente_responsavel_id,status_basico,ultima_interacao_em,ultima_mensagem_do_lead_em) VALUES (?,?,'5561999990000',?,'EM_ATENDIMENTO',now(),now())",lead,PREFIXO+lead,ana);
        jdbc.update("INSERT INTO atendimento(id,lead_id,canal_credencial_id,atendente_id,status) VALUES (?,?,?,?,'EM_ATENDIMENTO')",atendimento,lead,CREDENCIAL,ana);
        headers=new HttpHeaders(); headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON); headers.set("X-Forwarded-Proto","https");
    }
    @AfterEach void depois(){limpar();}
    @Test void segredoInvalidoInstanciaDivergenteJsonDuplicadoEExcessoNaoPersistem() {
        var recebido=recebido();
        for(String segredo:List.of("","errado")) assertThat(post(recebido.toString(),segredo)).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(post(recebido.deepCopy().put("instanceId","OUTRA").toString(),SEGREDO)).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(post("{\"instanceId\":\"ZAPI-PROTEGIDA\",\"instanceId\":\"OUTRA\"}",SEGREDO)).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(post(" ".repeat(4097),SEGREDO)).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM webhook_entrada",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_evento",Integer.class)).isZero();
        verifyNoInteractions(cdn);
    }
    @Test void textoPelaRotaRealDeduplicaEAtualizaTempoRealSemRepassarSegredo() {
        String payload=recebido().toString(); assertThat(post(payload,SEGREDO)).isEqualTo(HttpStatus.OK); assertThat(post(payload,SEGREDO)).isEqualTo(HttpStatus.OK);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM webhook_entrada",Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT payload::text FROM webhook_entrada",String.class)).doesNotContain(SEGREDO);
        verifyNoInteractions(cdn); processador.processarPendentes(); processador.processarPendentes();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM mensagem WHERE atendimento_id=?",Integer.class,atendimento)).isEqualTo(1);
        assertThat(eventos.stream(MensagemParaTempoReal.class).count()).isEqualTo(1);
    }
    @Test void httpSemProxyTlsNaoEnfileiraMesmoComSegredoCorreto() {
        headers.remove("X-Forwarded-Proto");
        assertThat(post(recebido().toString(),SEGREDO)).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM webhook_entrada",Integer.class)).isZero();
    }
    @Test void coletorSomenteLeituraNaoExportaConteudoTelefoneOuUrl() throws Exception {
        assertThat(post(recebido().toString(),SEGREDO)).isEqualTo(HttpStatus.OK);
        String script=java.nio.file.Files.readString(java.nio.file.Path.of("../../docs/diagnosticos/coletar-z-api-homologacao.sql"))
                .replace(":'instancia'","'ZAPI-PROTEGIDA'").replace(":'inicio'","'2000-01-01T00:00:00Z'").replace(":'fim'","'2100-01-01T00:00:00Z'");
        try(var conexao=jdbc.getDataSource().getConnection();var comando=conexao.createStatement()) {
            boolean resultado=comando.execute(script);int conjuntos=0;
            while(resultado || comando.getUpdateCount()!=-1) {
                if(resultado) try(var linhas=comando.getResultSet()) {
                    conjuntos++;
                    var metadados=linhas.getMetaData();
                    for(int c=1;c<=metadados.getColumnCount();c++) assertThat(metadados.getColumnLabel(c)).doesNotContain("payload","phone","url","token","conteudo");
                    while(linhas.next())for(int c=1;c<=metadados.getColumnCount();c++)if(linhas.getObject(c)!=null)
                        assertThat(linhas.getObject(c).toString()).doesNotContain(SEGREDO,"5561999990000","Unicode","https://");
                }
                resultado=comando.getMoreResults();
            }
            assertThat(conjuntos).isEqualTo(2);
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM webhook_entrada",Integer.class)).isEqualTo(1);
    }
    @ParameterizedTest @ValueSource(strings={"image","audio","video","document","document-jpeg","document-pdf"})
    void midiaPelaRotaRealVaiAoStoragePrivadoComDownloadAutorizado(String caso) throws Exception {
        String tipo=caso.startsWith("document")?"document":caso;
        byte[] bytes;
        if(caso.equals("video")) try(var f=getClass().getResourceAsStream("/midia/chat-interno/video-validacao.mp4")){bytes=f.readAllBytes();}
        else if(caso.equals("audio")) try(var f=getClass().getResourceAsStream("/z-api/audio-sintetico.ogg")){bytes=f.readAllBytes();}
        else if(caso.equals("document-pdf")) try(var f=getClass().getResourceAsStream("/z-api/documento-sintetico.pdf")){bytes=f.readAllBytes();}
        else if(caso.equals("document-jpeg")){var s=new java.io.ByteArrayOutputStream();javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(2,2,java.awt.image.BufferedImage.TYPE_INT_RGB),"jpg",s);bytes=s.toByteArray();}
        else bytes=Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+juxkAAAAASUVORK5CYII=");
        String url="https://cdn.exemplo.test/arquivo?assinatura=nao-persistir-na-mensagem";
        Call chamada=mock(Call.class);when(cdn.newCall(any())).thenReturn(chamada);
        when(chamada.execute()).thenReturn(new Response.Builder().request(new Request.Builder().url(url).build()).protocol(Protocol.HTTP_1_1).code(200).message("teste").body(ResponseBody.create(bytes,okhttp3.MediaType.get("application/octet-stream"))).build());
        var payload=recebido();payload.remove("text");payload.putObject(tipo).put(tipo+"Url",url).put("fileName","arquivo-do-teste");
        assertThat(post(payload.toString(),SEGREDO)).isEqualTo(HttpStatus.OK);verifyNoInteractions(cdn);
        processador.processarPendentes();
        UUID id=jdbc.queryForObject("SELECT id FROM mensagem WHERE atendimento_id=?",UUID.class,atendimento);
        assertThat(jdbc.queryForObject("SELECT tipo::text FROM mensagem WHERE id=?",String.class,id)).isEqualTo(switch(tipo){case "image"->"IMAGEM";case "audio"->"AUDIO";case "video"->"VIDEO";default->"DOCUMENTO";});
        headers.setBearerAuth(ApoioAutenticacao.login(http,ApoioAutenticacao.EMAIL_ANA,ApoioAutenticacao.SENHA_ATENDENTE).accessToken());
        var download=http.exchange("/api/v1/leads/"+lead+"/midias/"+id+"/download",HttpMethod.GET,new HttpEntity<>(headers),byte[].class);
        assertThat(download.getStatusCode()).isEqualTo(HttpStatus.OK);assertThat(download.getBody()).isEqualTo(bytes);
        assertThat(download.getHeaders().getFirst("Content-Disposition")).startsWith("attachment");
        headers.setBearerAuth(ApoioAutenticacao.login(http,"bruno@dev.local",ApoioAutenticacao.SENHA_ATENDENTE).accessToken());
        assertThat(http.exchange("/api/v1/leads/"+lead+"/midias/"+id+"/download",HttpMethod.GET,new HttpEntity<>(headers),String.class).getStatusCode()).isIn(HttpStatus.FORBIDDEN,HttpStatus.NOT_FOUND);
    }
    @Test void callbackPrecoceReconciliaSomenteIdentidadeExataESemRegredir() {
        UUID mensagem=UUID.randomUUID();
        jdbc.update("INSERT INTO mensagem(id,atendimento_id,remetente_tipo,tipo,conteudo,status_entrega,enviado_em) VALUES (?,?,'ATENDENTE','TEXTO','sintetico','PENDENTE',now())",mensagem,atendimento);
        var status=fixture("status.json"); status.putArray("ids").add("w-"+mensagem);status.put("status","READ");
        assertThat(post(status.toString(),SEGREDO)).isEqualTo(HttpStatus.OK);processador.processarPendentes();
        assertThat(jdbc.queryForObject("SELECT status_entrega::text FROM mensagem WHERE id=?",String.class,mensagem)).isEqualTo("PENDENTE");
        var correlacao=new com.synapse.crm.atendimento.domain.canal.IdentidadeDeMensagemDoCanal("z-api","ZAPI-PROTEGIDA","WHATSAPP","w-"+mensagem);
        // O mesmo vinculo que o publisher registra ao receber messageId; callback chegou antes dele.
        Instant enviadoEm=jdbc.queryForObject("SELECT enviado_em FROM mensagem WHERE id=?",java.sql.Timestamp.class,mensagem).toInstant();
        com.synapse.crm.sharedkernel.identidade.ContextoDeServico.executarComo("teste-vinculo",()->correlacionar.executar(List.of(new com.synapse.crm.atendimento.application.referencia.CorrelacionarMensagemDoCanalUseCase.Vinculo(correlacao,mensagem,enviadoEm,atendimento)),List.of()));
        assertThat(jdbc.queryForObject("SELECT status_entrega::text FROM mensagem WHERE id=?",String.class,mensagem)).isEqualTo("LIDO");
        status.put("status","RECEIVED");assertThat(post(status.toString(),SEGREDO)).isEqualTo(HttpStatus.OK);processador.processarPendentes();
        assertThat(jdbc.queryForObject("SELECT status_entrega::text FROM mensagem WHERE id=?",String.class,mensagem)).isEqualTo("LIDO");
    }
    @Autowired com.synapse.crm.atendimento.application.referencia.CorrelacionarMensagemDoCanalUseCase correlacionar;
    private org.springframework.http.HttpStatusCode post(String body,String segredo){return http.postForEntity("/webhook/z-api?secret="+segredo,new HttpEntity<>(body,headers),String.class).getStatusCode();}
    private ObjectNode recebido(){return fixture("received.json").put("messageId","recebida-"+lead);}
    private ObjectNode fixture(String nome){try(var f=getClass().getResourceAsStream("/z-api/"+nome)){var n=(ObjectNode)json.readTree(f);n.put("instanceId","ZAPI-PROTEGIDA");return n;}catch(Exception e){throw new IllegalStateException("FIXTURE_INVALIDA");}}
    private void limpar(){
        jdbc.update("DELETE FROM webhook_entrada");jdbc.update("DELETE FROM outbox_evento");jdbc.update("DELETE FROM mensagem_correlacao_canal WHERE instancia='ZAPI-PROTEGIDA'");
        for(String t:List.of("evento_timeline","audit_log"))jdbc.update("DELETE FROM "+t+" WHERE lead_id IN(SELECT id FROM lead WHERE nome LIKE ?)",PREFIXO+"%");
        jdbc.update("DELETE FROM mensagem WHERE atendimento_id IN(SELECT a.id FROM atendimento a JOIN lead l ON l.id=a.lead_id WHERE l.nome LIKE ?)",PREFIXO+"%");
        jdbc.update("DELETE FROM atendimento WHERE lead_id IN(SELECT id FROM lead WHERE nome LIKE ?)",PREFIXO+"%");jdbc.update("DELETE FROM lead WHERE nome LIKE ?",PREFIXO+"%");
        jdbc.update("DELETE FROM canal_credencial WHERE canal_id IN(SELECT id FROM canal WHERE nome LIKE ?)",PREFIXO+"%");jdbc.update("DELETE FROM canal WHERE nome LIKE ?",PREFIXO+"%");
    }
}
