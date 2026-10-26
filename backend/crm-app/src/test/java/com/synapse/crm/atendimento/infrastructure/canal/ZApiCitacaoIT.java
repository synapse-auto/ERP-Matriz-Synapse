package com.synapse.crm.atendimento.infrastructure.canal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.synapse.crm.app.seguranca.ApoioAutenticacao;

/** Webhook HTTP real -> job -> PostgreSQL -> API de resposta -> outbox -> adapter/HTTP. */
class ZApiCitacaoIT extends ZApiWebhookProtegidoIT {
    @Autowired ZApiAdapter saida;
    private RestClient anterior;
    private MockRestServiceServer servidor;
    private UUID credencialAntiga;
    private String idRecebido;

    @BeforeEach void instalarTransporteSomenteDeSaida() {
        idRecebido="original-zapi-"+lead;
        anterior=(RestClient)ReflectionTestUtils.getField(saida,"http");
        var cliente=RestClient.builder();servidor=MockRestServiceServer.bindTo(cliente).build();
        ReflectionTestUtils.setField(saida,"http",cliente.build());
    }
    @AfterEach void restaurarTransporteERota() {
        ReflectionTestUtils.setField(saida,"http",anterior);
        jdbc.update("DELETE FROM mensagem_envio_idempotencia WHERE atendimento_id=?",atendimento);
        if(credencialAntiga!=null) {
            jdbc.update("DELETE FROM atendimento_rota_canal WHERE atendimento_id=?",atendimento);
            jdbc.update("DELETE FROM canal_transicao");
            jdbc.update("UPDATE atendimento SET canal_credencial_id=? WHERE id=?",CREDENCIAL,atendimento);
            jdbc.update("DELETE FROM canal_credencial WHERE id=?",credencialAntiga);
        }
        servidor.verify();
    }

    @Test void recebidaRecarregadaEDeduplicadaRespondeComMessageIdOriginalUmaVez() throws Exception {
        UUID origem=receber();
        assertThat(jdbc.queryForObject("SELECT wamid FROM mensagem_id_externo WHERE mensagem_id=?",String.class,origem))
                .startsWith("canal:").isNotEqualTo(idRecebido);
        assertThat(jdbc.queryForObject("SELECT id_externo FROM mensagem_correlacao_canal WHERE mensagem_id=? AND namespace='WHATSAPP'",String.class,origem))
                .isEqualTo(idRecebido);
        autenticar(ApoioAutenticacao.EMAIL_ANA);
        assertThat(http.exchange("/api/v1/atendimentos/"+atendimento+"/mensagens",HttpMethod.GET,new HttpEntity<>(headers),String.class).getBody())
                .contains(origem.toString());
        esperar(idRecebido,"resposta-w");
        headers.set("Idempotency-Key",UUID.randomUUID().toString());
        UUID resposta=responder(origem);
        assertThat(responder(origem)).isEqualTo(resposta);
        publicador.publicarPendentes();publicador.publicarPendentes();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM mensagem WHERE atendimento_id=?",Integer.class,atendimento)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT origem_mensagem_id FROM mensagem_referencia WHERE mensagem_id=?",UUID.class,resposta)).isEqualTo(origem);
        assertThat(http.exchange("/api/v1/atendimentos/"+atendimento+"/mensagens",HttpMethod.GET,new HttpEntity<>(headers),String.class).getBody())
                .contains("citacao",origem.toString());
    }

    @Test void mensagemEnviadaTemNamespaceWhatsappERecebeRespostaCitada() throws Exception {
        autenticar(ApoioAutenticacao.EMAIL_ANA);
        esperar(null,"enviada-w");
        esperar("enviada-w","segunda-w");
        var envio=http.postForEntity("/api/v1/atendimentos/mensagens",new HttpEntity<>(Map.of("leadId",lead,"atendimentoId",atendimento,"conteudo","primeira sintetica"),headers),Map.class);
        assertThat(envio.getStatusCode()).isEqualTo(HttpStatus.OK);
        UUID origem=UUID.fromString(envio.getBody().get("mensagemId").toString());
        publicador.publicarPendentes();
        responder(origem);publicador.publicarPendentes();publicador.publicarPendentes();
    }

    @Test void cicloMigradoUsaMensagemNovaZapiSemReescreverHistoricoOuResponsavel() throws Exception {
        migrar();
        UUID dona=jdbc.queryForObject("SELECT atendente_id FROM atendimento WHERE id=?",UUID.class,atendimento);
        UUID origem=receber();autenticar(ApoioAutenticacao.EMAIL_ANA);esperar(idRecebido,"migrada-w");
        responder(origem);publicador.publicarPendentes();
        assertThat(jdbc.queryForObject("SELECT canal_credencial_id FROM atendimento WHERE id=?",UUID.class,atendimento)).isEqualTo(credencialAntiga);
        assertThat(jdbc.queryForObject("SELECT atendente_id FROM atendimento WHERE id=?",UUID.class,atendimento)).isEqualTo(dona);
    }

    @ParameterizedTest @ValueSource(strings={"uzapi","instancia","namespace","conflito"})
    void referenciaIncompativelRecusaAntesDaOutboxComOrientacaoSemCodigoTecnico(String caso) throws Exception {
        UUID origem=receber();
        switch(caso) {
            case "uzapi" -> jdbc.update("UPDATE mensagem_correlacao_canal SET provedor='uzapi-autotic' WHERE mensagem_id=?",origem);
            case "instancia" -> jdbc.update("UPDATE mensagem_correlacao_canal SET instancia='OUTRA' WHERE mensagem_id=?",origem);
            case "namespace" -> jdbc.update("UPDATE mensagem_correlacao_canal SET namespace='PROCESSAMENTO' WHERE mensagem_id=?",origem);
            default -> jdbc.update("UPDATE mensagem_correlacao_canal SET conflitante=true WHERE mensagem_id=?",origem);
        }
        autenticar(ApoioAutenticacao.EMAIL_ANA);
        var resposta=postResposta(origem);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(resposta.getBody()).contains("Remova a citação").doesNotContain("Z_API_CITACAO_SEM_CORRELACAO");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_evento WHERE tipo='canal.mensagem.enviar'",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM mensagem WHERE atendimento_id=?",Integer.class,atendimento)).isEqualTo(1);
    }

    @Test void outroAtendenteNaoPodeUsarIdConhecidoDoLead() throws Exception {
        UUID origem=receber();autenticar(ApoioAutenticacao.EMAIL_BRUNO);
        assertThat(postResposta(origem).getStatusCode()).isIn(HttpStatus.FORBIDDEN,HttpStatus.NOT_FOUND);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_evento WHERE tipo='canal.mensagem.enviar'",Integer.class)).isZero();
    }

    @Test void idLegadoIgualAoAliasDeOutraMensagemZapiNaoTrocaOrigem() throws Exception {
        UUID origem=receber(),outra=UUID.randomUUID();
        jdbc.update("UPDATE mensagem_correlacao_canal SET provedor='uzapi-autotic' WHERE mensagem_id=?",origem);
        jdbc.update("UPDATE mensagem_id_externo SET wamid='id-coincidente' WHERE mensagem_id=?",origem);
        jdbc.update("INSERT INTO mensagem(id,atendimento_id,remetente_tipo,tipo,conteudo,status_entrega,enviado_em) VALUES (?,?,'LEAD','TEXTO','outra sintetica','ENTREGUE',now())",outra,atendimento);
        jdbc.update("INSERT INTO mensagem_correlacao_canal(provedor,instancia,namespace,id_externo,mensagem_id,mensagem_enviada_em,atendimento_id) SELECT 'z-api','ZAPI-PROTEGIDA','WHATSAPP','id-coincidente',id,enviado_em,atendimento_id FROM mensagem WHERE id=?",outra);
        autenticar(ApoioAutenticacao.EMAIL_ANA);
        assertThat(postResposta(origem).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_evento WHERE tipo='canal.mensagem.enviar'",Integer.class)).isZero();
    }

    @Test void participanteConsentidoRespondeSemHerdarResponsabilidade() throws Exception {
        UUID origem=receber();
        UUID dona=jdbc.queryForObject("SELECT atendente_id FROM atendimento WHERE id=?",UUID.class,atendimento);
        UUID bruno=jdbc.queryForObject("SELECT id FROM usuario WHERE email=?",UUID.class,ApoioAutenticacao.EMAIL_BRUNO);
        jdbc.update("INSERT INTO atendimento_participante(atendimento_id,usuario_id,origem) VALUES (?,?,'CONVITE')",atendimento,bruno);
        autenticar(ApoioAutenticacao.EMAIL_BRUNO);esperar(idRecebido,"participante-w");responder(origem);publicador.publicarPendentes();
        assertThat(jdbc.queryForObject("SELECT atendente_id FROM atendimento WHERE id=?",UUID.class,atendimento)).isEqualTo(dona);
        jdbc.update("DELETE FROM atendimento_participante WHERE atendimento_id=?",atendimento);
    }

    @Test void conflitoEntreApiEWorkerNaoRemoveCitacaoNemDisparaHttp() throws Exception {
        UUID origem=receber();autenticar(ApoioAutenticacao.EMAIL_ANA);
        UUID resposta=responder(origem);
        jdbc.update("UPDATE mensagem_correlacao_canal SET conflitante=true WHERE mensagem_id=?",origem);
        publicador.publicarPendentes();publicador.publicarPendentes();
        assertThat(jdbc.queryForObject("SELECT status_entrega::text FROM mensagem WHERE id=?",String.class,resposta)).isEqualTo("FALHOU");
        assertThat(jdbc.queryForObject("SELECT origem_mensagem_id FROM mensagem_referencia WHERE mensagem_id=?",UUID.class,resposta)).isEqualTo(origem);
    }

    @Test void rlsLeSomenteAliasWhatsappDaCarteiraENaoAutorizaEscrever() throws Exception {
        UUID origem=receber();
        jdbc.update("INSERT INTO mensagem_correlacao_canal(provedor,instancia,namespace,id_externo,mensagem_id,mensagem_enviada_em,atendimento_id) SELECT provedor,instancia,'PROCESSAMENTO',id_externo,mensagem_id,mensagem_enviada_em,atendimento_id FROM mensagem_correlacao_canal WHERE mensagem_id=?",origem);
        try(var conexao=jdbc.getDataSource().getConnection()) {
            conexao.setAutoCommit(false);
            try(var sql=conexao.createStatement()) {
                sql.execute("SET LOCAL ROLE synapse_app");
                sql.execute("SELECT set_config('app.papel','ATENDENTE',true),set_config('app.acesso_clientes','true',true)");
                for(String email:java.util.List.of(ApoioAutenticacao.EMAIL_ANA,ApoioAutenticacao.EMAIL_BRUNO)) {
                    UUID usuario=jdbc.queryForObject("SELECT id FROM usuario WHERE email=?",UUID.class,email);
                    sql.execute("SELECT set_config('app.usuario_id','"+usuario+"',true)");
                    try(var r=sql.executeQuery("SELECT count(*) FROM mensagem_correlacao_canal WHERE mensagem_id='"+origem+"'")) {
                        assertThat(r.next()).isTrue();assertThat(r.getInt(1)).isEqualTo(email.equals(ApoioAutenticacao.EMAIL_ANA)?1:0);
                    }
                }
                UUID ana=jdbc.queryForObject("SELECT id FROM usuario WHERE email=?",UUID.class,ApoioAutenticacao.EMAIL_ANA);
                sql.execute("SELECT set_config('app.usuario_id','"+ana+"',true),set_config('app.acesso_clientes','false',true)");
                try(var r=sql.executeQuery("SELECT count(*) FROM mensagem_correlacao_canal WHERE mensagem_id='"+origem+"'")){assertThat(r.next()).isTrue();assertThat(r.getInt(1)).isZero();}
                sql.execute("SELECT set_config('app.acesso_clientes','true',true)");
                assertThat(sql.executeUpdate("UPDATE mensagem_correlacao_canal SET conflitante=true WHERE mensagem_id='"+origem+"'")).isZero();
                assertThat(sql.executeUpdate("DELETE FROM mensagem_correlacao_canal WHERE mensagem_id='"+origem+"'")).isZero();
                assertThatThrownBy(()->sql.executeUpdate("INSERT INTO mensagem_correlacao_canal(provedor,instancia,namespace,id_externo,mensagem_id,mensagem_enviada_em,atendimento_id) SELECT provedor,instancia,namespace,'negativo',mensagem_id,mensagem_enviada_em,atendimento_id FROM mensagem_correlacao_canal WHERE mensagem_id='"+origem+"' AND namespace='WHATSAPP'"))
                        .isInstanceOf(java.sql.SQLException.class).extracting(e->((java.sql.SQLException)e).getSQLState()).isEqualTo("42501");
            } finally {conexao.rollback();}
        }
    }

    @Test void audioComCitacaoSemContratoRecusaAntesDaOutboxPreservandoOrigem() throws Exception {
        UUID origem=receber();autenticar(ApoioAutenticacao.EMAIL_ANA);
        Instant enviado=jdbc.queryForObject("SELECT enviado_em FROM mensagem WHERE id=?",java.sql.Timestamp.class,origem).toInstant();
        var partes=new org.springframework.util.LinkedMultiValueMap<String,Object>();
        try(var f=getClass().getResourceAsStream("/z-api/audio-sintetico.ogg")) {
            partes.add("arquivo",new org.springframework.core.io.ByteArrayResource(f.readAllBytes()){@Override public String getFilename(){return "rascunho.ogg";}});
        }
        var upload=new HttpHeaders();upload.setBearerAuth(headers.getFirst(HttpHeaders.AUTHORIZATION).substring(7));upload.setContentType(MediaType.MULTIPART_FORM_DATA);
        var resposta=http.postForEntity("/api/v1/atendimentos/"+atendimento+"/mensagens/midia?mensagemOrigemId="+origem+"&origemEnviadaEm="+enviado,
                new HttpEntity<>(partes,upload),String.class);
        assertThat(resposta.getStatusCode()).as(resposta.getBody()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(resposta.getBody()).contains("CITACAO_NAO_SUPORTADA_PELO_CANAL");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_evento WHERE tipo='canal.mensagem.enviar'",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM mensagem WHERE atendimento_id=?",Integer.class,atendimento)).isEqualTo(1);
    }

    private UUID receber() throws Exception {
        try(var f=getClass().getResourceAsStream("/z-api/received.json")) {
            var payload=(com.fasterxml.jackson.databind.node.ObjectNode)json.readTree(f);
            payload.put("instanceId","ZAPI-PROTEGIDA").put("messageId",idRecebido);
            for(int i=0;i<2;i++) assertThat(http.postForEntity("/webhook/z-api?secret="+SEGREDO,new HttpEntity<>(payload.toString(),headers),String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
        }
        processador.processarPendentes();
        return jdbc.queryForObject("SELECT id FROM mensagem WHERE atendimento_id=?",UUID.class,atendimento);
    }
    private void autenticar(String email) {headers.setBearerAuth(ApoioAutenticacao.login(http,email,ApoioAutenticacao.SENHA_ATENDENTE).accessToken());}
    private ResponseEntity<String> postResposta(UUID origem) {
        Instant enviado=jdbc.queryForObject("SELECT enviado_em FROM mensagem WHERE id=?",java.sql.Timestamp.class,origem).toInstant();
        return http.postForEntity("/api/v1/atendimentos/mensagens",new HttpEntity<>(Map.of("leadId",lead,"atendimentoId",atendimento,"conteudo","resposta sintetica","mensagemOrigemId",origem,"origemEnviadaEm",enviado.toString()),headers),String.class);
    }
    private UUID responder(UUID origem) throws Exception {
        var resposta=postResposta(origem);assertThat(resposta.getStatusCode()).as(resposta.getBody()).isEqualTo(HttpStatus.OK);
        return UUID.fromString(json.readTree(resposta.getBody()).path("mensagemId").asText());
    }
    private void esperar(String citada,String id) {
        var expectativa=servidor.expect(requestTo("https://api.z-api.io/instances/ZAPI-PROTEGIDA/token/token-api-teste/send-text"))
                .andExpect(method(HttpMethod.POST)).andExpect(header("Client-Token","client-api-teste"));
        if(citada==null) expectativa.andExpect(jsonPath("$.messageId").doesNotExist());
        else expectativa.andExpect(jsonPath("$.messageId").value(citada));
        expectativa.andRespond(withSuccess("{\"zaapId\":\"p-"+id+"\",\"messageId\":\""+id+"\"}",MediaType.APPLICATION_JSON));
    }
    private void migrar() {
        credencialAntiga=UUID.randomUUID();
        UUID canal=UUID.randomUUID();
        jdbc.update("INSERT INTO canal(id,nome,tipo,ativo) VALUES (?,?,'WHATSAPP',true)",canal,PREFIXO+"legado-"+canal.toString().substring(0,8));
        jdbc.update("INSERT INTO canal_credencial(id,canal_id,numero,identificador_externo,token_ref) VALUES (?,?,'5561999990000','UZAPI-ANTIGA','env://UZAPI_TOKEN')",credencialAntiga,canal);
        jdbc.update("UPDATE atendimento SET canal_credencial_id=? WHERE id=?",credencialAntiga,atendimento);
        UUID transicao=jdbc.queryForObject("INSERT INTO canal_transicao(origem_credencial_id,destino_credencial_id,provedor_origem,provedor_destino,identificador_origem,identificador_destino,estado) VALUES (?,?,'uzapi-autotic','z-api','UZAPI-ANTIGA','ZAPI-PROTEGIDA','ATIVA') RETURNING id",UUID.class,credencialAntiga,CREDENCIAL);
        jdbc.update("INSERT INTO atendimento_rota_canal(atendimento_id,transicao_id,credencial_historica_id) VALUES (?,?,?)",atendimento,transicao,credencialAntiga);
    }
}
