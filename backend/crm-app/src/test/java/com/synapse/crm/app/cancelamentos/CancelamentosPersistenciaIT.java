package com.synapse.crm.app.cancelamentos;

import static com.synapse.crm.app.seguranca.ApoioAutenticacao.*;
import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.event.TransactionalEventListener;

import com.synapse.crm.app.PostgresIT;
import com.synapse.crm.relatorios.application.cancelamentos.CancelamentoRegistrado;

/** HTTP e persistencia/RLS reais. Sem adaptador/fixture substituindo a fonte de producao. */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
@TestPropertySource(properties="synapse.seguranca.token-interno=cancelamentos-it-descartavel")
@Import(CancelamentosPersistenciaIT.Observacao.class)
class CancelamentosPersistenciaIT extends PostgresIT {
    private static final String ORIGEM="cancelamentos-it";
    private static final String INTERNO="/internal/v1/cancelamentos";
    private static final String LISTA="/api/v1/cancelamentos?inicio=2036-10-01&fim=2036-10-31&base=AGENDAMENTO";
    private static final UUID ANA=UUID.fromString("11000000-0000-4000-8000-000000000004");
    private static final UUID BRUNO=UUID.fromString("11000000-0000-4000-8000-000000000005");
    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObservadorDoCommit eventos;
    @Autowired org.springframework.transaction.support.TransactionTemplate transacao;
    private String gestor,ana;
    private final List<UUID> leads=new ArrayList<>();

    @TestConfiguration static class Observacao {
        @Bean ObservadorDoCommit observadorDoCommit() { return new ObservadorDoCommit(); }
    }
    static class ObservadorDoCommit {
        final java.util.concurrent.atomic.AtomicInteger quantidade=new java.util.concurrent.atomic.AtomicInteger();
        @TransactionalEventListener void aposCommit(CancelamentoRegistrado evento) { quantidade.incrementAndGet(); }
    }

    @BeforeEach void preparar() {
        limpar();
        eventos.quantidade.set(0);
        gestor=login(http,EMAIL_GESTOR,SENHA_GESTOR).accessToken();
        ana=login(http,EMAIL_ANA,SENHA_ATENDENTE).accessToken();
        jdbc.update("UPDATE feature_flag SET habilitado=true WHERE chave='cancelamentos'");
        await().atMost(Duration.ofSeconds(5)).untilAsserted(()->assertThat(get(gestor,LISTA).getStatusCode().value()).isEqualTo(200));
    }
    @AfterEach void limpar() {
        jdbc.update("DELETE FROM cancelamento_agendamento WHERE origem=?",ORIGEM);
        for(UUID id:leads) { jdbc.update("DELETE FROM atendimento WHERE lead_id=?",id); jdbc.update("DELETE FROM lead WHERE id=?",id); }
        leads.clear();
        jdbc.update("UPDATE feature_flag SET habilitado=false WHERE chave='cancelamentos'");
        jdbc.update("DELETE FROM permissao_perfil_item WHERE alvo IN ('cancelamentos','cancelamentos.ver')");
    }
    Map<String,Object> corpo(String evento) {
        var r=new HashMap<String,Object>();
        r.put("origem",ORIGEM); r.put("eventoExternoId",evento); r.put("agendamentoExternoId","agenda-42");
        r.put("agendadoPara","2036-10-25T11:30:00-03:00");
        r.put("contatoNome","Contato sintetico"); r.put("profissionalNome","Profissional A"); r.put("tipoAgendamento","Retorno");
        return r;
    }
    ResponseEntity<JsonNode> post(Map<String,Object> body,String chave) {
        var h=new HttpHeaders(); h.set("X-Synapse-Token","cancelamentos-it-descartavel");
        if(chave!=null)h.set("Idempotency-Key",chave);
        return http.exchange(INTERNO,HttpMethod.POST,new HttpEntity<>(body,h),JsonNode.class);
    }
    ResponseEntity<JsonNode> get(String token,String rota) {
        var h=new HttpHeaders();h.setBearerAuth(token);
        return http.exchange(java.net.URI.create(http.getRootUri()+rota),HttpMethod.GET,new HttpEntity<>(h),JsonNode.class);
    }
    JsonNode criar(Map<String,Object> body,String chave) {
        var r=post(body,chave); assertThat(r.getStatusCode().value()).as(String.valueOf(r.getBody())).isEqualTo(201); return r.getBody();
    }
    Map<String,Object> comVinculo(String evento,UUID dono) {
        UUID lead=UUID.randomUUID(),atendimento=UUID.randomUUID();leads.add(lead);
        jdbc.update("INSERT INTO lead(id,nome,status_basico,atendente_responsavel_id) VALUES (?,?,'EM_ATENDIMENTO',?)",lead,"Cancelamento IT",dono);
        jdbc.update("INSERT INTO atendimento(id,lead_id,atendente_id,status) VALUES (?,?,?,'EM_ATENDIMENTO')",atendimento,lead,dono);
        var r=corpo(evento);r.put("leadId",lead);r.put("atendimentoId",atendimento);return r;
    }
    void conceder() {
        long revisao=jdbc.queryForObject("SELECT revisao FROM permissao_perfil WHERE papel='ATENDENTE'",Long.class);
        var h=new HttpHeaders();h.setBearerAuth(gestor);
        var r=http.exchange("/api/v1/gestao/permissoes/perfis/ATENDENTE",HttpMethod.PUT,new HttpEntity<>(
            Map.of("revisaoEsperada",revisao,"niveis",Map.of("cancelamentos","VER"),"acoes",Map.of("cancelamentos.ver",true)),h),String.class);
        assertThat(r.getStatusCode().value()).as(r.getBody()).isEqualTo(200);
    }
    @Test void gravaReplayIdenticoSemDuplicarEConflitoPreservaOriginal() {
        var body=corpo("evento-1");body.put("descricao","Motivo sintetico\nsegunda linha");
        var primeiro=criar(body,"chave-1");var replay=post(body,"chave-1");
        assertThat(replay.getStatusCode().value()).isEqualTo(200);
        assertThat(replay.getBody().path("id")).isEqualTo(primeiro.path("id"));
        assertThat(replay.getBody().path("recebidoEm")).isEqualTo(primeiro.path("recebidoEm"));
        assertThat(replay.getBody().path("repetido").asBoolean()).isTrue();
        body.put("descricao","Diferente");assertThat(post(body,"chave-1").getStatusCode().value()).isEqualTo(409);
        assertThat(post(corpo("evento-1"),"chave-nova").getStatusCode().value()).isEqualTo(409);
        assertThat(post(corpo("evento-outro"),"chave-1").getStatusCode().value()).isEqualTo(409);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM cancelamento_agendamento WHERE origem=?",Long.class,ORIGEM)).isEqualTo(1);
        assertThat(eventos.quantidade.get()).isEqualTo(1);
        var detalhe=get(gestor,"/api/v1/cancelamentos/"+primeiro.path("id").asText()).getBody();
        assertThat(detalhe.path("descricao").asText()).isEqualTo("Motivo sintetico\nsegunda linha");
        assertThat(detalhe.path("canceladoEm").isNull()).isTrue();assertThat(detalhe.path("registradoEm").isNull()).isTrue();
        assertThat(detalhe.path("recebidoEm").isTextual()).isTrue();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE entidade_tipo='CANCELAMENTO' AND entidade_id=? AND ator_tipo='AUTOMACAO'",Long.class,UUID.fromString(primeiro.path("id").asText()))).isGreaterThan(0);
        assertThat(jdbc.queryForObject("SELECT dados_depois::text FROM audit_log WHERE entidade_id=? ORDER BY id DESC LIMIT 1",String.class,UUID.fromString(primeiro.path("id").asText()))).doesNotContain("Contato","Motivo","Profissional");
    }
    @Test void concorrenciaDeMesmaChaveEDeMesmoEventoNaoDuplica() throws Exception {
        try(var pool=Executors.newFixedThreadPool(4)) {
            var inicio=new CountDownLatch(1);var tarefas=new ArrayList<Future<ResponseEntity<JsonNode>>>();
            for(int i=0;i<4;i++)tarefas.add(pool.submit(()->{assertThat(inicio.await(10,TimeUnit.SECONDS)).isTrue();return post(corpo("concorrente"),"concorrente");}));
            inicio.countDown();Set<String> ids=new HashSet<>();int criados=0;
            for(var f:tarefas) {var r=f.get(30,TimeUnit.SECONDS);assertThat(r.getStatusCode().value()).as(String.valueOf(r.getBody())).isIn(200,201);if(r.getStatusCode().value()==201)criados++;ids.add(r.getBody().path("id").asText());}
            assertThat(criados).isEqualTo(1);assertThat(ids).hasSize(1);
            var conflitoInicio=new CountDownLatch(1);
            var concorrentes=new ArrayList<Future<ResponseEntity<JsonNode>>>();
            for(String variante:List.of("variante-a","variante-b")) {
                concorrentes.add(pool.submit(()->{
                    assertThat(conflitoInicio.await(10,TimeUnit.SECONDS)).isTrue();
                    var body=corpo("conflito-concorrente");body.put("descricao",variante);
                    return post(body,"conflito-concorrente");
                }));
            }
            conflitoInicio.countDown();
            var status=new ArrayList<Integer>();
            for(var futuro:concorrentes)status.add(futuro.get(30,TimeUnit.SECONDS).getStatusCode().value());
            assertThat(status).containsExactlyInAnyOrder(201,409);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM cancelamento_agendamento WHERE origem=? AND evento_externo_id='conflito-concorrente'",Long.class,ORIGEM)).isEqualTo(1);
        }
    }
    @Test void ocorrenciasDiferentesDoMesmoAgendamentoSaoPreservadas() {
        criar(corpo("ocorrencia-1"),"ocorrencia-1");criar(corpo("ocorrencia-2"),"ocorrencia-2");
        assertThat(get(gestor,LISTA).getBody().path("itens")).hasSize(2);
    }
    @Test void detalheProjetaIdentificacaoAtualAutorizadaSemVazarNaListaOuEntreLeads() {
        conceder();
        var proprio = comVinculo("identificacao-propria", ANA);
        var outro = comVinculo("identificacao-outro", BRUNO);
        jdbc.update("UPDATE lead SET cpf=?,telefone=? WHERE id=?", "00000000000", "556100000001", proprio.get("leadId"));
        jdbc.update("UPDATE lead SET cpf=?,telefone=? WHERE id=?", "11111111111", "556100000002", outro.get("leadId"));
        var id = criar(proprio,"identificacao-propria").path("id").asText();
        var idOutro = criar(outro,"identificacao-outro").path("id").asText();
        var detalhe = get(ana,"/api/v1/cancelamentos/"+id);
        assertThat(detalhe.getStatusCode().value()).isEqualTo(200);
        assertThat(detalhe.getBody().path("identificacao").path("cpf").asText()).isEqualTo("00000000000");
        assertThat(detalhe.getBody().path("identificacao").path("telefone").asText()).isEqualTo("556100000001");
        assertThat(detalhe.getBody().path("profissionalNome").asText()).isEqualTo("Profissional A");
        assertThat(get(ana,LISTA).getBody().toString()).doesNotContain("identificacao","cpf","telefone","00000000000","556100000001");
        var negado = get(ana,"/api/v1/cancelamentos/"+idOutro);
        assertThat(negado.getStatusCode().value()).isEqualTo(404);
        assertThat(negado.getBody().toString()).doesNotContain("11111111111","556100000002");
        jdbc.update("UPDATE lead SET cpf=?,telefone=? WHERE id=?", "22222222222", "556100000003", proprio.get("leadId"));
        var atual = get(ana,"/api/v1/cancelamentos/"+id).getBody().path("identificacao");
        assertThat(atual.path("cpf").asText()).isEqualTo("22222222222");
        assertThat(atual.path("telefone").asText()).isEqualTo("556100000003");
        var gestorOutro = get(gestor,"/api/v1/cancelamentos/"+idOutro).getBody().path("identificacao");
        assertThat(gestorOutro.path("cpf").asText()).isEqualTo("11111111111");
        jdbc.update("UPDATE lead SET atendente_responsavel_id=? WHERE id=?", BRUNO, proprio.get("leadId"));
        jdbc.update("UPDATE atendimento SET atendente_id=? WHERE id=?", BRUNO, proprio.get("atendimentoId"));
        assertThat(get(ana,"/api/v1/cancelamentos/"+id).getStatusCode().value()).isEqualTo(404);
    }

    @Test void detalheSemVinculoOuSemIdentificacaoNaoInventaDadosNemAlteraIngestao() {
        var semVinculo = corpo("sem-identificacao"); semVinculo.remove("profissionalNome");
        var id = criar(semVinculo,"sem-identificacao").path("id").asText();
        var detalhe = get(gestor,"/api/v1/cancelamentos/"+id).getBody();
        assertThat(detalhe.path("identificacao").isNull()).isTrue();
        assertThat(detalhe.path("profissionalNome").isNull()).isTrue();
        assertThat(detalhe.path("canceladoEm").isNull()).isTrue();
        assertThat(post(semVinculo,"sem-identificacao").getStatusCode().value()).isEqualTo(200);
        var vinculado = comVinculo("cadastro-vazio",ANA);
        var outroId = criar(vinculado,"cadastro-vazio").path("id").asText();
        var vazio = get(gestor,"/api/v1/cancelamentos/"+outroId).getBody().path("identificacao");
        assertThat(vazio.path("cpf").isNull()).isTrue();
        assertThat(vazio.path("telefone").isNull()).isTrue();
    }
    @Test void autenticaSomenteServicoNaoJwtDeUsuario() {
        var h=new HttpHeaders();h.setBearerAuth(gestor);h.set("Idempotency-Key","teste");
        assertThat(http.exchange(INTERNO,HttpMethod.POST,new HttpEntity<>(corpo("negado"),h),String.class).getStatusCode().value()).isEqualTo(401);
        h.set("X-Synapse-Token","errado");assertThat(http.exchange(INTERNO,HttpMethod.POST,new HttpEntity<>(corpo("negado"),h),String.class).getStatusCode().value()).isEqualTo(401);
        assertThat(http.postForEntity(INTERNO,corpo("negado"),String.class).getStatusCode().value()).isEqualTo(401);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM cancelamento_agendamento WHERE origem=?",Long.class,ORIGEM)).isZero();
    }
    @Test void contratoRejeitaChaveAusenteDatasSemOffsetCamposDesconhecidosELimites() {
        assertThat(post(corpo("teste"),null).getStatusCode().value()).isEqualTo(400);
        assertThat(post(corpo("teste")," ").getStatusCode().value()).isEqualTo(400);
        for(var entrada:Map.<String,Object>of("agendadoPara","2036-10-25T11:30:00","descricao","x".repeat(2001),"payload",Map.of("segredo","nao-armazenar"),"leadId","uuid-invalido").entrySet()) {
            var body=corpo("invalido");body.put(entrada.getKey(),entrada.getValue());
            var r=post(body,"invalido");assertThat(r.getStatusCode().value()).as(String.valueOf(r.getBody())).isEqualTo(400);
            assertThat(r.getBody().toString()).doesNotContain("nao-armazenar","uuid-invalido","stackTrace");
        }
        var semData=corpo("sem-data");semData.remove("agendadoPara");assertThat(post(semData,"sem-data").getStatusCode().value()).isEqualTo(400);
        var semEvento=corpo("sem-evento");semEvento.remove("eventoExternoId");assertThat(post(semEvento,"sem-evento").getStatusCode().value()).isEqualTo(400);
    }
    @Test void validaParCanonicoNaoAdivinhaVinculoNemAceitaIdsInexistentes() {
        var a=comVinculo("vinculo-a",ANA);var b=comVinculo("vinculo-b",BRUNO);
        var erro=new HashMap<>(a);erro.put("atendimentoId",b.get("atendimentoId"));assertThat(post(erro,"errado").getStatusCode().value()).isEqualTo(400);
        erro.put("atendimentoId",UUID.randomUUID());assertThat(post(erro,"errado").getStatusCode().value()).isEqualTo(400);
        erro.remove("atendimentoId");assertThat(post(erro,"errado").getStatusCode().value()).isEqualTo(400);
        criar(a,"correto");criar(corpo("sem-vinculo"),"sem-vinculo");
    }
    @Test void leituraRealRespeitaFlagCapacidadeRlsDetalheEAtualizacaoDeResponsavel() {
        var a=comVinculo("a",ANA);var b=comVinculo("b",BRUNO);
        String idA=criar(a,"a").path("id").asText(),idB=criar(b,"b").path("id").asText();
        String sem=criar(corpo("sem"),"sem").path("id").asText();
        assertThat(get(ana,LISTA).getStatusCode().value()).isEqualTo(403);conceder();
        assertThat(get(ana,LISTA).getBody().path("itens")).hasSize(1);
        assertThat(get(gestor,LISTA).getBody().path("itens")).hasSize(3);
        for(String id:List.of(idB,sem,UUID.randomUUID().toString()))assertThat(get(ana,"/api/v1/cancelamentos/"+id).getStatusCode().value()).isEqualTo(404);
        var detalhe=get(ana,"/api/v1/cancelamentos/"+idA).getBody();assertThat(detalhe.path("leadId").asText()).isEqualTo(a.get("leadId").toString());
        jdbc.update("UPDATE lead SET atendente_responsavel_id=? WHERE id=?",BRUNO,a.get("leadId"));
        jdbc.update("UPDATE atendimento SET atendente_id=? WHERE id=?",BRUNO,a.get("atendimentoId"));
        assertThat(get(ana,"/api/v1/cancelamentos/"+idA).getStatusCode().value()).isEqualTo(404);
        jdbc.update("UPDATE feature_flag SET habilitado=false WHERE chave='cancelamentos'");
        await().atMost(Duration.ofSeconds(5)).untilAsserted(()->assertThat(get(gestor,LISTA).getStatusCode().value()).isEqualTo(403));
        assertThat(get(gestor,"/api/v1/cancelamentos/"+idA).getStatusCode().value()).isEqualTo(403);
    }
    @Test void paginaNoBancoOrdenaPorIdNoEmpateECombinaFiltrosLiterais() {
        for(int i=0;i<3;i++){var b=corpo("pagina-"+i);b.put("descricao",i==0?"Motivo":" \n\t");b.put("contatoNome",i==0?"Contato 100%_literal":"Outro");criar(b,"pagina-"+i);}
        var primeira=get(gestor,LISTA+"&tamanho=1").getBody();var segunda=get(gestor,LISTA+"&tamanho=1&pagina=1").getBody();
        assertThat(primeira.path("temProxima").asBoolean()).isTrue();assertThat(primeira.has("total")).isFalse();
        assertThat(primeira.path("itens").get(0).path("id")).isNotEqualTo(segunda.path("itens").get(0).path("id"));
        var filtrada=get(gestor,LISTA+"&nome=100%25_literal&profissional=Profissional%20A&tipo=retorno&descricao=COM_DESCRICAO");
        assertThat(filtrada.getStatusCode().value()).as(String.valueOf(filtrada.getBody())).isEqualTo(200);
        assertThat(filtrada.getBody().path("itens")).hasSize(1);
        assertThat(get(gestor,LISTA+"&descricao=SEM_DESCRICAO").getBody().path("itens")).hasSize(2);
        assertThat(get(gestor,LISTA+"&nome=inexistente").getBody().path("itens")).isEmpty();
    }
    @Test void separaRecebimentoOcorrenciaEAgendamentoComLimitesNoFuso() {
        var body=corpo("horarios");body.put("canceladoEm","2036-10-01T00:00:00-03:00");criar(body,"horarios");
        assertThat(get(gestor,"/api/v1/cancelamentos?inicio=2036-10-01&fim=2036-10-01&base=CANCELAMENTO").getBody().path("itens")).hasSize(1);
        assertThat(get(gestor,"/api/v1/cancelamentos?inicio=2036-09-30&fim=2036-09-30&base=CANCELAMENTO").getBody().path("itens")).isEmpty();
        assertThat(get(gestor,"/api/v1/cancelamentos?inicio=2036-10-01&fim=2036-10-31&base=REGISTRO").getBody().path("itens")).isEmpty();
    }
    @Test void registrarNaoAlteraAtendimentoNemPublicaComandoWhatsApp() {
        var body=comVinculo("sem-efeitos",ANA);
        long antes=jdbc.queryForObject("SELECT count(*) FROM outbox_evento",Long.class);
        criar(body,"sem-efeitos");
        assertThat(jdbc.queryForObject("SELECT status::text FROM atendimento WHERE id=?",String.class,body.get("atendimentoId"))).isEqualTo("EM_ATENDIMENTO");
        assertThat(jdbc.queryForObject("SELECT atendente_id FROM atendimento WHERE id=?",UUID.class,body.get("atendimentoId"))).isEqualTo(ANA);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_evento",Long.class)).isEqualTo(antes);
    }
    @Test void rlsRealNegaLeituraSemContextoEEscritaHumanaMesmoPorSqlDireto() {
        var a=comVinculo("rls",ANA);criar(a,"rls");criar(corpo("oculto-sem-vinculo"),"oculto-sem-vinculo");
        Long semContexto=transacao.execute(s->jdbc.queryForObject("SELECT count(*) FROM cancelamento_agendamento",Long.class));
        assertThat(semContexto).isZero();
        com.synapse.crm.app.seguranca.ApoioRls.entrarComo(ANA,com.synapse.crm.sharedkernel.identidade.PapelUsuario.ATENDENTE);
        try {
            String role=transacao.execute(s->jdbc.queryForObject("SELECT current_user",String.class));
            Long visiveis=transacao.execute(s->jdbc.queryForObject("SELECT count(*) FROM cancelamento_agendamento",Long.class));
            assertThat(role).isEqualTo("synapse_app");
            assertThat(visiveis).isEqualTo(1);
            assertThatThrownBy(()->transacao.executeWithoutResult(s->jdbc.update("""
                INSERT INTO cancelamento_agendamento(id,origem,evento_externo_id,agendamento_externo_id,
                    chave_idempotencia,impressao,agendado_para,recebido_em)
                VALUES (gen_random_uuid(),'cancelamentos-it','proibido','agenda','proibido','x',now(),now())
                """))).isInstanceOf(org.springframework.dao.DataAccessException.class)
                    .satisfies(falha->{
                        Throwable causa=falha;
                        while(causa.getCause()!=null)causa=causa.getCause();
                        assertThat(causa).isInstanceOf(java.sql.SQLException.class);
                        assertThat(((java.sql.SQLException)causa).getSQLState()).isEqualTo("42501");
                    });
        } finally { com.synapse.crm.app.seguranca.ApoioRls.sair(); }
    }
    @Test void planoLimitadoComVinteMilRegistrosSinteticos() {
        // Fixture somente neste PostgreSQL descartavel; nao e importacao ou medicao de producao.
        jdbc.update("""
            INSERT INTO cancelamento_agendamento(id,origem,evento_externo_id,agendamento_externo_id,
                chave_idempotencia,impressao,agendado_para,recebido_em)
            SELECT gen_random_uuid(),'cancelamentos-it','plano-'||n,'agenda-'||n,'plano-'||n,'x',
                TIMESTAMPTZ '2036-10-01 00:00:00+00'+n*INTERVAL '1 minute',
                TIMESTAMPTZ '2036-10-01 00:00:00+00'+n*INTERVAL '1 minute'
            FROM generate_series(1,20000) n
            """);
        jdbc.execute("ANALYZE cancelamento_agendamento");
        com.synapse.crm.app.seguranca.ApoioRls.entrarComo(UUID.fromString("11000000-0000-4000-8000-000000000002"),com.synapse.crm.sharedkernel.identidade.PapelUsuario.GESTOR);
        try {
            var plano=transacao.execute(s->jdbc.queryForList("""
                EXPLAIN (ANALYZE,BUFFERS,COSTS OFF)
                SELECT id,agendado_para,recebido_em FROM cancelamento_agendamento
                WHERE recebido_em >= TIMESTAMPTZ '2036-10-01 03:00:00+00'
                  AND recebido_em < TIMESTAMPTZ '2036-10-02 03:00:00+00'
                ORDER BY recebido_em DESC,id DESC LIMIT 26
                """,String.class));
            assertThat(plano).anyMatch(l->l.contains("cancelamento_recebido_idx"));
            System.out.println("PLANO_CANCELAMENTOS_SINTETICO_20000\n"+String.join("\n",plano));
        } finally { com.synapse.crm.app.seguranca.ApoioRls.sair(); }
    }
}
