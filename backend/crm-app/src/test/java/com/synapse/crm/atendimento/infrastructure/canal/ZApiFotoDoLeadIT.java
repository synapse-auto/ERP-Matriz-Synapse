package com.synapse.crm.atendimento.infrastructure.canal;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import okhttp3.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.boot.test.context.*;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import com.synapse.crm.app.PostgresIT;
import com.synapse.crm.app.foto.CapturarFotoZApiDoLead;
import com.synapse.crm.app.seguranca.ApoioAutenticacao;
import com.synapse.crm.core.application.lead.foto.ArmazenamentoDeFotoDeLead;

/** Entrada HTTP real, PostgreSQL/RLS e MinIO reais; só o HTTP externo é simulado. */
@SpringBootTest(classes=com.synapse.crm.app.SynapseCrmApplication.class,webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
@Import(ZApiFotoDoLeadIT.Transporte.class)
@TestPropertySource(properties={"synapse.canal.whatsapp.provedor=z-api","synapse.canal.whatsapp.numero-principal=FOTO-TESTE",
        "synapse.canal.z-api.habilitado=true","synapse.canal.z-api.instancia-id=FOTO-TESTE",
        "synapse.canal.z-api.token=token-sintetico-foto","synapse.canal.z-api.client-token=conta-sintetica-foto",
        "synapse.canal.z-api.credencial-id=44444444-4444-4444-4444-444444444444",
        "synapse.canal.z-api.foto-perfil.habilitado=true"})
class ZApiFotoDoLeadIT extends PostgresIT {
    static final UUID CREDENCIAL=UUID.fromString("44444444-4444-4444-4444-444444444444");
    static final GenericContainer<?> MINIO=new GenericContainer<>("cgr.dev/chainguard/minio@sha256:bd014394a80898e68c149f2311fdf8d5a2c2f3bb2c33b9327ae6d02b4b065ae1")
            .withEnv("MINIO_ROOT_USER","foto-test-access").withEnv("MINIO_ROOT_PASSWORD","foto-test-secret")
            .withCommand("server","/data").withExposedPorts(9000).waitingFor(Wait.forHttp("/minio/health/ready").forPort(9000));
    static { MINIO.start(); }
    @DynamicPropertySource static void storage(DynamicPropertyRegistry r) {
        r.add("synapse.midia.endpoint",()->"http://"+MINIO.getHost()+":"+MINIO.getMappedPort(9000));
        r.add("synapse.midia.access-key",()->"foto-test-access"); r.add("synapse.midia.secret-key",()->"foto-test-secret");
        r.add("synapse.midia.bucket",()->"foto-zapi-it");
    }
    @TestConfiguration(proxyBeanMethods=false) static class Transporte {
        @Bean static BeanFactoryPostProcessor storageReal() {
            return f->{f.getBeanDefinition("armazenamentoDeFotoDeLeadFake").setPrimary(false);
                f.getBeanDefinition("minioArmazenamentoDeFotoDeLead").setPrimary(true);};
        }
        @Bean ZApiWebhookTradutor tradutorFoto(ObjectMapper json) { return new ZApiWebhookTradutor(json,"FOTO-TESTE","WHATSAPP"); }
        @Bean SimuladorFoto servidorFoto() { return new SimuladorFoto(); }
        @Bean @Primary ZApiFotoDePerfil fotoParaTeste(ZApiProperties api,ZApiFotoProperties foto,ObjectMapper json,
                CircuitBreakerRegistry breakers,SimuladorFoto servidor) {
            return new ZApiFotoDePerfil(api,foto,json,breakers,new OkHttpClient.Builder().addInterceptor(servidor::responder).build(),"https://api.z-api.io");
        }
    }
    static class SimuladorFoto {
        final AtomicInteger consultas=new AtomicInteger(), downloads=new AtomicInteger();
        final List<Request> pedidos=new CopyOnWriteArrayList<>();
        volatile int status=200;
        volatile String link="https://pps.whatsapp.net/foto-sintetica";
        volatile Runnable aoConsultar=()->{};
        volatile int cor=0x2775b6;
        okhttp3.Response responder(Interceptor.Chain chain) throws java.io.IOException {
            assertThat(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            var pedido=chain.request(); pedidos.add(pedido);
            boolean api=pedido.url().host().equals("api.z-api.io");
            if(api) { consultas.incrementAndGet(); aoConsultar.run(); }
            else { downloads.incrementAndGet(); assertThat(pedido.header("Client-Token")).isNull(); }
            if(status==408) throw new java.net.SocketTimeoutException("fixture");
            byte[] bytes;
            if(api) bytes=("{\"link\":"+(link==null?"null":"\""+link+"\"")+"}").getBytes(java.nio.charset.StandardCharsets.UTF_8);
            else {
                var out=new java.io.ByteArrayOutputStream();
                var imagem=new java.awt.image.BufferedImage(64,64,java.awt.image.BufferedImage.TYPE_INT_RGB);
                var desenho=imagem.createGraphics();
                try { desenho.setColor(new java.awt.Color(cor));desenho.fillRect(0,0,64,64);
                    desenho.setColor(java.awt.Color.WHITE);desenho.fillOval(24,12,16,16);desenho.fillOval(16,34,32,26);
                } finally { desenho.dispose(); }
                javax.imageio.ImageIO.write(imagem,"png",out); bytes=out.toByteArray();
            }
            return new okhttp3.Response.Builder().request(pedido).protocol(Protocol.HTTP_1_1).message("fixture")
                    .code(status).body(ResponseBody.create(bytes,okhttp3.MediaType.get(api?"application/json":"image/png"))).build();
        }
    }
    @Autowired JdbcTemplate jdbc;
    @Autowired TestRestTemplate http;
    @Autowired CapturarFotoZApiDoLead captura;
    @Autowired SimuladorFoto servidor;
    @Autowired ArmazenamentoDeFotoDeLead storage;
    @Autowired CircuitBreakerRegistry breakers;
    @Autowired @org.springframework.beans.factory.annotation.Qualifier("fotoDePerfilExecutor")
    org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor executorFoto;
    @Autowired org.springframework.context.ApplicationEventPublisher eventos;
    @Autowired org.springframework.transaction.PlatformTransactionManager tm;
    @Autowired com.synapse.crm.core.application.lead.foto.CapturaDeFotoRepositorio reservas;
    @org.springframework.boot.test.mock.mockito.SpyBean com.synapse.crm.app.foto.AvisadorDeFotoDoLead avisador;
    UUID lead, canal, ana;
    String tokenAna,tokenBruno;
    @BeforeEach void preparar() {
        servidor.consultas.set(0);servidor.downloads.set(0);servidor.pedidos.clear();servidor.status=200;
        servidor.link="https://pps.whatsapp.net/foto-sintetica"; servidor.aoConsultar=()->{};
        servidor.cor=0x2775b6;
        breakers.circuitBreaker("canal-z-api-foto").reset();
        canal=jdbc.queryForObject("INSERT INTO canal(nome,tipo,ativo) VALUES (?,'WHATSAPP',true) RETURNING id",UUID.class,"FOTO-IT-"+UUID.randomUUID());
        jdbc.update("INSERT INTO canal_credencial(id,canal_id,numero,identificador_externo,token_ref) VALUES (?,?,'5511999991234','FOTO-TESTE','env://Z_API_TOKEN')",CREDENCIAL,canal);
        ana=jdbc.queryForObject("SELECT id FROM usuario WHERE email='ana@dev.local'",UUID.class);
        lead=jdbc.queryForObject("INSERT INTO lead(nome,telefone,status_basico,atendente_responsavel_id) VALUES (?,'5511999991234','EM_ATENDIMENTO',?) RETURNING id",UUID.class,"Foto sintética "+UUID.randomUUID(),ana);
        tokenAna=ApoioAutenticacao.login(http,ApoioAutenticacao.EMAIL_ANA,ApoioAutenticacao.SENHA_ATENDENTE).accessToken();
        tokenBruno=ApoioAutenticacao.login(http,ApoioAutenticacao.EMAIL_BRUNO,ApoioAutenticacao.SENHA_ATENDENTE).accessToken();
    }
    @AfterEach void limpar() {
        await().atMost(Duration.ofSeconds(20)).until(()->executorFoto.getActiveCount()==0 && executorFoto.getThreadPoolExecutor().getQueue().isEmpty());
        await().atMost(Duration.ofSeconds(20)).until(()->jdbc.queryForObject("SELECT count(*) FROM lead_foto_captura WHERE lead_id=? AND resultado IS NULL",Integer.class,lead)==0);
        jdbc.query("SELECT foto_referencia FROM lead WHERE id=?",(rs,n)->rs.getString(1),lead).forEach(storage::remover);
        jdbc.update("DELETE FROM evento_timeline WHERE lead_id=?",lead);
        jdbc.update("DELETE FROM atendimento WHERE lead_id=?",lead);
        jdbc.update("DELETE FROM lead WHERE id=?",lead);jdbc.update("DELETE FROM canal_credencial WHERE id=?",CREDENCIAL);jdbc.update("DELETE FROM canal WHERE id=?",canal);
    }
    private <T> ResponseEntity<T> get(String caminho,Class<T> tipo,String token) {
        return ApoioAutenticacao.comToken(http,token,HttpMethod.GET,caminho,tipo);
    }
    private void abrir() { assertThat(get("/api/v1/leads/"+lead,String.class,tokenAna).getStatusCode()).isEqualTo(HttpStatus.OK); }
    private void esperar(String resultado) {
        await().atMost(Duration.ofSeconds(20)).untilAsserted(()->assertThat(jdbc.queryForList("SELECT resultado FROM lead_foto_captura WHERE lead_id=?",String.class,lead)).containsExactly(resultado));
        await().atMost(Duration.ofSeconds(20)).until(()->executorFoto.getActiveCount()==0 && executorFoto.getThreadPoolExecutor().getQueue().isEmpty());
    }
    @Test void leadAntigoAtualizaPelaFichaPersisteFotoAutorizadaECacheSobreviveANovasSolicitacoes() {
        abrir(); esperar("FOTO");
        var resposta=get("/api/v1/leads/"+lead+"/foto",byte[].class,tokenAna);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resposta.getHeaders().getContentType().toString()).isEqualTo("image/png");
        assertThat(resposta.getBody()).isNotEmpty();
        assertThat(get("/api/v1/leads/"+lead+"/foto",byte[].class,tokenBruno).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        for(int i=0;i<10;i++) abrir();
        assertThat(servidor.consultas.get()).isEqualTo(1);
        assertThat(get("/api/v1/leads?busca=Foto",String.class,tokenAna).getBody()).contains("/api/v1/leads/"+lead+"/foto");
        org.mockito.Mockito.verify(avisador,org.mockito.Mockito.timeout(5000).atLeastOnce()).publicar();
    }
    @Test void fotoNaoFornecidaMantemFallbackComCacheNegativo() {
        servidor.link=null; abrir();esperar("AUSENTE"); abrir();
        assertThat(get("/api/v1/leads/"+lead+"/foto",byte[].class,tokenAna).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(servidor.consultas.get()).isEqualTo(1);assertThat(servidor.downloads.get()).isZero();
    }
    @ParameterizedTest @ValueSource(ints={408,401,403,429,500,503})
    void erroPreservaFotoValidaEAplicaIntervaloDeErro(int status) {
        abrir();esperar("FOTO");String anterior=jdbc.queryForObject("SELECT foto_referencia FROM lead WHERE id=?",String.class,lead);
        jdbc.update("UPDATE lead_foto_captura SET proxima_consulta=now()-interval '1 minute',resultado=NULL WHERE lead_id=?",lead);
        servidor.status=status; abrir();esperar("ERRO"); abrir();
        assertThat(jdbc.queryForObject("SELECT foto_referencia FROM lead WHERE id=?",String.class,lead)).isEqualTo(anterior);
        assertThat(get("/api/v1/leads/"+lead+"/foto",byte[].class,tokenAna).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(servidor.consultas.get()).isEqualTo(2);
    }
    @Test void fotosManuaisEDeOutraOrigemNaoSaoSubstituidas() {
        jdbc.update("UPDATE lead SET foto_url='https://manual.example/avatar' WHERE id=?",lead);
        abrir();assertThat(servidor.consultas.get()).isZero();
        jdbc.update("UPDATE lead SET foto_url=NULL,foto_referencia='lead/origem-anterior.png' WHERE id=?",lead);
        abrir();esperar("PRESERVADA");assertThat(servidor.consultas.get()).isZero();
    }
    @Test void alteracaoManualDuranteDownloadNaoEPerdida() {
        servidor.aoConsultar=()->jdbc.update("UPDATE lead SET foto_url='https://manual.example/nova' WHERE id=?",lead);
        abrir();esperar("ERRO");
        assertThat(jdbc.queryForObject("SELECT foto_url FROM lead WHERE id=?",String.class,lead)).isEqualTo("https://manual.example/nova");
        assertThat(jdbc.queryForObject("SELECT foto_referencia FROM lead WHERE id=?",String.class,lead)).isNull();
    }
    @Test void leituraSemAcessoNaoDisparaColeta() {
        assertThat(get("/api/v1/leads/"+lead,String.class,tokenBruno).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(servidor.consultas.get()).isZero();
    }
    @Test void credencialDeOutraInstanciaNaoConsulta() {
        jdbc.update("UPDATE canal_credencial SET identificador_externo='OUTRA' WHERE id=?",CREDENCIAL);
        abrir(); assertThat(servidor.consultas.get()).isZero();
    }
    @Test void mensagemCommitadaUsaOListenerRealEConcorrentesDeduplicam() {
        UUID atendimento=jdbc.queryForObject("INSERT INTO atendimento(lead_id,canal_id,atendente_id,status) VALUES (?,?,?,'EM_ATENDIMENTO') RETURNING id",UUID.class,lead,canal,ana);
        var transacao=new org.springframework.transaction.support.TransactionTemplate(tm);
        com.synapse.crm.sharedkernel.identidade.ContextoDeServico.executarComo("teste-evento-foto",()->transacao.executeWithoutResult(s->eventos.publishEvent(new com.synapse.crm.atendimento.domain.evento.EventoDeAtendimento.MensagemRecebida(
                lead,atendimento,UUID.randomUUID(),false,java.time.Instant.now()))));
        java.util.stream.IntStream.range(0,20).parallel().forEach(i->captura.solicitar(lead));
        esperar("FOTO"); assertThat(servidor.consultas.get()).isEqualTo(1);
    }
    @Test void rollbackDaMensagemNaoIniciaColeta() {
        var transacao=new org.springframework.transaction.support.TransactionTemplate(tm);
        transacao.executeWithoutResult(s->{
            eventos.publishEvent(new com.synapse.crm.atendimento.domain.evento.EventoDeAtendimento.MensagemRecebida(
                    lead,UUID.randomUUID(),UUID.randomUUID(),false,java.time.Instant.now()));
            s.setRollbackOnly();
        });
        assertThat(servidor.consultas.get()).isZero();
    }
    @Test void reservaDuravelBloqueiaOutroNoENaoPodeTrocarInstancia() {
        var transacao=new org.springframework.transaction.support.TransactionTemplate(tm);
        var agora=java.time.Instant.now();
        com.synapse.crm.sharedkernel.identidade.ContextoDeServico.executarComo("teste-foto",()->transacao.executeWithoutResult(s->{
            assertThat(reservas.reservar(lead,"FOTO-TESTE",CREDENCIAL,"a".repeat(64),agora,agora.plusSeconds(120))).isPresent();
            assertThat(reservas.reservar(lead,"FOTO-TESTE",CREDENCIAL,"a".repeat(64),agora,agora.plusSeconds(120))).isEmpty();
            assertThat(reservas.reservar(lead,"OUTRA",CREDENCIAL,"a".repeat(64),agora.plusSeconds(121),agora.plusSeconds(240))).isEmpty();
        }));
        jdbc.update("UPDATE lead_foto_captura SET resultado='PRESERVADA' WHERE lead_id=?",lead);
    }

    @Test void reservasRealmenteConcorrentesNoBancoTemUmUnicoVencedor() throws Exception {
        var barreira=new java.util.concurrent.CyclicBarrier(2);
        java.util.function.Supplier<Boolean> reservar=()-> {
            try { barreira.await(10,java.util.concurrent.TimeUnit.SECONDS); }
            catch (Exception falha) { throw new IllegalStateException(falha); }
            return com.synapse.crm.sharedkernel.identidade.ContextoDeServico.buscarComo("teste-concorrente-foto",()->
                new org.springframework.transaction.support.TransactionTemplate(tm).execute(s->{
                    var agora=java.time.Instant.now();
                    return reservas.reservar(lead,"FOTO-TESTE",CREDENCIAL,"a".repeat(64),agora,agora.plusSeconds(120)).isPresent();
                }));
        };
        var primeira=java.util.concurrent.CompletableFuture.supplyAsync(reservar);
        var segunda=java.util.concurrent.CompletableFuture.supplyAsync(reservar);
        assertThat(List.of(primeira.get(15,java.util.concurrent.TimeUnit.SECONDS),segunda.get(15,java.util.concurrent.TimeUnit.SECONDS)))
                .containsExactlyInAnyOrder(true,false);
        jdbc.update("UPDATE lead_foto_captura SET resultado='PRESERVADA' WHERE lead_id=?",lead);
    }

    @Test void validadeExpiradaPermiteAtualizarSomenteAFotoQueOColetorCriou() {
        abrir();esperar("FOTO");
        String anterior=jdbc.queryForObject("SELECT foto_referencia FROM lead WHERE id=?",String.class,lead);
        jdbc.update("UPDATE lead_foto_captura SET proxima_consulta=now()-interval '1 minute',resultado=NULL WHERE lead_id=?",lead);
        servidor.cor=0x16834d;
        abrir();esperar("FOTO");
        assertThat(jdbc.queryForObject("SELECT foto_referencia FROM lead WHERE id=?",String.class,lead)).isNotEqualTo(anterior);
        assertThat(servidor.consultas.get()).isEqualTo(2);
    }

    @Test void cacheOperacionalNaoPodeSerLidoNemEscritoPorUsuarioMesmoDonoDoLead() throws Exception {
        abrir();esperar("FOTO");
        try(var conexao=java.sql.DriverManager.getConnection(POSTGRES.getJdbcUrl(),POSTGRES.getUsername(),POSTGRES.getPassword())) {
            conexao.setAutoCommit(false);
            try(var sql=conexao.createStatement()) {
                sql.execute("SET LOCAL ROLE synapse_app");
                try(var papel=sql.executeQuery("SELECT current_user,rolsuper,rolbypassrls FROM pg_roles WHERE rolname=current_user")) {
                    assertThat(papel.next()).isTrue();assertThat(papel.getString(1)).isEqualTo("synapse_app");
                    assertThat(papel.getBoolean(2)).isFalse();assertThat(papel.getBoolean(3)).isFalse();
                }
                sql.execute("SELECT set_config('app.papel','SERVICO',true)");
                try(var linha=sql.executeQuery("SELECT count(*) FROM lead_foto_captura WHERE lead_id='"+lead+"'")) {
                    linha.next();assertThat(linha.getInt(1)).isEqualTo(1);
                }
                sql.execute("SELECT set_config('app.papel','ATENDENTE',true),set_config('app.usuario_id','"+ana+"',true),set_config('app.acesso_clientes','true',true)");
                try(var linha=sql.executeQuery("SELECT count(*) FROM lead WHERE id='"+lead+"'")) {
                    linha.next();assertThat(linha.getInt(1)).isEqualTo(1);
                }
                try(var linha=sql.executeQuery("SELECT count(*) FROM lead_foto_captura WHERE lead_id='"+lead+"'")) {
                    linha.next();assertThat(linha.getInt(1)).isZero();
                }
                assertThat(sql.executeUpdate("UPDATE lead_foto_captura SET resultado='ERRO' WHERE lead_id='"+lead+"'")).isZero();
                assertThatThrownBy(()->sql.executeUpdate("INSERT INTO lead_foto_captura(lead_id,instancia,credencial_id,telefone_hash,token,proxima_consulta) VALUES ('"
                        +lead+"','FOTO-TESTE','"+CREDENCIAL+"','"+"a".repeat(64)+"','"+UUID.randomUUID()+"',now())"))
                        .isInstanceOf(java.sql.SQLException.class)
                        .satisfies(falha->assertThat(((java.sql.SQLException)falha).getSQLState()).isEqualTo("42501"));
            } finally { conexao.rollback(); }
        }
    }

    /** Aplicação real para screenshots headed, limitada ao banco Testcontainers sintético. */
    public static void main(String[] args) {
        if (!"true".equals(System.getenv("Z_API_FOTO_VISUAL_LOCAL"))) throw new IllegalStateException("FIXTURE_LOCAL_DESABILITADA");
        for (String propriedade : ZApiFotoDoLeadIT.class.getAnnotation(TestPropertySource.class).properties()) {
            int separador=propriedade.indexOf('='); System.setProperty(propriedade.substring(0,separador),propriedade.substring(separador+1));
        }
        for (String pool : List.of("general","chat")) {
            System.setProperty("synapse.datasource."+pool+".url",POSTGRES.getJdbcUrl());
            System.setProperty("synapse.datasource."+pool+".username",POSTGRES.getUsername());
            System.setProperty("synapse.datasource."+pool+".password",POSTGRES.getPassword());
            System.setProperty("synapse.datasource."+pool+".hikari.maximum-pool-size","2");
        }
        System.setProperty("spring.data.redis.host",REDIS.getHost());
        System.setProperty("spring.data.redis.port",REDIS.getMappedPort(6379).toString());
        System.setProperty("synapse.midia.endpoint","http://"+MINIO.getHost()+":"+MINIO.getMappedPort(9000));
        System.setProperty("synapse.midia.bucket","foto-zapi-visual");
        System.setProperty("synapse.midia.access-key","foto-test-access"); System.setProperty("synapse.midia.secret-key","foto-test-secret");
        System.setProperty("synapse.seguranca.jwt-segredo",SEGREDO_JWT_DE_TESTE);
        System.setProperty("synapse.seguranca.frontend-origem","http://localhost:3015");
        System.setProperty("synapse.tempo-real.origens-permitidas","http://localhost:3015");
        System.setProperty("synapse.agendamento.habilitado","false");
        System.setProperty("synapse.saude.critica.monitoramento-habilitado","false");
        var app=new org.springframework.boot.SpringApplication(com.synapse.crm.app.SynapseCrmApplication.class,Transporte.class);
        var contexto=app.run("--spring.profiles.active=dev","--server.address=127.0.0.1","--server.port=8095");
        var db=contexto.getBean(JdbcTemplate.class);
        UUID canal=db.queryForObject("INSERT INTO canal(nome,tipo,ativo) VALUES ('Fotos Z-API sintéticas','WHATSAPP',true) RETURNING id",UUID.class);
        db.update("INSERT INTO canal_credencial(id,canal_id,numero,identificador_externo,token_ref) VALUES (?,?,'5511999991234','FOTO-TESTE','env://Z_API_TOKEN')",CREDENCIAL,canal);
        UUID ana=db.queryForObject("SELECT id FROM usuario WHERE email='ana@dev.local'",UUID.class);
        for(int i=1;i<=2;i++) {
            UUID lead=db.queryForObject("INSERT INTO lead(nome,telefone,status_basico,atendente_responsavel_id,num_atendimentos) VALUES (?,?,'EM_ATENDIMENTO',?,1) RETURNING id",UUID.class,"Contato Foto Sintética "+i,"551199999123"+i,ana);
            db.update("INSERT INTO atendimento(lead_id,canal_id,canal_credencial_id,atendente_id,status) VALUES (?,?,?,?,'EM_ATENDIMENTO')",lead,canal,CREDENCIAL,ana);
        }
    }
}
