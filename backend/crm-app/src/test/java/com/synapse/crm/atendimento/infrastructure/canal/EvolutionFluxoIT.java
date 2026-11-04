package com.synapse.crm.atendimento.infrastructure.canal;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

import java.time.Instant;
import java.util.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import okhttp3.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.boot.test.context.*;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.test.context.event.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import com.synapse.crm.app.*;
import com.synapse.crm.app.seguranca.ApoioAutenticacao;
import com.synapse.crm.atendimento.application.RepasseWebhookAutomacaoGateway;
import com.synapse.crm.atendimento.domain.evento.MensagemParaTempoReal;
import com.synapse.crm.atendimento.infrastructure.outbox.*;
import com.synapse.crm.atendimento.infrastructure.webhook.ProcessadorDeWebhookEntrada;

/** Entrada HTTP e API CRM reais, PostgreSQL/Redis/MinIO; somente transporte externo simulado. */
@SpringBootTest(classes=SynapseCrmApplication.class,webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev") @RecordApplicationEvents @Import(EvolutionFluxoIT.StorageReal.class)
@TestPropertySource(properties={"synapse.canal.whatsapp.provedor=evolution","synapse.canal.whatsapp.numero-principal=evo-teste",
    "synapse.canal.evolution.habilitado=true","synapse.canal.evolution.url-base=https://evolution.example.test",
    "synapse.canal.evolution.instancia=evo-teste","synapse.canal.evolution.api-key=chave-sintetica",
    "synapse.canal.evolution.credencial-id=44444444-4444-4444-4444-444444444444",
    "synapse.canal.evolution.segredo-webhook=segredo-sintetico-exclusivo-evolution-com-43-caracteres",
    "synapse.canal.evolution.repasse-automacao-habilitado=true","synapse.automacao.repasse-webhook.url=https://automacao.example.test/eventos",
    "synapse.automacao.repasse-webhook.token=repasse-exclusivo-sintetico","server.forward-headers-strategy=NATIVE"})
class EvolutionFluxoIT extends PostgresIT {
    static final String SEGREDO="segredo-sintetico-exclusivo-evolution-com-43-caracteres",PREFIXO="EVO-IT-";
    static final UUID CRED=UUID.fromString("44444444-4444-4444-4444-444444444444");
    static final GenericContainer<?> MINIO=new GenericContainer<>("cgr.dev/chainguard/minio@sha256:bd014394a80898e68c149f2311fdf8d5a2c2f3bb2c33b9327ae6d02b4b065ae1")
        .withEnv("MINIO_ROOT_USER","evolution-test-access").withEnv("MINIO_ROOT_PASSWORD","evolution-test-secret-storage")
        .withCommand("server","/data").withExposedPorts(9000).waitingFor(Wait.forHttp("/minio/health/ready").forPort(9000));
    static{MINIO.start();}
    @DynamicPropertySource static void storage(DynamicPropertyRegistry r){
        r.add("synapse.midia.endpoint",()->"http://"+MINIO.getHost()+":"+MINIO.getMappedPort(9000));
        r.add("synapse.midia.url-publica",()->"http://"+MINIO.getHost()+":"+MINIO.getMappedPort(9000));
        r.add("synapse.midia.bucket",()->"evolution-teste");r.add("synapse.midia.access-key",()->"evolution-test-access");r.add("synapse.midia.secret-key",()->"evolution-test-secret-storage");
    }
    @TestConfiguration(proxyBeanMethods=false) static class StorageReal{
        @Bean static BeanFactoryPostProcessor escolher(){return f->{f.getBeanDefinition("armazenamentoDeMidiaFake").setPrimary(false);f.getBeanDefinition("minioArmazenamentoDeMidia").setPrimary(true);};}
    }
    @Autowired JdbcTemplate jdbc;@Autowired TestRestTemplate http;@Autowired ObjectMapper json;
    @Autowired ProcessadorDeWebhookEntrada worker;@Autowired PublicadorDaOutbox publicador;@Autowired PublicadorDeRepasseWebhook repasses;
    @Autowired @Qualifier("evolutionAdapter") EvolutionAdapter adapter;@Autowired RepasseWebhookAutomacaoGateway automacao;@Autowired ApplicationEvents eventos;
    @org.springframework.boot.test.web.server.LocalServerPort int porta;
    UUID lead,atendimento;HttpHeaders cabecalhos;OkHttpClient transporte,anterior;Call chamada;RestClient repasseAnterior;MockRestServiceServer destino;
    @BeforeEach void preparar()throws Exception {
        limpar();lead=UUID.randomUUID();atendimento=UUID.randomUUID();UUID canal=UUID.randomUUID();
        UUID ana=jdbc.queryForObject("SELECT id FROM usuario WHERE email='ana@dev.local'",UUID.class);
        jdbc.update("INSERT INTO canal(id,nome,tipo) VALUES (?,?,'WHATSAPP')",canal,PREFIXO+canal);
        jdbc.update("INSERT INTO canal_credencial(id,canal_id,numero,identificador_externo,token_ref,ativo) VALUES (?,?,'5561999990000','evo-teste','env://EVOLUTION_API_KEY',true)",CRED,canal);
        jdbc.update("INSERT INTO lead(id,nome,telefone,atendente_responsavel_id,status_basico,ultima_interacao_em,ultima_mensagem_do_lead_em) VALUES (?,?,'5561999990000',?,'EM_ATENDIMENTO',now(),now())",lead,PREFIXO+lead,ana);
        jdbc.update("INSERT INTO atendimento(id,lead_id,canal_credencial_id,atendente_id,status) VALUES (?,?,?,?,'EM_ATENDIMENTO')",atendimento,lead,CRED,ana);
        cabecalhos=new HttpHeaders();cabecalhos.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);cabecalhos.set("X-Forwarded-Proto","https");cabecalhos.set(EvolutionWebhookProtegidoFilter.HEADER,SEGREDO);
        transporte=mock(OkHttpClient.class);chamada=mock(Call.class);when(transporte.newCall(any())).thenReturn(chamada);
        anterior=(OkHttpClient)ReflectionTestUtils.getField(adapter,"http");ReflectionTestUtils.setField(adapter,"http",transporte);
        repasseAnterior=(RestClient)ReflectionTestUtils.getField(automacao,"http");var builder=RestClient.builder();destino=MockRestServiceServer.bindTo(builder).build();ReflectionTestUtils.setField(automacao,"http",builder.build());
    }
    @AfterEach void depois(){ReflectionTestUtils.setField(adapter,"http",anterior);ReflectionTestUtils.setField(automacao,"http",repasseAnterior);destino.verify();limpar();}
    ObjectNode recebido(){var n=json.createObjectNode().put("event","messages.upsert").put("instance","evo-teste").put("apikey","NAO_PERSISTIR_CHAVE").put("destination","https://privado/?segredo=NAO_PERSISTIR");var d=n.putObject("data").put("messageTimestamp",Instant.now().getEpochSecond()).put("pushName","Contato sintetico");d.putObject("key").put("id","w-"+lead).put("fromMe",false).put("remoteJid","5561999990000@s.whatsapp.net");d.putObject("message").put("conversation","Texto sintetico");return n;}
    void receber(ObjectNode payload){assertThat(http.postForEntity("/webhook/evolution",new HttpEntity<>(payload.toString(),cabecalhos),String.class).getStatusCode()).isEqualTo(HttpStatus.OK);}
    void esperarRepasse(){destino.expect(requestTo("https://automacao.example.test/eventos")).andExpect(header("X-Synapse-Webhook-Token","repasse-exclusivo-sintetico")).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("NAO_PERSISTIR")))).andRespond(withSuccess());}
    @ParameterizedTest @ValueSource(strings={"texto","botao","lista","template","native"})
    void callbackRealDeduplicaPersisteAtualizaEventoERepassa(String caso){
        var n=recebido();var m=(ObjectNode)n.path("data").path("message");
        if(!caso.equals("texto")){m.removeAll();
            switch(caso){
                case "botao"->m.putObject("buttonsResponseMessage").put("selectedButtonId","1").put("selectedDisplayText","Escolha sintetica");
                case "template"->m.putObject("templateButtonReplyMessage").put("selectedId","3").put("selectedDisplayText","Escolha sintetica");
                case "native"->{var escolha=m.putObject("interactiveResponseMessage");escolha.putObject("body").put("text","Escolha sintetica");escolha.putObject("nativeFlowResponseMessage").put("name","quick_reply").put("paramsJson","{\"campoOpaco\":\"opcao\"}");}
                default->m.putObject("listResponseMessage").put("title","Escolha sintetica").putObject("singleSelectReply").put("selectedRowId","2");
            }
            String campo=switch(caso){case "botao"->"buttonsResponseMessage";case "template"->"templateButtonReplyMessage";case "native"->"interactiveResponseMessage";default->"listResponseMessage";};
            ((ObjectNode)m.path(campo)).putObject("contextInfo").put("stanzaId","original-ainda-nao-local");
        }
        receber(n);receber(n);assertThat(jdbc.queryForObject("SELECT count(*) FROM webhook_entrada",Integer.class)).isEqualTo(1);verifyNoInteractions(transporte);
        worker.processarPendentes();worker.processarPendentes();assertThat(jdbc.queryForObject("SELECT count(*) FROM mensagem WHERE atendimento_id=?",Integer.class,atendimento)).isEqualTo(1);
        assertThat(eventos.stream(MensagemParaTempoReal.class).filter(e->e.atendimentoId().equals(atendimento)).count()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT payload::text FROM webhook_entrada",String.class)).doesNotContain("NAO_PERSISTIR","apikey","destination");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM mensagem_correlacao_canal WHERE provedor='evolution'",Integer.class)).isEqualTo(1);
        if(!caso.equals("texto"))assertThat(jdbc.queryForObject("SELECT midia_metadados::text FROM mensagem WHERE atendimento_id=?",String.class,atendimento)).contains("referenciaExterna","original-ainda-nao-local");
        esperarRepasse();repasses.publicarPendentes();repasses.publicarPendentes();
    }
    @ParameterizedTest @ValueSource(strings={"image","audio","video","pdf","doc","docx","document-image"})
    void midiaPelaEntradaRealPersisteBytesENomeNoStorageComDownloadAutorizado(String caso)throws Exception{
        byte[] bytes;String campo,nome="Arquivo sintetico",mime;
        switch(caso){
            case "audio"->{campo="audioMessage";mime="audio/ogg";try(var f=getClass().getResourceAsStream("/z-api/audio-sintetico.ogg")){bytes=f.readAllBytes();}}
            case "video"->{campo="videoMessage";mime="video/mp4";try(var f=getClass().getResourceAsStream("/midia/chat-interno/video-validacao.mp4")){bytes=f.readAllBytes();}}
            case "doc","docx","pdf"->{campo="documentMessage";mime=caso.equals("pdf")?"application/pdf":caso.equals("doc")?"application/msword":"application/vnd.openxmlformats-officedocument.wordprocessingml.document";nome="Relatorio com acento."+caso;try(var f=getClass().getResourceAsStream(caso.equals("pdf")?"/z-api/documento-sintetico.pdf":caso.equals("doc")?"/word/vazio.doc":"/word/sintetico.docx")){bytes=f.readAllBytes();}}
            default->{campo=caso.equals("image")?"imageMessage":"documentMessage";mime="image/png";nome="Imagem como documento.PNG";bytes=Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+juxkAAAAASUVORK5CYII=");}
        }
        String resposta=json.createObjectNode().put("base64",Base64.getEncoder().encodeToString(bytes)).put("mimetype",mime).toString();when(chamada.execute()).thenReturn(new Response.Builder().request(new Request.Builder().url("https://evolution.example.test").build()).protocol(Protocol.HTTP_1_1).code(200).message("sintetico").body(ResponseBody.create(resposta,MediaType.get("application/json"))).build());
        var n=recebido();var m=(ObjectNode)n.path("data").path("message");m.removeAll();m.putObject(campo).put("mimetype",mime).put("fileName",nome).put("url","https://nao-acessar.example.test/privado");receber(n);verifyNoInteractions(transporte);worker.processarPendentes();
        UUID id=jdbc.queryForObject("SELECT id FROM mensagem WHERE atendimento_id=?",UUID.class,atendimento);
        assertThat(jdbc.queryForObject("SELECT tipo::text FROM mensagem WHERE id=?",String.class,id)).isEqualTo(campo.equals("documentMessage")?"DOCUMENTO":campo.equals("imageMessage")?"IMAGEM":campo.equals("audioMessage")?"AUDIO":"VIDEO");
        autenticar(ApoioAutenticacao.EMAIL_ANA);var download=http.exchange("/api/v1/leads/"+lead+"/midias/"+id+"/download",HttpMethod.GET,new HttpEntity<>(cabecalhos),byte[].class);
        assertThat(download.getStatusCode()).isEqualTo(HttpStatus.OK);assertThat(download.getBody()).containsExactly(bytes);assertThat(download.getHeaders().getContentDisposition().getFilename()).isEqualTo(nome);
        autenticar(ApoioAutenticacao.EMAIL_BRUNO);assertThat(http.exchange("/api/v1/leads/"+lead+"/midias/"+id+"/download",HttpMethod.GET,new HttpEntity<>(cabecalhos),String.class).getStatusCode()).isIn(HttpStatus.FORBIDDEN,HttpStatus.NOT_FOUND);
    }
    @Test void apiCrmOutboxAceiteECitacaoRecarregadaSemRedisparo()throws Exception{
        receber(recebido());worker.processarPendentes();UUID original=jdbc.queryForObject("SELECT id FROM mensagem WHERE atendimento_id=?",UUID.class,atendimento);
        when(chamada.execute()).thenAnswer(i->{var req=(Request)mockingDetails(transporte).getInvocations().stream().filter(v->v.getMethod().getName().equals("newCall")).reduce((a,b)->b).orElseThrow().getArgument(0);
            String body=req.url().encodedPath().contains("findMessages")?"{\"messages\":{\"records\":[{\"key\":{\"id\":\"w-"+lead+"\",\"fromMe\":false,\"remoteJid\":\"opaco@lid\",\"remoteJidAlt\":\"5561999990000@s.whatsapp.net\"},\"message\":{\"conversation\":\"Sintetico\"}}]}}":"{\"key\":{\"id\":\"wa-envio\",\"fromMe\":true}}";
            return new Response.Builder().request(req).protocol(Protocol.HTTP_1_1).code(200).message("sintetico").body(ResponseBody.create(body,MediaType.get("application/json"))).build();});
        autenticar(ApoioAutenticacao.EMAIL_ANA);cabecalhos.set("Idempotency-Key",UUID.randomUUID().toString());
        Instant horario=jdbc.queryForObject("SELECT enviado_em FROM mensagem WHERE id=?",java.sql.Timestamp.class,original).toInstant();
        var corpo=Map.of("leadId",lead,"atendimentoId",atendimento,"conteudo","Resposta sintetica","mensagemOrigemId",original,"origemEnviadaEm",horario.toString());
        var envio=http.postForEntity("/api/v1/atendimentos/mensagens",new HttpEntity<>(corpo,cabecalhos),ObjectNode.class);
        assertThat(envio.getStatusCode()).as(envio.getBody().toString()).isEqualTo(HttpStatus.OK);
        // Callback antecipado: so pode mudar a mensagem depois da correlacao do aceite.
        var estado=json.createObjectNode().put("event","messages.update").put("instance","evo-teste");
        estado.putObject("data").put("keyId","wa-envio").put("remoteJid","opaco@lid").put("fromMe",true).put("status","READ");
        receber(estado);worker.processarPendentes();publicador.publicarPendentes();
        org.awaitility.Awaitility.await().atMost(java.time.Duration.ofSeconds(10)).untilAsserted(()->assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_evento WHERE tipo='canal.mensagem.enviar' AND publicado_em IS NOT NULL",Integer.class)).isEqualTo(1));
        publicador.publicarPendentes();verify(transporte,times(2)).newCall(any());
        UUID nova=UUID.fromString(envio.getBody().path("mensagemId").asText());
        assertThat(jdbc.queryForObject("SELECT status_entrega::text FROM mensagem WHERE id=?",String.class,nova)).isEqualTo("LIDO");
        ((ObjectNode)estado.path("data")).put("status","DELIVERY_ACK");receber(estado);receber(estado);worker.processarPendentes();
        assertThat(jdbc.queryForObject("SELECT status_entrega::text FROM mensagem WHERE id=?",String.class,nova)).isEqualTo("LIDO");
    }
    @Test void proxyRealPermiteOriginExternoSomenteNoWebhook()throws Exception{
        try(var proxy=ProxyZApiParaTeste.criar(porta)){proxy.start();var cliente=clienteTlsDoProxy();String base="https://"+proxy.getHost()+":"+proxy.getMappedPort(8443);
            org.awaitility.Awaitility.await().atMost(java.time.Duration.ofSeconds(10)).untilAsserted(()->{try(var r=cliente.newCall(new Request.Builder().url(base+"/webhook/evolution").header("Host",ProxyZApiParaTeste.DOMINIO).post(RequestBody.create("{}",MediaType.get("application/json"))).build()).execute()){assertThat(r.code()).isEqualTo(403);}});
            for(String segredo:List.of("", "errado",SEGREDO))try(var r=cliente.newCall(new Request.Builder().url(base+"/webhook/evolution").header("Host",ProxyZApiParaTeste.DOMINIO).header("Origin","https://externo.example.test").header(EvolutionWebhookProtegidoFilter.HEADER,segredo).post(RequestBody.create(recebido().toString(),MediaType.get("application/json"))).build()).execute()){assertThat(r.code()).isEqualTo(segredo.equals(SEGREDO)?200:403);assertThat(r.body().string()).doesNotContain("Invalid CORS request");}
            try(var r=cliente.newCall(new Request.Builder().url(base+"/api/v1/config/features").header("Host",ProxyZApiParaTeste.DOMINIO).header("Origin","https://externo.example.test").build()).execute()){assertThat(r.code()).isEqualTo(403);assertThat(r.body().string()).contains("Invalid CORS request");}
        }
    }
    @ParameterizedTest @ValueSource(strings={"image","audio","video","pdf","doc","docx"})
    void uploadNaApiRealSaiPelaOutboxPreservandoOriginal(String caso)throws Exception {
        String caminho=switch(caso){case "audio"->"/z-api/audio-sintetico.ogg";case "video"->"/midia/chat-interno/video-validacao.mp4";case "pdf"->"/z-api/documento-sintetico.pdf";case "doc"->"/word/vazio.doc";case "docx"->"/word/sintetico.docx";default->null;};
        byte[] bytes;
        if(caminho==null)bytes=Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+juxkAAAAASUVORK5CYII=");
        else try(var f=getClass().getResourceAsStream(caminho)){bytes=f.readAllBytes();}
        String nome="Relatório com espaços."+switch(caso){case "image"->"PNG";case "audio"->"ogg";case "video"->"mp4";default->caso.toUpperCase(java.util.Locale.ROOT);};
        // Politica do upload compartilhado: Word preserva Unicode; os demais nomes
        // sao sanitizados antes do storage/outbox, independentemente do provedor.
        String nomePersistido=caso.equals("doc")||caso.equals("docx")?nome:nome.replaceAll("[^A-Za-z0-9._-]","_");
        String citacao="";
        if(caso.equals("docx")) {
            receber(recebido());worker.processarPendentes();
            UUID origem=jdbc.queryForObject("SELECT id FROM mensagem WHERE atendimento_id=?",UUID.class,atendimento);
            Instant origemEm=jdbc.queryForObject("SELECT enviado_em FROM mensagem WHERE id=?",java.sql.Timestamp.class,origem).toInstant();
            citacao="?mensagemOrigemId="+origem+"&origemEnviadaEm="+origemEm;
        }
        when(chamada.execute()).thenAnswer(i->{
            var req=(Request)mockingDetails(transporte).getInvocations().stream().filter(v->v.getMethod().getName().equals("newCall")).reduce((a,b)->b).orElseThrow().getArgument(0);
            if(req.url().encodedPath().contains("findMessages"))return new Response.Builder().request(req).protocol(Protocol.HTTP_1_1).code(200).message("sintetico").body(ResponseBody.create("{\"messages\":{\"records\":[{\"key\":{\"id\":\"w-"+lead+"\",\"fromMe\":false,\"remoteJid\":\"5561999990000@s.whatsapp.net\"},\"message\":{\"conversation\":\"Original sintetico\"}}]}}",MediaType.get("application/json"))).build();
            assertThat(req.url().encodedPath()).isEqualTo("/message/sendMedia/evo-teste");var b=new okio.Buffer();req.body().writeTo(b);var n=json.readTree(b.readUtf8());
            assertThat(Base64.getDecoder().decode(n.path("media").asText())).containsExactly(bytes);assertThat(n.path("fileName").asText()).isEqualTo(nomePersistido);
            if(caso.equals("docx"))assertThat(n.path("quoted").path("message").isObject()).isTrue();
            return new Response.Builder().request(req).protocol(Protocol.HTTP_1_1).code(201).message("sintetico").body(ResponseBody.create("{\"key\":{\"id\":\"wa-upload\",\"fromMe\":true}}",MediaType.get("application/json"))).build();
        });
        autenticar(ApoioAutenticacao.EMAIL_ANA);var upload=new HttpHeaders();upload.setBearerAuth(cabecalhos.getFirst(HttpHeaders.AUTHORIZATION).substring(7));upload.setContentType(org.springframework.http.MediaType.MULTIPART_FORM_DATA);upload.set("Idempotency-Key",UUID.randomUUID().toString());
        var partes=new org.springframework.util.LinkedMultiValueMap<String,Object>();partes.add("arquivo",new org.springframework.core.io.ByteArrayResource(bytes){@Override public String getFilename(){return nome;}});
        var resultado=http.postForEntity("/api/v1/atendimentos/"+atendimento+"/mensagens/midia"+citacao,new HttpEntity<>(partes,upload),ObjectNode.class);
        assertThat(resultado.getStatusCode()).as(String.valueOf(resultado.getBody())).isEqualTo(HttpStatus.OK);UUID id=UUID.fromString(resultado.getBody().path("mensagemId").asText());
        publicador.publicarPendentes();org.awaitility.Awaitility.await().atMost(java.time.Duration.ofSeconds(10)).untilAsserted(()->assertThat(jdbc.queryForObject("SELECT publicado_em IS NOT NULL FROM outbox_evento WHERE payload->>'mensagemId'=?",Boolean.class,id.toString())).isTrue());
        var download=http.exchange("/api/v1/leads/"+lead+"/midias/"+id+"/download",HttpMethod.GET,new HttpEntity<>(cabecalhos),byte[].class);assertThat(download.getBody()).containsExactly(bytes);
        assertThat(download.getHeaders().getContentDisposition().getFilename()).isEqualTo(nomePersistido);
        publicador.publicarPendentes();verify(transporte,times(caso.equals("docx")?2:1)).newCall(any());
    }
    @Test void usuarioSemVisibilidadeNaoEnvia() {
        autenticar(ApoioAutenticacao.EMAIL_BRUNO);
        var r=http.postForEntity("/api/v1/atendimentos/mensagens",new HttpEntity<>(Map.of("leadId",lead,"atendimentoId",atendimento,"conteudo","sintetico"),cabecalhos),String.class);
        assertThat(r.getStatusCode()).isIn(HttpStatus.FORBIDDEN,HttpStatus.NOT_FOUND);verifyNoInteractions(transporte);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_evento WHERE tipo='canal.mensagem.enviar'",Integer.class)).isZero();
    }
    void autenticar(String email){cabecalhos.setBearerAuth(ApoioAutenticacao.login(http,email,ApoioAutenticacao.SENHA_ATENDENTE).accessToken());}
    private static OkHttpClient clienteTlsDoProxy()throws Exception {
        var trust=new javax.net.ssl.X509TrustManager(){
            @Override public java.security.cert.X509Certificate[] getAcceptedIssuers(){return new java.security.cert.X509Certificate[0];}
            @Override public void checkClientTrusted(java.security.cert.X509Certificate[] c,String a){}
            @Override public void checkServerTrusted(java.security.cert.X509Certificate[] c,String a){}
        };
        var tls=javax.net.ssl.SSLContext.getInstance("TLS");tls.init(null,new javax.net.ssl.TrustManager[]{trust},new java.security.SecureRandom());
        // Exclusivo certificado sintetico local; transporte de producao nao usa este cliente.
        return new OkHttpClient.Builder().sslSocketFactory(tls.getSocketFactory(),trust).hostnameVerifier((h,s)->true).followRedirects(false).callTimeout(java.time.Duration.ofSeconds(15)).build();
    }
    void limpar(){jdbc.update("DELETE FROM webhook_entrada");jdbc.update("DELETE FROM outbox_evento");jdbc.update("DELETE FROM mensagem_correlacao_canal WHERE instancia='evo-teste'");
        jdbc.update("DELETE FROM mensagem_referencia WHERE mensagem_id IN(SELECT m.id FROM mensagem m JOIN atendimento a ON a.id=m.atendimento_id JOIN lead l ON l.id=a.lead_id WHERE l.nome LIKE ?)",PREFIXO+"%");
        for(String t:List.of("evento_timeline","audit_log"))jdbc.update("DELETE FROM "+t+" WHERE lead_id IN(SELECT id FROM lead WHERE nome LIKE ?)",PREFIXO+"%");
        jdbc.update("DELETE FROM mensagem WHERE atendimento_id IN(SELECT a.id FROM atendimento a JOIN lead l ON l.id=a.lead_id WHERE l.nome LIKE ?)",PREFIXO+"%");jdbc.update("DELETE FROM mensagem_envio_idempotencia WHERE atendimento_id IN(SELECT a.id FROM atendimento a JOIN lead l ON l.id=a.lead_id WHERE l.nome LIKE ?)",PREFIXO+"%");jdbc.update("DELETE FROM atendimento WHERE lead_id IN(SELECT id FROM lead WHERE nome LIKE ?)",PREFIXO+"%");jdbc.update("DELETE FROM lead WHERE nome LIKE ?",PREFIXO+"%");jdbc.update("DELETE FROM canal_credencial WHERE canal_id IN(SELECT id FROM canal WHERE nome LIKE ?)",PREFIXO+"%");jdbc.update("DELETE FROM canal WHERE nome LIKE ?",PREFIXO+"%");}
}
