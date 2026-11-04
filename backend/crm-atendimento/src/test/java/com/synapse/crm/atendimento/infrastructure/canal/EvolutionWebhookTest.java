package com.synapse.crm.atendimento.infrastructure.canal;

import static org.assertj.core.api.Assertions.*;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class EvolutionWebhookTest {
    static final ObjectMapper JSON=new ObjectMapper();
    static final UUID CREDENCIAL=UUID.fromString("44444444-4444-4444-4444-444444444444");
    static final String SEGREDO="segredo-sintetico-exclusivo-evolution-com-43-caracteres";
    static EvolutionProperties config(){return new EvolutionProperties(true,"https://evolution.example.test","evo-teste","chave-sintetica",CREDENCIAL,SEGREDO,Duration.ofSeconds(2),1,4096,65536,false,true);}
    static ObjectNode recebido(String id){
        var n=JSON.createObjectNode().put("event","messages.upsert").put("instance","evo-teste").put("apikey","nunca-persistir").put("destination","https://privado/?secret=segredo");
        var d=n.putObject("data").put("pushName","Contato sintetico").put("messageTimestamp",1793793600);
        d.putObject("key").put("remoteJid","5561999990000@s.whatsapp.net").put("fromMe",false).put("id",id);
        d.putObject("message").put("conversation","Texto sintetico");return n;
    }
    @Test void textoTemIdentidadeCompostaCitacaoEDeduplicacaoIndependenteDoEnvelope() {
        var t=new EvolutionWebhookTradutor(JSON,config());var n=recebido("w-recebido");var original=t.idsExternos(n.toString());
        var m=(ObjectNode)n.path("data").path("message");m.remove("conversation");m.putObject("extendedTextMessage").put("text","Resposta").putObject("contextInfo").put("stanzaId","w-original");
        var mensagem=t.traduzir(n.toString()).getFirst();assertThat(mensagem.contextoWamid()).isEqualTo("w-original");
        assertThat(mensagem.metadados()).contains("referenciaExterna","evolution","evo-teste","WHATSAPP","w-original");
        assertThat(t.identidadeDaMensagem(n.toString(),mensagem.idExterno()).orElseThrow().valor()).isEqualTo("w-recebido");
        n.put("date_time","outro-horario-de-retry");assertThat(t.idsExternos(n.toString())).isEqualTo(original);
    }
    @ParameterizedTest @ValueSource(strings={"buttonsResponseMessage","listResponseMessage","templateButtonReplyMessage","interactiveResponseMessage"})
    void escolhaSemTextoComumNaoDependeDaReferencia(String campo) {
        var n=recebido("clique");var m=(ObjectNode)n.path("data").path("message");m.remove("conversation");var escolha=m.putObject(campo);
        if(campo.startsWith("buttons"))escolha.put("selectedDisplayText","Opcao").put("selectedButtonId","1");
        else if(campo.equals("templateButtonReplyMessage"))escolha.put("selectedDisplayText","Opcao").put("selectedId","3");
        else if(campo.equals("interactiveResponseMessage")){escolha.putObject("body").put("text","Opcao");escolha.putObject("nativeFlowResponseMessage").put("name","quick_reply").put("paramsJson","{\"campoOpaco\":\"nao-inferir\"}");}
        else escolha.put("title","Opcao").putObject("singleSelectReply").put("selectedRowId","2");
        var t=new EvolutionWebhookTradutor(JSON,config());var msg=t.traduzir(n.toString()).getFirst();
        assertThat(msg.texto()).isEqualTo("Opcao");assertThat(msg.metadados()).contains(campo.equals("interactiveResponseMessage")?"NATIVE_FLOW":campo.equals("listResponseMessage")?"selectedRowId":"buttonId");
        assertThat(t.repasseSemGrupos(n.toString(),null)).isPresent();
    }
    @ParameterizedTest @ValueSource(strings={"SERVER_ACK","DELIVERY_ACK","READ","PLAYED","ERROR","DESCONHECIDO"})
    void statusUsaKeyIdSemInventarAlias(String status) {
        var n=JSON.createObjectNode().put("instance","evo-teste").put("event","messages.update");
        n.putObject("data").put("keyId","wa-id").put("remoteJid","5561999990000@s.whatsapp.net").put("fromMe",true).put("status",status);
        var resultado=new EvolutionWebhookTradutor(JSON,config()).statusComIdentidade(n.toString(),Instant.EPOCH);
        if(status.equals("DESCONHECIDO"))assertThat(resultado).isEmpty();else {
            assertThat(resultado).hasSize(1);assertThat(resultado.getFirst().identidade().valor()).isEqualTo("wa-id");
        }
    }
    @ParameterizedTest @ValueSource(strings={"5561999990000@g.us","opaque@lid","status@broadcast"})
    void identidadeNaoTelefonicaNaoCriaMensagemNemRepasse(String jid) {
        var n=recebido("w1");((ObjectNode)n.path("data").path("key")).put("remoteJid",jid);
        var t=new EvolutionWebhookTradutor(JSON,config());assertThat(t.traduzir(n.toString())).isEmpty();assertThat(t.repasseSemGrupos(n.toString(),null)).isEmpty();
    }
    @Test void ecoEOtraInstanciaNaoDuplicam() {
        var n=recebido("w1");((ObjectNode)n.path("data").path("key")).put("fromMe",true);
        var t=new EvolutionWebhookTradutor(JSON,config());assertThat(t.traduzir(n.toString())).isEmpty();
        n.put("instance","outra");assertThat(t.idsExternos(n.toString())).isEmpty();
    }
    @Test void lidSoUsaTelefoneAlternativoExplicitoSemAssociacaoPorAproximacao() {
        var n=recebido("w-lid");var key=(ObjectNode)n.path("data").path("key");
        key.put("remoteJid","id-opaco@lid").put("remoteJidAlt","5561999990000@s.whatsapp.net");
        var tradutor=new EvolutionWebhookTradutor(JSON,config());
        assertThat(tradutor.traduzir(n.toString()).getFirst().telefoneRemetente()).isEqualTo("5561999990000");
        key.put("remoteJid","grupo@g.us");assertThat(tradutor.traduzir(n.toString())).isEmpty();
        key.put("remoteJid","id-opaco@lid").put("remoteJidAlt","outro@lid");assertThat(tradutor.traduzir(n.toString())).isEmpty();
    }
    @Test void allowlistEvolutionExigeTelefoneResolvidoDoLidERejeitaGrupo() {
        var tradutor = new EvolutionWebhookTradutor(JSON, config());
        var n = recebido("w-lid-allowlist");
        var key = (ObjectNode) n.path("data").path("key");
        key.put("remoteJid", "id-opaco@lid").put("remoteJidAlt", "556196791022@s.whatsapp.net");

        assertThat(tradutor.repasseSomenteNumerosPermitidos(
                        n.toString(), null, telefone -> telefone.equals("556196791022")))
                .isPresent();
        assertThat(tradutor.repasseSomenteNumerosPermitidos(
                        n.toString(), null, telefone -> telefone.equals("555491082498")))
                .isEmpty();

        key.put("remoteJid", "grupo@g.us").remove("remoteJidAlt");
        assertThat(tradutor.repasseSomenteNumerosPermitidos(
                        n.toString(), null, telefone -> true))
                .isEmpty();

        key.put("remoteJid", "id-opaco@lid");
        assertThat(tradutor.repasseSomenteNumerosPermitidos(
                        n.toString(), null, telefone -> true))
                .isEmpty();
    }
    @ParameterizedTest @ValueSource(strings={"","errado"})
    void segredoInvalidoNaoLeCorpo(String segredo)throws Exception {
        var req=new MockHttpServletRequest("POST","/webhook/evolution") {
            @Override public jakarta.servlet.ServletInputStream getInputStream(){throw new AssertionError("corpo nao deve ser lido");}
        };req.setSecure(true);if(!segredo.isEmpty())req.addHeader(EvolutionWebhookProtegidoFilter.HEADER,segredo);
        var resposta=new MockHttpServletResponse();new EvolutionWebhookProtegidoFilter(config(),JSON).doFilter(req,resposta,(a,b)->{throw new AssertionError("nao processar");});
        assertThat(resposta.getStatus()).isEqualTo(403);
    }
    @Test void filtroRedigeCredencialAntesDaCadeiaExigeHttpsInstanciaETamanho()throws Exception {
        var f=new EvolutionWebhookProtegidoFilter(config(),JSON);var req=pedido(recebido("w1").toString());req.addHeader("apikey","nao-propagar");
        f.doFilter(req,new MockHttpServletResponse(),(a,b)->{
            var r=(jakarta.servlet.http.HttpServletRequest)a;
            assertThat(r.getHeader(EvolutionWebhookProtegidoFilter.HEADER)).isNull();
            assertThat(r.getHeader("apikey")).isNull();
            assertThat(new String(r.getInputStream().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8)).doesNotContain("nunca-persistir","apikey","destination").contains("Texto sintetico");
        });
        for(var invalido:java.util.List.of(pedido(recebido("x").put("instance","outra").toString()),pedido(" ".repeat(4097)))) {
            var resposta=new MockHttpServletResponse();f.doFilter(invalido,resposta,(a,b)->{throw new AssertionError();});assertThat(resposta.getStatus()).isIn(403,413);
        }
        req=pedido("{}");req.setSecure(false);var resposta=new MockHttpServletResponse();f.doFilter(req,resposta,(a,b)->{throw new AssertionError();});assertThat(resposta.getStatus()).isEqualTo(403);
    }
    static MockHttpServletRequest pedido(String body){var r=new MockHttpServletRequest("POST","/webhook/evolution");r.setSecure(true);r.setContentType("application/json");r.addHeader(EvolutionWebhookProtegidoFilter.HEADER,SEGREDO);r.setContent(body.getBytes(java.nio.charset.StandardCharsets.UTF_8));return r;}
    @Test void recursoOffNaoExigeCredencialENegaAntesDoCorpo()throws Exception {
        var p=new EvolutionProperties(false,null,null,null,null,null,null,0,0,0,false,false);
        var resposta=new MockHttpServletResponse();new EvolutionWebhookProtegidoFilter(p,JSON).doFilter(pedido("{}"),resposta,(a,b)->{throw new AssertionError();});
        assertThat(resposta.getStatus()).isEqualTo(403);
    }
    @Test void textoELegendaPreservamQuebrasDeLinha() {
        var n=recebido("w-linhas");var m=(ObjectNode)n.path("data").path("message");m.put("conversation","Linha 1\nLinha 2\t🙂");
        var t=new EvolutionWebhookTradutor(JSON,config());assertThat(t.traduzir(n.toString()).getFirst().texto()).isEqualTo("Linha 1\nLinha 2\t🙂");
        m.removeAll();m.putObject("imageMessage").put("mimetype","image/png").put("caption","Foto\nLegenda");
        assertThat(t.traduzir(n.toString()).getFirst().legenda()).isEqualTo("Foto\nLegenda");
    }
}
