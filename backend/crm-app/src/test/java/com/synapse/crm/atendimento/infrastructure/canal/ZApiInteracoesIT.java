package com.synapse.crm.atendimento.infrastructure.canal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.synapse.crm.app.seguranca.ApoioAutenticacao;
import com.synapse.crm.atendimento.application.RepasseWebhookAutomacaoGateway;
import com.synapse.crm.atendimento.domain.evento.MensagemParaTempoReal;
import com.synapse.crm.atendimento.infrastructure.outbox.PublicadorDeRepasseWebhook;

/** HTTP de entrada/saida, jobs reais, PostgreSQL e eventos; somente transportes externos simulados. */
@TestPropertySource(properties={
    "synapse.canal.z-api.webhook.repasse-automacao-habilitado=true",
    "synapse.automacao.repasse-webhook.url=https://automacao.example.test/eventos",
    "synapse.automacao.repasse-webhook.token=repasse-exclusivo-sintetico"})
class ZApiInteracoesIT extends ZApiWebhookProtegidoIT {
    @Autowired RepasseWebhookAutomacaoGateway automacao;
    @Autowired PublicadorDeRepasseWebhook repasses;
    @Autowired ZApiAdapter saida;
    private RestClient httpAnterior, saidaAnterior;
    private MockRestServiceServer servidor, provedor;
    private UUID credencialLegada;

    @BeforeEach void transportes() {
        ReflectionTestUtils.setField(tradutor,"repasseHabilitado",true);
        httpAnterior=(RestClient)ReflectionTestUtils.getField(automacao,"http");
        saidaAnterior=(RestClient)ReflectionTestUtils.getField(saida,"http");
        var builder=RestClient.builder();servidor=MockRestServiceServer.bindTo(builder).build();
        ReflectionTestUtils.setField(automacao,"http",builder.build());
        var envio=RestClient.builder();provedor=MockRestServiceServer.bindTo(envio).build();
        ReflectionTestUtils.setField(saida,"http",envio.build());
    }
    @AfterEach void restaurar() {
        ReflectionTestUtils.setField(automacao,"http",httpAnterior);
        ReflectionTestUtils.setField(saida,"http",saidaAnterior);
        jdbc.update("DELETE FROM mensagem_envio_idempotencia WHERE atendimento_id=?",atendimento);
        if(credencialLegada!=null) {
            jdbc.update("DELETE FROM atendimento_rota_canal WHERE atendimento_id=?",atendimento);
            jdbc.update("DELETE FROM canal_transicao WHERE origem_credencial_id=?",credencialLegada);
            jdbc.update("UPDATE atendimento SET canal_credencial_id=? WHERE id=?",CREDENCIAL,atendimento);
            jdbc.update("DELETE FROM canal_credencial WHERE id=?",credencialLegada);
        }
        servidor.verify();provedor.verify();
    }

    @ParameterizedTest @ValueSource(strings={"buttonsResponseMessage","listResponseMessage"})
    void escolhaSemTextoPersisteRecarregaPublicaUmaVezERespondeComIdDoClique(String campo) throws Exception {
        var evento=escolha(campo,"clique-"+lead,"opcao-1");
        evento.put("referenceMessageId","original-ainda-desconhecida");
        receber(evento);receber(evento);processador.processarPendentes();processador.processarPendentes();
        UUID mensagem=idPorConteudo("Ótimo 🙂");
        assertThat(totalMensagens()).isEqualTo(1);
        String metadados=jdbc.queryForObject("SELECT midia_metadados::text FROM mensagem WHERE id=?",String.class,mensagem);
        assertThat(json.readTree(metadados).path("respostaInterativa").path("identificador").asText()).isEqualTo("opcao-1");
        assertThat(json.readTree(metadados).path("referenciaExterna").path("valor").asText()).isEqualTo("original-ainda-desconhecida");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM mensagem_referencia WHERE mensagem_id=?",Integer.class,mensagem)).isZero();
        assertThat(eventos.stream(MensagemParaTempoReal.class).filter(e->e.mensagemId().equals(mensagem)).count()).isEqualTo(1);
        UUID repasse=repasseId();
        esperarRepasse(evento,repasse,1,false);repasses.publicarPendentes();repasses.publicarPendentes();
        autenticar(ApoioAutenticacao.EMAIL_ANA);
        String historico=http.exchange("/api/v1/atendimentos/"+atendimento+"/mensagens",HttpMethod.GET,new HttpEntity<>(headers),String.class).getBody();
        assertThat(historico).contains(mensagem.toString(),"Ótimo", "respostaInterativa");
        autenticar(ApoioAutenticacao.EMAIL_BRUNO);
        assertThat(http.exchange("/api/v1/atendimentos/"+atendimento+"/mensagens",HttpMethod.GET,new HttpEntity<>(headers),String.class).getStatusCode()).isIn(HttpStatus.FORBIDDEN,HttpStatus.NOT_FOUND);
        autenticar(ApoioAutenticacao.EMAIL_ANA);
        provedor.expect(requestTo("https://api.z-api.io/instances/ZAPI-PROTEGIDA/token/token-api-teste/send-text"))
                .andExpect(jsonPath("$.messageId").value(evento.path("messageId").asText()))
                .andRespond(withSuccess("{\"zaapId\":\"processamento-resposta\",\"messageId\":\"resposta-whatsapp\"}",MediaType.APPLICATION_JSON));
        Instant horario=jdbc.queryForObject("SELECT enviado_em FROM mensagem WHERE id=?",java.sql.Timestamp.class,mensagem).toInstant();
        headers.set("Idempotency-Key",UUID.randomUUID().toString());
        var corpo=Map.of("leadId",lead,"atendimentoId",atendimento,"conteudo","Resposta sintetica","mensagemOrigemId",mensagem,"origemEnviadaEm",horario.toString());
        for(int i=0;i<2;i++) assertThat(http.postForEntity("/api/v1/atendimentos/mensagens",new HttpEntity<>(corpo,headers),String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
        publicador.publicarPendentes();publicador.publicarPendentes();
        assertThat(totalMensagens()).isEqualTo(2);
    }

    @Test void falhaN8nNaoBloqueiaHistoricoERetryPreservaEnvelopeEIdentidade() throws Exception {
        var evento=escolha("buttonsResponseMessage","clique-"+lead,"1");
        receber(evento);processador.processarPendentes();UUID repasse=repasseId();
        esperarRepasse(evento,repasse,1,true);esperarRepasse(evento,repasse,2,false);
        repasses.publicarPendentes();
        assertThat(totalMensagens()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT publicado_em IS NULL AND tentativas=1 FROM outbox_evento WHERE id=?",Boolean.class,repasse)).isTrue();
        jdbc.update("UPDATE outbox_evento SET proxima_tentativa_em=now()-interval '1 second' WHERE id=?",repasse);
        receber(evento);processador.processarPendentes();repasses.publicarPendentes();
        assertThat(repasseId()).isEqualTo(repasse);
        assertThat(jdbc.queryForObject("SELECT publicado_em IS NOT NULL FROM outbox_evento WHERE id=?",Boolean.class,repasse)).isTrue();
        assertThat(totalMensagens()).isEqualTo(1);
    }

    @Test void cliquesDistintosComMesmoRotuloNaoSaoDeduplicadosPeloButtonIdOuTexto() throws Exception {
        receber(escolha("buttonsResponseMessage","clique-A-"+lead,"1"));
        receber(escolha("buttonsResponseMessage","clique-B-"+lead,"2"));
        receber(escolha("buttonsResponseMessage","clique-C-"+lead,"1"));
        processador.processarPendentes();
        assertThat(totalMensagens()).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_evento WHERE tipo='automacao.webhook.repassar'",Integer.class)).isEqualTo(3);
        assertThat(jdbc.queryForList("SELECT midia_metadados->'respostaInterativa'->>'identificador' FROM mensagem WHERE atendimento_id=?",String.class,atendimento)).containsExactlyInAnyOrder("1","2","1");
    }

    @ParameterizedTest @ValueSource(booleans={false,true})
    void referenciaRecebidaParaMensagemEnviadaUsaMessageIdNaoZaapId(boolean migrado) throws Exception {
        if(migrado) migrarCiclo();
        UUID responsavel=jdbc.queryForObject("SELECT atendente_id FROM atendimento WHERE id=?",UUID.class,atendimento);
        ReflectionTestUtils.setField(tradutor,"repasseHabilitado",false);
        autenticar(ApoioAutenticacao.EMAIL_ANA);
        provedor.expect(requestTo("https://api.z-api.io/instances/ZAPI-PROTEGIDA/token/token-api-teste/send-text"))
                .andRespond(withSuccess("{\"zaapId\":\"processamento-original\",\"messageId\":\"whatsapp-original\"}",MediaType.APPLICATION_JSON));
        var envio=http.postForEntity("/api/v1/atendimentos/mensagens",new HttpEntity<>(Map.of("leadId",lead,"atendimentoId",atendimento,"conteudo","Original enviada"),headers),String.class);
        assertThat(envio.getStatusCode()).isEqualTo(HttpStatus.OK);
        UUID origem=UUID.fromString(json.readTree(envio.getBody()).path("mensagemId").asText());
        publicador.publicarPendentes();
        var clique=escolha("buttonsResponseMessage","clique-"+lead,"1").put("referenceMessageId","whatsapp-original");
        receber(clique);processador.processarPendentes();
        assertThat(jdbc.queryForObject("SELECT origem_mensagem_id FROM mensagem_referencia WHERE mensagem_id=?",UUID.class,idPorConteudo("Ótimo 🙂"))).isEqualTo(origem);
        assertThat(jdbc.queryForObject("SELECT atendente_id FROM atendimento WHERE id=?",UUID.class,atendimento)).isEqualTo(responsavel);
        if(migrado)assertThat(jdbc.queryForObject("SELECT canal_credencial_id FROM atendimento WHERE id=?",UUID.class,atendimento)).isEqualTo(credencialLegada);
    }

    @ParameterizedTest @ValueSource(strings={"image","audio","video","document"})
    void midiaRecebidaConservaCitacaoEMetadadosDoArquivo(String tipo) throws Exception {
        ReflectionTestUtils.setField(tradutor,"repasseHabilitado",false);
        var origem=base("original-"+lead);origem.putObject("text").put("message","Original sintetica");
        receber(origem);processador.processarPendentes();UUID idOrigem=idPorConteudo("Original sintetica");
        byte[] bytes;
        String recurso=tipo.equals("video")?"/midia/chat-interno/video-validacao.mp4":tipo.equals("audio")?"/z-api/audio-sintetico.ogg":tipo.equals("document")?"/z-api/documento-sintetico.pdf":null;
        if(recurso!=null)try(var f=getClass().getResourceAsStream(recurso)){bytes=f.readAllBytes();}
        else bytes=java.util.Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+juxkAAAAASUVORK5CYII=");
        String url="https://cdn.exemplo.test/arquivo?assinatura=sintetica";
        var chamada=org.mockito.Mockito.mock(okhttp3.Call.class);
        org.mockito.Mockito.when(cdn.newCall(org.mockito.ArgumentMatchers.any())).thenReturn(chamada);
        org.mockito.Mockito.when(chamada.execute()).thenReturn(new okhttp3.Response.Builder().request(new okhttp3.Request.Builder().url(url).build()).protocol(okhttp3.Protocol.HTTP_1_1).code(200).message("teste").body(okhttp3.ResponseBody.create(bytes,okhttp3.MediaType.get("application/octet-stream"))).build());
        var evento=base("midia-citada-"+lead).put("referenceMessageId",origem.path("messageId").asText());
        evento.putObject(tipo).put(tipo+"Url",url).put("fileName","arquivo-sintetico");
        receber(evento);processador.processarPendentes();
        UUID mensagem=jdbc.queryForObject("SELECT id FROM mensagem WHERE atendimento_id=? AND tipo<>'TEXTO'",UUID.class,atendimento);
        assertThat(jdbc.queryForObject("SELECT origem_mensagem_id FROM mensagem_referencia WHERE mensagem_id=?",UUID.class,mensagem)).isEqualTo(idOrigem);
        var dados=json.readTree(jdbc.queryForObject("SELECT midia_metadados::text FROM mensagem WHERE id=?",String.class,mensagem));
        assertThat(dados.path("referenciaExterna").path("valor").asText()).isEqualTo(origem.path("messageId").asText());
        assertThat(dados.path("tamanho").asInt()).isEqualTo(bytes.length);
        // Responder a midia usa o ID WhatsApp da propria midia, nao o ID que ela citava.
        autenticar(ApoioAutenticacao.EMAIL_ANA);
        provedor.expect(requestTo("https://api.z-api.io/instances/ZAPI-PROTEGIDA/token/token-api-teste/send-text"))
                .andExpect(jsonPath("$.messageId").value(evento.path("messageId").asText()))
                .andRespond(withSuccess("{\"zaapId\":\"processamento-resposta-midia\",\"messageId\":\"resposta-midia-whatsapp\"}",MediaType.APPLICATION_JSON));
        Instant horario=jdbc.queryForObject("SELECT enviado_em FROM mensagem WHERE id=?",java.sql.Timestamp.class,mensagem).toInstant();
        var resposta=http.postForEntity("/api/v1/atendimentos/mensagens",new HttpEntity<>(Map.of("leadId",lead,"atendimentoId",atendimento,"conteudo","Resposta a midia sintetica","mensagemOrigemId",mensagem,"origemEnviadaEm",horario.toString()),headers),String.class);
        assertThat(resposta.getStatusCode()).as(resposta.getBody()).isEqualTo(HttpStatus.OK);
        publicador.publicarPendentes();publicador.publicarPendentes();
    }

    @ParameterizedTest @ValueSource(strings={"provedor","namespace","conflito","outroLead"})
    void referenciaIncompativelOuDeOutraConversaNaoApontaParaOrigem(String caso) throws Exception {
        ReflectionTestUtils.setField(tradutor,"repasseHabilitado",false);
        var origem=base("original-"+lead);origem.putObject("text").put("message","Original sintetica");
        receber(origem);processador.processarPendentes();UUID idOrigem=idPorConteudo("Original sintetica");
        UUID outroLead=null,outroAtendimento=null;
        try {
            if(caso.equals("outroLead")) {
                outroAtendimento=UUID.randomUUID();String telefone;
                do {
                    outroLead=UUID.randomUUID();
                    telefone="5561"+String.format("%09d",Math.floorMod(outroLead.getLeastSignificantBits(),1_000_000_000L));
                } while(Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM lead WHERE telefone=?)",Boolean.class,telefone)));
                jdbc.update("INSERT INTO lead(id,nome,telefone,status_basico) VALUES (?,?,?,'IA')",outroLead,PREFIXO+outroLead,telefone);
                jdbc.update("INSERT INTO atendimento(id,lead_id,canal_credencial_id,status) VALUES (?,?,?,'EM_ATENDIMENTO')",outroAtendimento,outroLead,CREDENCIAL);
                jdbc.update("UPDATE mensagem SET atendimento_id=? WHERE id=?",outroAtendimento,idOrigem);
                jdbc.update("UPDATE mensagem_correlacao_canal SET atendimento_id=? WHERE mensagem_id=?",outroAtendimento,idOrigem);
            } else if(caso.equals("provedor"))jdbc.update("UPDATE mensagem_correlacao_canal SET provedor='uzapi-autotic' WHERE mensagem_id=?",idOrigem);
            else if(caso.equals("namespace"))jdbc.update("UPDATE mensagem_correlacao_canal SET namespace='PROCESSAMENTO' WHERE mensagem_id=?",idOrigem);
            else jdbc.update("UPDATE mensagem_correlacao_canal SET conflitante=true WHERE mensagem_id=?",idOrigem);
            var clique=escolha("buttonsResponseMessage","clique-"+lead,"1").put("referenceMessageId",origem.path("messageId").asText());
            receber(clique);processador.processarPendentes();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM mensagem_referencia WHERE mensagem_id=?",Integer.class,idPorConteudo("Ótimo 🙂"))).isZero();
        } finally {
            if(outroLead!=null) {
                jdbc.update("UPDATE mensagem SET atendimento_id=? WHERE id=?",atendimento,idOrigem);
                jdbc.update("UPDATE mensagem_correlacao_canal SET atendimento_id=? WHERE mensagem_id=?",atendimento,idOrigem);
                jdbc.update("DELETE FROM atendimento WHERE id=?",outroAtendimento);jdbc.update("DELETE FROM lead WHERE id=?",outroLead);
            }
        }
    }

    @ParameterizedTest @ValueSource(strings={"text","buttonsResponseMessage","listResponseMessage"})
    void referenciaRecebidaResolveSomenteAliasExatoNoMesmoLead(String tipo) throws Exception {
        ReflectionTestUtils.setField(tradutor,"repasseHabilitado",false);
        var origem=base("original-"+lead);origem.putObject("text").put("message","Original sintetica");
        receber(origem);processador.processarPendentes();UUID idOrigem=idPorConteudo("Original sintetica");
        var resposta=tipo.equals("text")?base("resposta-"+lead):escolha(tipo,"resposta-"+lead,"1");
        if(tipo.equals("text"))resposta.putObject("text").put("message","Ótimo 🙂");
        resposta.put("referenceMessageId",origem.path("messageId").asText());
        receber(resposta);processador.processarPendentes();UUID id=idPorConteudo("Ótimo 🙂");
        assertThat(jdbc.queryForObject("SELECT origem_mensagem_id FROM mensagem_referencia WHERE mensagem_id=?",UUID.class,id)).isEqualTo(idOrigem);
        // Mesmo ID externo em outro namespace/provedor/instancia nunca e fallback.
        jdbc.update("UPDATE mensagem_correlacao_canal SET instancia='OUTRA' WHERE mensagem_id=?",idOrigem);
        resposta.put("messageId","sem-alias-"+lead);receber(resposta);processador.processarPendentes();
        assertThat(totalMensagens()).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM mensagem_referencia r JOIN mensagem m ON m.id=r.mensagem_id WHERE m.atendimento_id=?",Integer.class,atendimento)).isEqualTo(1);
        jdbc.update("UPDATE mensagem_correlacao_canal SET instancia='ZAPI-PROTEGIDA' WHERE mensagem_id=?",idOrigem);
    }

    @ParameterizedTest @ValueSource(strings={"fromMe","isGroup","instanceId","incompleto"})
    void entradaNegativaNaoCriaMensagemNemRepasse(String caso) throws Exception {
        var evento=escolha("buttonsResponseMessage","negativo-"+lead,"1");
        if(caso.equals("instanceId"))evento.put(caso,"OUTRA");
        else if(caso.equals("incompleto"))((ObjectNode)evento.path("buttonsResponseMessage")).remove("message");
        else evento.put(caso,true);
        var resposta=http.postForEntity("/webhook/z-api?secret="+SEGREDO,new HttpEntity<>(evento.toString(),headers),String.class);
        assertThat(resposta.getStatusCode()).isEqualTo(caso.equals("instanceId")?HttpStatus.FORBIDDEN:HttpStatus.OK);
        processador.processarPendentes();assertThat(totalMensagens()).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_evento WHERE tipo='automacao.webhook.repassar'",Integer.class)).isZero();
        if(caso.equals("incompleto"))assertThat(jdbc.queryForObject("SELECT itens_descartados FROM webhook_entrada",Integer.class)).isEqualTo(1);
    }

    private ObjectNode base(String id) throws Exception {
        try(var f=getClass().getResourceAsStream("/z-api/received.json")) {
            var evento=(ObjectNode)json.readTree(f);evento.remove("text");
            return evento.put("instanceId","ZAPI-PROTEGIDA").put("messageId",id);
        }
    }
    private ObjectNode escolha(String campo,String id,String opcao) throws Exception {
        var evento=base(id);evento.putObject(campo).put(campo.equals("buttonsResponseMessage")?"buttonId":"selectedRowId",opcao).put("message","Ótimo 🙂");
        if(campo.equals("listResponseMessage"))((ObjectNode)evento.path(campo)).put("title","Escolha sintetica");
        return evento;
    }
    private void receber(ObjectNode evento) {
        assertThat(http.postForEntity("/webhook/z-api?secret="+SEGREDO,new HttpEntity<>(evento.toString(),headers),String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
    }
    private int totalMensagens(){return jdbc.queryForObject("SELECT count(*) FROM mensagem WHERE atendimento_id=?",Integer.class,atendimento);}
    private UUID idPorConteudo(String texto){return jdbc.queryForObject("SELECT id FROM mensagem WHERE atendimento_id=? AND conteudo=?",UUID.class,atendimento,texto);}
    private UUID repasseId(){return jdbc.queryForObject("SELECT id FROM outbox_evento WHERE tipo='automacao.webhook.repassar'",UUID.class);}
    private void autenticar(String email){headers.setBearerAuth(ApoioAutenticacao.login(http,email,ApoioAutenticacao.SENHA_ATENDENTE).accessToken());}
    private void migrarCiclo() {
        credencialLegada=UUID.randomUUID();UUID canal=UUID.randomUUID();
        jdbc.update("INSERT INTO canal(id,nome,tipo) VALUES (?,?,'WHATSAPP')",canal,PREFIXO+canal);
        jdbc.update("INSERT INTO canal_credencial(id,canal_id,numero,identificador_externo,token_ref) VALUES (?,?,'5561999990000','UZAPI-ANTIGA','env://UZAPI_TOKEN')",credencialLegada,canal);
        jdbc.update("UPDATE atendimento SET canal_credencial_id=? WHERE id=?",credencialLegada,atendimento);
        UUID transicao=jdbc.queryForObject("INSERT INTO canal_transicao(origem_credencial_id,destino_credencial_id,provedor_origem,provedor_destino,identificador_origem,identificador_destino,estado) VALUES (?,?,'uzapi-autotic','z-api','UZAPI-ANTIGA','ZAPI-PROTEGIDA','ATIVA') RETURNING id",UUID.class,credencialLegada,CREDENCIAL);
        jdbc.update("INSERT INTO atendimento_rota_canal(atendimento_id,transicao_id,credencial_historica_id) VALUES (?,?,?)",atendimento,transicao,credencialLegada);
    }
    private void esperarRepasse(ObjectNode evento,UUID id,int tentativa,boolean falha) {
        servidor.expect(requestTo("https://automacao.example.test/eventos"))
                .andExpect(method(HttpMethod.POST)).andExpect(content().json(evento.toString(),true))
                .andExpect(header("X-Synapse-Evento-Id",id.toString())).andExpect(header("X-Synapse-Tentativa",Integer.toString(tentativa)))
                .andExpect(header("X-Synapse-Webhook-Token","repasse-exclusivo-sintetico"))
                .andRespond(falha?withServerError():withSuccess());
    }
}
