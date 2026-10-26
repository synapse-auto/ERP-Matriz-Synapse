package com.synapse.crm.atendimento.infrastructure.canal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import org.junit.jupiter.api.*;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.*;
import org.springframework.test.context.TestPropertySource;

import com.synapse.crm.app.seguranca.ApoioAutenticacao;
import com.synapse.crm.atendimento.domain.canal.ResultadoDeEnvio;

/** Mesmos endpoints, job e storage reais, agora sobre ciclos anteriores ao corte. */
@TestPropertySource(properties={
    "synapse.canal.transicao.status-uzapi-legado=true",
    "synapse.canal.whatsapp.webhook-secret=segredo-antigo-sintetico"})
@org.springframework.context.annotation.Import({ZApiWebhookProtegidoIT.TransporteCdn.class,TransicaoProdutivaZApiIT.BancoIsolado.class})
class TransicaoProdutivaZApiIT extends ZApiWebhookProtegidoIT {
    private static final String BANCO="transicao_zapi_"+UUID.randomUUID().toString().replace("-", "");
    private static final String URL_BANCO="jdbc:postgresql://"+POSTGRES.getHost()+":"+POSTGRES.getMappedPort(5432)+"/"+BANCO;
    static {
        try(var c=java.sql.DriverManager.getConnection(POSTGRES.getJdbcUrl(),POSTGRES.getUsername(),POSTGRES.getPassword());var sql=c.createStatement()) {
            sql.execute("CREATE DATABASE "+BANCO);
        } catch(java.sql.SQLException erro){throw new ExceptionInInitializerError(erro);}
        org.flywaydb.core.Flyway.configure().dataSource(URL_BANCO,POSTGRES.getUsername(),POSTGRES.getPassword())
                .locations("classpath:db/migration","classpath:db/seed").placeholders(Map.of("telefone_ddi_padrao","55")).load().migrate();
    }
    @org.springframework.boot.test.context.TestConfiguration(proxyBeanMethods=false)
    static class BancoIsolado {
        @org.springframework.context.annotation.Bean
        static org.springframework.beans.factory.config.BeanFactoryPostProcessor poolsIsolados(org.springframework.core.env.ConfigurableEnvironment ambiente) {
            // O script operacional atua sobre TODOS os ciclos: nao pode compartilhar dados com
            // outras suites. A sobreposicao e local a este ApplicationContext, nunca global.
            ambiente.getPropertySources().addFirst(new org.springframework.core.env.MapPropertySource("transicao-banco-descartavel",Map.of(
                    "synapse.datasource.general.url",URL_BANCO,"synapse.datasource.chat.url",URL_BANCO)));
            return fabrica->{};
        }
    }
    @SpyBean ZApiAdapter adapter;
    @org.springframework.beans.factory.annotation.Autowired com.synapse.crm.app.mensagemprogramada.AgendadorDeMensagensProgramadas agendador;
    @org.springframework.beans.factory.annotation.Autowired com.synapse.crm.atendimento.infrastructure.webhook.ProcessadorDeWebhookEntradaOperacoes entradaOperacoes;
    UUID origem, canalAntigo, canalNovo, leadSemAncora, semAncora, programada;
    List<Map<String,Object>> canaisAnteriores;

    @BeforeEach void prepararTransicao() throws Exception {
        clearInvocations(adapter);
        doReturn(new ResultadoDeEnvio.Enfileirado("ZAPI-PROTEGIDA","processo-sintetico","wamid-sintetico"))
                .when(adapter).enviar(any());
        canaisAnteriores=jdbc.queryForList("SELECT id,ativo FROM canal");
        jdbc.update("UPDATE canal SET ativo=false");
        canalNovo=jdbc.queryForObject("SELECT canal_id FROM canal_credencial WHERE id=?",UUID.class,CREDENCIAL);
        jdbc.update("UPDATE canal_credencial SET ativo=false,token_ref='env://Z_API_TOKEN' WHERE id=?",CREDENCIAL);
        canalAntigo=UUID.randomUUID(); origem=UUID.randomUUID();
        jdbc.update("INSERT INTO canal(id,nome,tipo,ativo) VALUES (?,?,'WHATSAPP',true)",canalAntigo,PREFIXO+canalAntigo);
        jdbc.update("INSERT INTO canal_credencial(id,canal_id,numero,identificador_externo,token_ref) VALUES (?,?,'5561999990000','UZAPI-ANTIGA','env://UZAPI_TOKEN')",origem,canalAntigo);
        jdbc.update("UPDATE atendimento SET canal_id=?,canal_credencial_id=? WHERE id=?",canalAntigo,origem,atendimento);
        UUID ana=jdbc.queryForObject("SELECT atendente_id FROM atendimento WHERE id=?",UUID.class,atendimento);
        leadSemAncora=UUID.randomUUID();semAncora=UUID.randomUUID();
        jdbc.update("INSERT INTO lead(id,nome,telefone,atendente_responsavel_id,status_basico,ultima_mensagem_do_lead_em) VALUES (?,?,'5561999990001',?,'EM_ATENDIMENTO',now())",leadSemAncora,PREFIXO+leadSemAncora,ana);
        jdbc.update("INSERT INTO atendimento(id,lead_id,atendente_id,status) VALUES (?,?,?,'EM_ATENDIMENTO')",semAncora,leadSemAncora,ana);
        programada=jdbc.queryForObject("INSERT INTO mensagem_programada(lead_id,atendente_id,conteudo,data_envio) VALUES (?,?,'Agenda sintetica',now()-interval '1 minute') RETURNING id",UUID.class,lead,ana);
        String sql=Base64.getEncoder().encodeToString(Files.readAllBytes(Path.of("../../docker/provisionamento/transicionar-canal-z-api.sql")));
        assertThat(POSTGRES.execInContainer("sh","-c","printf '%s' \"$1\" | base64 -d > \"$2\"","sh",sql,"/tmp/transicionar-canal-z-api.sql").getExitCode()).isZero();
        assertThat(operar("PREPARAR","APLICAR").getExitCode()).isZero();
        var ativacao=operar("ATIVAR","APLICAR");
        assertThat(ativacao.getExitCode()).as(ativacao.getStderr()).isZero();
    }

    @AfterEach void limparTransicao() {
        jdbc.update("DELETE FROM audit_log WHERE entidade_id IN (?,?) OR entidade_id IN (SELECT id FROM canal_transicao)",atendimento,semAncora);
        jdbc.update("DELETE FROM canal_transicao_programada");
        jdbc.update("DELETE FROM mensagem_programada WHERE id=?",programada);
        jdbc.update("DELETE FROM atendimento_rota_canal");
        jdbc.update("DELETE FROM canal_transicao");
        jdbc.update("DELETE FROM mensagem_envio_idempotencia WHERE atendimento_id IN (?,?)",atendimento,semAncora);
        jdbc.update("DELETE FROM atendimento_participante WHERE atendimento_id IN (?,?)",atendimento,semAncora);
        for(var canal:canaisAnteriores) jdbc.update("UPDATE canal SET ativo=? WHERE id=?",canal.get("ativo"),canal.get("id"));
    }

    @Test void continuaCicloEAutoriaSemReescreverAncoraEIdempotenciaNaoRedispara() throws Exception {
        autenticar(ApoioAutenticacao.EMAIL_ANA);
        UUID responsavel=jdbc.queryForObject("SELECT atendente_id FROM atendimento WHERE id=?",UUID.class,atendimento);
        String chave=UUID.randomUUID().toString();headers.set("Idempotency-Key",chave);
        var primeira=enviar(lead,atendimento);
        assertThat(primeira.getStatusCode()).as(primeira.getBody()).isEqualTo(HttpStatus.OK);
        assertThat(json.readTree(enviar(lead,atendimento).getBody()).get("mensagemId"))
                .isEqualTo(json.readTree(primeira.getBody()).get("mensagemId"));
        var payload=jdbc.queryForObject("SELECT payload::text FROM outbox_evento WHERE tipo='canal.mensagem.enviar'",String.class);
        assertThat(payload).contains(CREDENCIAL.toString(),origem.toString(),"transicaoCanalId","z-api");
        publicador.publicarPendentes();publicador.publicarPendentes();
        verify(adapter,times(1)).enviar(argThat(e->CREDENCIAL.equals(e.credencialId())));
        assertThat(jdbc.queryForObject("SELECT canal_credencial_id FROM atendimento WHERE id=?",UUID.class,atendimento)).isEqualTo(origem);
        assertThat(jdbc.queryForObject("SELECT atendente_id FROM atendimento WHERE id=?",UUID.class,atendimento)).isEqualTo(responsavel);
        assertThat(jdbc.queryForObject("SELECT finalizado_em IS NULL FROM atendimento WHERE id=?",Boolean.class,atendimento)).isTrue();
        assertThat(jdbc.queryForObject("SELECT status_entrega::text FROM mensagem WHERE atendimento_id=?",String.class,atendimento)).isEqualTo("PENDENTE");
    }

    @Test void cicloSemAncoraTambemEnviaSemInventarHistorico() {
        autenticar(ApoioAutenticacao.EMAIL_ANA);
        var resposta=enviar(leadSemAncora,semAncora);
        assertThat(resposta.getStatusCode()).as(resposta.getBody()).isEqualTo(HttpStatus.OK);
        publicador.publicarPendentes();
        verify(adapter).enviar(argThat(e->CREDENCIAL.equals(e.credencialId())));
        assertThat(jdbc.queryForObject("SELECT canal_credencial_id IS NULL AND canal_id IS NULL FROM atendimento WHERE id=?",Boolean.class,semAncora)).isTrue();
    }

    @Test void participanteConsentidoNaoAssumeERespeitaNegativoDeOutroLead() {
        UUID bruno=jdbc.queryForObject("SELECT id FROM usuario WHERE email='bruno@dev.local'",UUID.class);
        UUID dona=jdbc.queryForObject("SELECT atendente_id FROM atendimento WHERE id=?",UUID.class,atendimento);
        jdbc.update("INSERT INTO atendimento_participante(atendimento_id,usuario_id,origem) VALUES (?,?,'CONVITE')",atendimento,bruno);
        autenticar("bruno@dev.local");
        assertThat(enviar(leadSemAncora,semAncora).getStatusCode()).isIn(HttpStatus.FORBIDDEN,HttpStatus.NOT_FOUND);
        var resposta=enviar(lead,atendimento);
        assertThat(resposta.getStatusCode()).as(resposta.getBody()).isEqualTo(HttpStatus.OK);
        assertThat(jdbc.queryForObject("SELECT atendente_id FROM atendimento WHERE id=?",UUID.class,atendimento)).isEqualTo(dona);
        assertThat(jdbc.queryForObject("SELECT atendente_responsavel_id FROM lead WHERE id=?",UUID.class,lead)).isEqualTo(dona);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM atendimento_participante WHERE atendimento_id=? AND saiu_em IS NULL",Integer.class,atendimento)).isEqualTo(1);
    }

    @Test void preparacaoOuRecuperacaoRecusaNovoEnvioSemGravarMensagemOuRoubarResponsavel() throws Exception {
        autenticar(ApoioAutenticacao.EMAIL_ANA);
        for(String estado:List.of("PREPARADA","RECUPERACAO")) {
            jdbc.update("UPDATE canal_transicao SET estado=?",estado);
            var resposta=enviar(lead,atendimento);
            assertThat(resposta.getStatusCode()).as(resposta.getBody()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(resposta.getBody()).contains("SAIDA_SUSPENSA_TRANSICAO_CANAL");
            assertThat(jdbc.queryForObject("SELECT count(*) FROM mensagem WHERE atendimento_id=?",Integer.class,atendimento)).isZero();
            publicador.publicarPendentes();
        }
        verify(adapter,never()).enviar(any());
    }

    @Test void outboxAntigaPendenteAmbiguaEAceitaNuncaTrocaDeProvedor() {
        for(String marcador:List.of("pendente","esgotada","ambigua","aceita","sem-origem")) {
            String payload=marcador.equals("sem-origem")?"{}":"{\"provedorOrigem\":\"uzapi-autotic\",\"identificadorCanalOrigem\":\"UZAPI-ANTIGA\",\"marcador\":\""+marcador+"\"}";
            jdbc.update("INSERT INTO outbox_evento(tipo,payload,esgotado_em,despacho_ambiguo_em,publicado_em) VALUES ('canal.mensagem.enviar',?::jsonb,CASE WHEN ?='esgotada' THEN now() END,CASE WHEN ?='ambigua' THEN now() END,CASE WHEN ?='aceita' THEN now() END)",payload,marcador,marcador,marcador);
        }
        var antes=jdbc.queryForList("SELECT id,payload::text,publicado_em,esgotado_em,despacho_ambiguo_em FROM outbox_evento ORDER BY id");
        publicador.publicarPendentes();publicador.publicarPendentes();
        assertThat(jdbc.queryForList("SELECT id,payload::text,publicado_em,esgotado_em,despacho_ambiguo_em FROM outbox_evento ORDER BY id")).isEqualTo(antes);
        verify(adapter,never()).enviar(any());
    }

    @Test void statusDaUrlAntigaExigeSegredoDestinoECorrelacaoAnteriorAoCorte() {
        UUID antiga=mensagemAntiga("id-uzapi-antiga",atendimento);
        UUID nova=mensagemComIdExterno("id-zapi-apos-corte",atendimento,false);
        assertThat(status("id-uzapi-antiga","read","UZAPI-ANTIGA","errado")).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(status("id-uzapi-antiga","read","OUTRA","segredo-antigo-sintetico")).isEqualTo(HttpStatus.FORBIDDEN);
        for(String estado:List.of("read","delivered","read"))
            assertThat(status("id-uzapi-antiga",estado,"UZAPI-ANTIGA","segredo-antigo-sintetico")).isEqualTo(HttpStatus.OK);
        assertThat(status("id-zapi-apos-corte","read","UZAPI-ANTIGA","segredo-antigo-sintetico")).isEqualTo(HttpStatus.OK);
        assertThat(jdbc.queryForObject("SELECT status_entrega::text FROM mensagem WHERE id=?",String.class,antiga)).isEqualTo("LIDO");
        assertThat(jdbc.queryForObject("SELECT status_entrega::text FROM mensagem WHERE id=?",String.class,nova)).isEqualTo("ENVIADO");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM webhook_entrada",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_evento",Integer.class)).isZero();
    }

    @Test void scriptSimulaAuditaEPreservaAsCredenciaisENaoReativaRecuperacao() throws Exception {
        String credencialAntes=jdbc.queryForObject("SELECT row_to_json(c)::text FROM canal_credencial c WHERE id=?",String.class,origem);
        assertThat(operar("SUSPENDER","SIMULAR").getExitCode()).isZero();
        assertThat(jdbc.queryForObject("SELECT estado FROM canal_transicao",String.class)).isEqualTo("ATIVA");
        assertThat(operar("ATIVAR","APLICAR").getExitCode()).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM atendimento_rota_canal",Integer.class)).isEqualTo(2);
        assertThat(operar("SUSPENDER","APLICAR").getExitCode()).isZero();
        assertThat(operar("ATIVAR","APLICAR").getExitCode()).isNotZero();
        assertThat(jdbc.queryForObject("SELECT row_to_json(c)::text FROM canal_credencial c WHERE id=?",String.class,origem)).isEqualTo(credencialAntes);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE acao='TRANSICAO_ROTA_DE_ATENDIMENTO' AND entidade_id IN (?,?)",Integer.class,atendimento,semAncora)).isEqualTo(2);
    }

    @Test void agendaAnteriorFicaRetidaSemCancelamentoEExigeLiberacaoIndividual() throws Exception {
        agendador.processarPendentes();publicador.publicarPendentes();
        assertThat(jdbc.queryForObject("SELECT status::text FROM mensagem_programada WHERE id=?",String.class,programada)).isEqualTo("AGENDADA");
        verify(adapter,never()).enviar(any());
        assertThat(operar("LIBERAR_PROGRAMADA","APLICAR","programada="+UUID.randomUUID()).getExitCode()).isNotZero();
        var liberar=operar("LIBERAR_PROGRAMADA","APLICAR","programada="+programada);
        assertThat(liberar.getExitCode()).as(liberar.getStderr()).isZero();
        agendador.processarPendentes();agendador.processarPendentes();publicador.publicarPendentes();
        assertThat(jdbc.queryForObject("SELECT status::text FROM mensagem_programada WHERE id=?",String.class,programada)).isEqualTo("ENVIADA");
        verify(adapter,times(1)).enviar(argThat(e->CREDENCIAL.equals(e.credencialId())));
    }

    @Test void operadorNaoEsperaLockDeEnvioNemAlteraBancoDivergente() throws Exception {
        assertThat(operar("SUSPENDER","APLICAR","banco_esperado=OUTRO").getExitCode()).isNotZero();
        try(var conexao=jdbc.getDataSource().getConnection();var comando=conexao.createStatement()) {
            conexao.setAutoCommit(false);
            comando.execute("SELECT pg_advisory_xact_lock_shared(hashtextextended('synapse:transicao-canal',0))");
            var recusa=operar("SUSPENDER","APLICAR");
            assertThat(recusa.getExitCode()).isNotZero();
            assertThat(recusa.getStderr()).contains("transacao de envio em andamento");
            conexao.rollback();
        }
        assertThat(jdbc.queryForObject("SELECT estado FROM canal_transicao",String.class)).isEqualTo("ATIVA");
    }

    @Test void rotaTemRlsRealENaoPodeSerReescritaPeloUsuarioDaAplicacao() throws Exception {
        for(String email:List.of("ana@dev.local","bruno@dev.local")) {
            UUID usuario=jdbc.queryForObject("SELECT id FROM usuario WHERE email=?",UUID.class,email);
            try(var c=jdbc.getDataSource().getConnection();var comando=c.createStatement()) {
                c.setAutoCommit(false);
                comando.execute("SET LOCAL ROLE synapse_app");
                try(var contexto=c.prepareStatement("SELECT set_config('app.usuario_id',?,true),set_config('app.papel','ATENDENTE',true),set_config('app.acesso_clientes','true',true)")) {
                    contexto.setString(1,usuario.toString());contexto.execute();
                }
                try(var linhas=comando.executeQuery("SELECT current_user,count(*) FROM atendimento_rota_canal")) {
                    assertThat(linhas.next()).isTrue();assertThat(linhas.getString(1)).isEqualTo("synapse_app");
                    assertThat(linhas.getInt(2)).isEqualTo(email.startsWith("ana")?2:0);
                }
                comando.execute("SELECT set_config('app.acesso_clientes','false',true)");
                try(var linhas=comando.executeQuery("SELECT count(*) FROM atendimento_rota_canal")) {
                    linhas.next();assertThat(linhas.getInt(1)).isZero();
                }
                org.assertj.core.api.Assertions.assertThatThrownBy(()->comando.execute("UPDATE atendimento_rota_canal SET credencial_historica_id=NULL"))
                        .isInstanceOf(java.sql.SQLException.class).hasMessageContaining("permission denied");
                c.rollback();
            }
        }
    }

    @Test void coletaInicialAutenticadaPreservaEventoSemBaixarBytesOuGastarTentativas() throws Exception {
        org.springframework.test.util.ReflectionTestUtils.setField(entradaOperacoes,"zApiSomenteColeta",true);
        try {
            var payload=json.readTree(getClass().getResourceAsStream("/z-api/received.json"));
            ((com.fasterxml.jackson.databind.node.ObjectNode)payload).put("instanceId","ZAPI-PROTEGIDA").put("messageId","coleta-"+lead);
            assertThat(http.postForEntity("/webhook/z-api?secret=errado",new HttpEntity<>(payload.toString(),headers),String.class).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(http.postForEntity("/webhook/z-api?secret="+SEGREDO,new HttpEntity<>(payload.toString(),headers),String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
            var antes=jdbc.queryForList("SELECT id_externo,tentativas,proxima_tentativa_em,processado_em FROM webhook_entrada");
            processador.processarPendentes();processador.processarPendentes();
            assertThat(jdbc.queryForList("SELECT id_externo,tentativas,proxima_tentativa_em,processado_em FROM webhook_entrada")).isEqualTo(antes);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM mensagem WHERE atendimento_id=?",Integer.class,atendimento)).isZero();
            verifyNoInteractions(cdn);
        } finally {org.springframework.test.util.ReflectionTestUtils.setField(entradaOperacoes,"zApiSomenteColeta",false);}
        processador.processarPendentes();processador.processarPendentes();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM mensagem WHERE atendimento_id=?",Integer.class,atendimento)).isEqualTo(1);
    }

    private UUID mensagemAntiga(String externo,UUID ciclo) {
        return mensagemComIdExterno(externo,ciclo,true);
    }
    private UUID mensagemComIdExterno(String externo,UUID ciclo,boolean anterior) {
        UUID id=UUID.randomUUID();
        jdbc.update("INSERT INTO mensagem(id,atendimento_id,remetente_tipo,tipo,conteudo,status_entrega,enviado_em) VALUES (?,?,'ATENDENTE','TEXTO','sintetico','ENVIADO',CASE WHEN ? THEN now()-interval '1 minute' ELSE now()+interval '1 second' END)",id,ciclo,anterior);
        jdbc.update("INSERT INTO mensagem_id_externo(wamid,mensagem_id,mensagem_enviada_em,atendimento_id) SELECT ?,id,enviado_em,atendimento_id FROM mensagem WHERE id=?",externo,id);
        return id;
    }
    private org.springframework.http.HttpStatusCode status(String id,String estado,String destino,String segredo) {
        String body="{\"entry\":[{\"changes\":[{\"value\":{\"metadata\":{\"phone_number_id\":\""+destino+"\"},\"statuses\":[{\"id\":\""+id+"\",\"status\":\""+estado+"\"}]}}]}]}";
        return http.postForEntity("/webhook/canal?secret="+segredo,new HttpEntity<>(body,headers),String.class).getStatusCode();
    }
    private void autenticar(String email) {
        headers.setBearerAuth(ApoioAutenticacao.login(http,email,ApoioAutenticacao.SENHA_ATENDENTE).accessToken());
    }
    private ResponseEntity<String> enviar(UUID l,UUID a) {
        return http.postForEntity("/api/v1/atendimentos/mensagens",new HttpEntity<>(Map.of("leadId",l,"atendimentoId",a,"conteudo","Continuidade sintética 🧪"),headers),String.class);
    }
    private org.testcontainers.containers.Container.ExecResult operar(String op,String confirmacao,String... extras) throws Exception {
        var args=new ArrayList<>(List.of("psql","-X","-U",POSTGRES.getUsername(),"-d",BANCO,"-v","banco_esperado="+BANCO,"-v","origem="+origem,"-v","destino="+CREDENCIAL,"-v","operacao="+op,"-v","confirmar="+confirmacao));
        for(String extra:extras){args.add("-v");args.add(extra);}
        args.addAll(List.of("-f","/tmp/transicionar-canal-z-api.sql"));
        return POSTGRES.execInContainer(args.toArray(String[]::new));
    }
}
