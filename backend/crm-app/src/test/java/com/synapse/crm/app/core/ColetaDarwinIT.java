package com.synapse.crm.app.core;

import static com.synapse.crm.app.seguranca.ApoioAutenticacao.*;
import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.synapse.crm.app.PostgresIT;
import com.synapse.crm.core.application.darwin.DarwinGateway;

/** Entradas HTTP reais do CRM -> executor -> cliente HTTP Darwin real -> PostgreSQL -> ficha. */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
class ColetaDarwinIT extends PostgresIT {
    private static final AtomicInteger CHAMADAS=new AtomicInteger();
    private static final HttpServer DARWIN=servidor();
    private static volatile int status=200;
    private static volatile String corpo;
    private static volatile String caminho;
    private static volatile String autorizacao;
    private static volatile CountDownLatch bloqueio;
    private static final String PACIENTE="""
            {"cpf":"529.982.247-25","name":"Pessoa Sintética Darwin","birthDate":"1990-05-21T00:00:00.000Z",
             "phoneNumber":"55 (61) 900000000","email":null}
            """;
    private static HttpServer servidor() {
        try {
            var servidor=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
            servidor.setExecutor(java.util.concurrent.Executors.newCachedThreadPool(r->{var t=new Thread(r);t.setDaemon(true);return t;}));
            servidor.createContext("/", troca->{
                CHAMADAS.incrementAndGet(); caminho=troca.getRequestURI().toString();
                autorizacao=troca.getRequestHeaders().getFirst("Authorization");
                var espera=bloqueio;
                if (espera!=null) try { espera.await(10,TimeUnit.SECONDS); } catch(InterruptedException e){Thread.currentThread().interrupt();}
                byte[] bytes=corpo.getBytes(StandardCharsets.UTF_8);
                try {
                    troca.getResponseHeaders().set("Content-Type","application/json");
                    troca.sendResponseHeaders(status,bytes.length); troca.getResponseBody().write(bytes);
                } finally {troca.close();}
            }); servidor.start(); return servidor;
        } catch(java.io.IOException e){throw new ExceptionInInitializerError(e);}
    }
    @DynamicPropertySource static void configurar(DynamicPropertyRegistry r){
        r.add("synapse.integracoes.darwin.habilitado",()->"true");
        r.add("synapse.integracoes.darwin.url",()->"http://127.0.0.1:"+DARWIN.getAddress().getPort());
        r.add("synapse.integracoes.darwin.token",()->"credencial-sintetica");
        r.add("synapse.integracoes.darwin.circuito-minimo",()->"100");
        r.add("synapse.integracoes.darwin.timeout-consulta",()->"1s");
    }
    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate jdbc;
    @SpyBean DarwinGateway gateway;
    private UUID id;
    private String ana;
    private String bruno;
    private String telefone;
    private UUID atendimento;
    @BeforeEach void preparar(){
        status=200;corpo=PACIENTE;bloqueio=null;CHAMADAS.set(0);
        id=UUID.randomUUID(); telefone="55619"+String.format("%08d",Math.floorMod(id.getLeastSignificantBits(),100_000_000L));
        jdbc.update("INSERT INTO lead (id,nome,telefone,atendente_responsavel_id,status_basico) VALUES (?,?,?,(SELECT id FROM usuario WHERE email=?),'EM_ATENDIMENTO')",id,telefone,telefone,EMAIL_ANA);
        atendimento=UUID.randomUUID();
        jdbc.update("INSERT INTO atendimento(id,lead_id,canal_id,atendente_id,status,iniciado_em) VALUES (?,?,'ca000000-0000-4000-8000-000000000001',(SELECT id FROM usuario WHERE email=?),'EM_ATENDIMENTO',now())",atendimento,id,EMAIL_ANA);
        jdbc.update("INSERT INTO campo_customizado (chave,rotulo,tipo,ordem) VALUES ('data_nascimento','Data de nascimento','DATA',0) ON CONFLICT (chave) DO NOTHING");
        ana=login(http,EMAIL_ANA,SENHA_ATENDENTE).accessToken();bruno=login(http,EMAIL_BRUNO,SENHA_ATENDENTE).accessToken();
        doAnswer(i->{assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();return i.callRealMethod();}).when(gateway).consultar(anyString());
    }
    @AfterEach void limpar(){
        if(bloqueio!=null)bloqueio.countDown();
        await().atMost(Duration.ofSeconds(10)).until(()->!"PROCESSANDO".equals(estadoBanco()));
        jdbc.update("DELETE FROM audit_log WHERE entidade_id=?",id);
        jdbc.update("DELETE FROM outbox_evento WHERE payload->>'leadId'=?",id.toString());
        jdbc.update("DELETE FROM atendimento WHERE id=?",atendimento);
        jdbc.update("DELETE FROM lead WHERE id=?",id);
    }
    private String rota(){return "/api/v1/leads/"+id;}
    private ResponseEntity<String> pedir(String token,HttpMethod metodo,String rota,Object dados){
        var h=new HttpHeaders();h.setBearerAuth(token);h.setContentType(MediaType.APPLICATION_JSON);
        return http.exchange(rota,metodo,new HttpEntity<>(dados,h),String.class);
    }
    private String estadoBanco(){return jdbc.query("SELECT estado FROM lead_coleta_cadastral WHERE lead_id=?",(rs,n)->rs.getString(1),id).stream().findFirst().orElse("");}
    private void cpf(){assertThat(pedir(ana,HttpMethod.PUT,rota(),Map.of("cpf","52998224725")).getStatusCode()).isEqualTo(HttpStatus.OK);}
    private void concluido(String estado){await().atMost(Duration.ofSeconds(10)).untilAsserted(()->assertThat(estadoBanco()).isEqualTo(estado));}

    @Test void cpfPersistidoDisparaHttpRealPreencheCamposERefreshNaoRepete(){
        cpf();concluido("ATUALIZADO");
        assertThat(CHAMADAS).hasValue(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_evento WHERE tipo='tempo-real.atendimento.estado.v1' AND payload->>'leadId'=? AND payload->>'eventoTipo'='DADOS_CLIENTE_ATUALIZADOS'",Integer.class,id.toString())).isPositive();
        assertThat(caminho).isEqualTo("/api/patients/find/cpf?cpf=529.982.247-25");
        assertThat(autorizacao).isEqualTo("Bearer credencial-sintetica");
        for(int n=0;n<2;n++)assertThat(pedir(ana,HttpMethod.GET,rota(),null).getBody())
                .contains("Pessoa Sintética Darwin","52998224725","1990-05-21",telefone).doesNotContain("900000000");
        assertThat(pedir(ana,HttpMethod.GET,rota()+"/darwin/coleta",null).getBody()).contains("ATUALIZADO");
        assertThat(CHAMADAS).hasValue(1);
        await().atMost(Duration.ofSeconds(5)).untilAsserted(()->assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE acao='ENRIQUECER_CADASTRO_DARWIN' AND entidade_id=?",Integer.class,id)).isEqualTo(1));
    }
    @Test void primeiraConsultaDeLeadComCpfJaExistenteDisparaUmaVez(){
        jdbc.update("UPDATE lead SET cpf='52998224725' WHERE id=?",id);
        assertThat(pedir(ana,HttpMethod.GET,rota(),null).getStatusCode()).isEqualTo(HttpStatus.OK);
        concluido("ATUALIZADO");assertThat(CHAMADAS).hasValue(1);
    }
    @Test void semCpfNaoAdivinhaIdentidadeNemConsulta(){
        pedir(ana,HttpMethod.GET,rota(),null);concluido("IDENTIFICACAO_PENDENTE");
        assertThat(CHAMADAS).hasValue(0);
        assertThat(jdbc.queryForObject("SELECT cpf FROM lead WHERE id=?",String.class,id)).isNull();
        cpf();concluido("ATUALIZADO");assertThat(CHAMADAS).hasValue(1);
    }
    @Test void cpfDivergenteNaoAplica(){
        corpo=PACIENTE.replace("529.982.247-25","390.533.447-05");cpf();concluido("IDENTIDADE_DIVERGENTE");
        assertThat(jdbc.queryForObject("SELECT nome FROM lead WHERE id=?",String.class,id)).isEqualTo(telefone);
    }
    @Test void listaAmbiguaNaoAplica(){corpo="["+PACIENTE+","+PACIENTE+"]";cpf();concluido("RESPOSTA_INVALIDA");}
    @Test void nascimentoInvalidoNaoPersisteParcialmente(){
        corpo=PACIENTE.replace("1990-05-21T00:00:00.000Z","1990-02-31T00:00:00.000Z");cpf();concluido("RESPOSTA_INVALIDA");
        assertThat(jdbc.queryForObject("SELECT nome FROM lead WHERE id=?",String.class,id)).isEqualTo(telefone);
    }
    @Test void pacienteForaDoEscopoOuAusenteNaoEInventado(){status=404;cpf();concluido("NAO_ENCONTRADO");}
    @Test void novaTentativaControladaAposIntervaloRecupera(){
        status=503;cpf();concluido("INDISPONIVEL");status=200;
        jdbc.update("UPDATE lead_coleta_cadastral SET repeticao_manual=now()-interval '1 second' WHERE lead_id=?",id);
        assertThat(pedir(ana,HttpMethod.POST,rota()+"/darwin/coleta",null).getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        concluido("ATUALIZADO");assertThat(CHAMADAS).hasValue(2);
    }
    @Test void dadosManuaisValidosNaoSaoSobrescritos(){
        jdbc.update("UPDATE lead SET nome='Nome manual', dados_customizados='{\"data_nascimento\":\"1985-01-01\"}'::jsonb WHERE id=?",id);
        cpf();concluido("REVISAO_NECESSARIA");
        assertThat(pedir(ana,HttpMethod.GET,rota(),null).getBody()).contains("Nome manual","1985-01-01").doesNotContain("Pessoa Sintética Darwin");
    }
    @Test void edicaoDuranteHttpNaoEPerdidaENaoDuplica(){
        bloqueio=new CountDownLatch(1);cpf();
        await().atMost(Duration.ofSeconds(5)).until(()->CHAMADAS.get()==1);
        assertThat(pedir(ana,HttpMethod.PUT,rota(),Map.of("nome","Edição concorrente")).getStatusCode()).isEqualTo(HttpStatus.OK);
        for(int n=0;n<3;n++)pedir(ana,HttpMethod.POST,rota()+"/darwin/coleta",null);
        bloqueio.countDown();concluido("REVISAO_NECESSARIA");
        assertThat(pedir(ana,HttpMethod.GET,rota(),null).getBody()).contains("Edição concorrente");
        assertThat(CHAMADAS).hasValue(1);
    }
    @ParameterizedTest @ValueSource(ints={401,403,429,500,503})
    void falhaNaoBloqueiaFichaNemRepeteAutomaticamente(int httpStatus){
        status=httpStatus;cpf();concluido(httpStatus==401||httpStatus==403?"AUTENTICACAO":httpStatus==429?"LIMITE":"INDISPONIVEL");
        for(int n=0;n<3;n++){
            assertThat(pedir(ana,HttpMethod.GET,rota(),null).getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(pedir(ana,HttpMethod.POST,rota()+"/darwin/coleta",null).getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        }
        assertThat(CHAMADAS).hasValue(1);
    }
    @Test void timeoutNaoBloqueiaRequisicaoDoChat(){
        bloqueio=new CountDownLatch(1);cpf();
        assertThat(pedir(ana,HttpMethod.GET,rota(),null).getStatusCode()).isEqualTo(HttpStatus.OK);
        concluido("INDISPONIVEL");assertThat(CHAMADAS).hasValue(1);bloqueio.countDown();
    }
    @Test void acessoNegadoNaoAgendaNemConsultaEstado(){
        jdbc.update("UPDATE lead SET cpf='52998224725' WHERE id=?",id);
        for(String rota:java.util.List.of(rota(),rota()+"/darwin/coleta"))
            assertThat(pedir(bruno,HttpMethod.GET,rota,null).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(pedir(bruno,HttpMethod.POST,rota()+"/darwin/coleta",null).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(CHAMADAS).hasValue(0);assertThat(estadoBanco()).isEmpty();
    }
}
