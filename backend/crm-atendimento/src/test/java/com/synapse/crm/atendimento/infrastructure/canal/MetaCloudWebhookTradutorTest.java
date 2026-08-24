package com.synapse.crm.atendimento.infrastructure.canal;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import com.synapse.crm.atendimento.domain.canal.TradutorDeCanal;

class MetaCloudWebhookTradutorTest {

    private final MetaCloudWebhookTradutor tradutor = new MetaCloudWebhookTradutor(
            new CanalProperties("meta-cloud", null, null, null, "verify", "secret", null, null),
            new ObjectMapper());

    @Test
    void respostaDeBotaoUsaTituloNoHistorico() {
        var mensagem = tradutor.traduzir(payload("button", "Agendar consulta", "agendar"));

        assertThat(mensagem).hasSize(1);
        assertThat(mensagem.get(0).tipo()).isEqualTo("TEXTO");
        assertThat(mensagem.get(0).texto()).isEqualTo("Agendar consulta");
        assertThat(mensagem.get(0).identificadorDestino()).isNull();
    }

    @Test
    void respostaDeListaUsaTituloNoHistorico() {
        var mensagem = tradutor.traduzir(payload("list", "Orçamento", "orcamento"));

        assertThat(mensagem).hasSize(1);
        assertThat(mensagem.get(0).texto()).isEqualTo("Orçamento");
    }

    @Test
    void tresMensagensNaMesmaChangeMantemOrdem() {
        var mensagens = tradutor.traduzir(payloadComMensagens(
                """
                {"from":"5561000000001","id":"A","timestamp":"1720000000","type":"text","text":{"body":"um"}},
                {"from":"5561000000001","id":"B","timestamp":"1720000001","type":"text","text":{"body":"dois"}},
                {"from":"5561000000001","id":"C","timestamp":"1720000002","type":"text","text":{"body":"tres"}}
                """));

        assertThat(mensagens).extracting(TradutorDeCanal.MensagemRecebidaDoCanal::idExterno)
                .containsExactly("A", "B", "C");
    }

    @Test
    void contatosSaoCasadosComCadaRemetente() {
        var mensagens = tradutor.traduzir("""
                {"entry":[{"changes":[{"value":{
                  "contacts":[
                    {"wa_id":"5561000000001","profile":{"name":"Ana"}},
                    {"wa_id":"5561000000002","profile":{"name":"Bruno"}}],
                  "messages":[
                    {"from":"5561000000002","id":"B","type":"text","text":{"body":"b"}},
                    {"from":"5561000000001","id":"A","type":"text","text":{"body":"a"}}]
                }}]}]}
                """);

        assertThat(mensagens).extracting(TradutorDeCanal.MensagemRecebidaDoCanal::nomeExibicao)
                .containsExactly("Bruno", "Ana");
    }

    @Test
    void tipoDesconhecidoNaoDerrubaMensagemSeguinte() {
        var mensagens = tradutor.traduzir(payloadComMensagens(
                """
                {"from":"5561000000001","id":"S","type":"status"},
                {"from":"5561000000001","id":"A","type":"text","text":{"body":"ok"}}
                """));

        assertThat(mensagens).extracting(TradutorDeCanal.MensagemRecebidaDoCanal::idExterno)
                .containsExactly("A");
    }

    @Test
    void mensagensDeDuasEntriesSaoPercorridas() {
        var mensagens = tradutor.traduzir("""
                {"entry":[
                  {"changes":[{"value":{"contacts":[{"wa_id":"1","profile":{"name":"A"}}],"messages":[{"from":"1","id":"A","type":"text","text":{"body":"a"}}]}}]},
                  {"changes":[{"value":{"contacts":[{"wa_id":"2","profile":{"name":"B"}}],"messages":[{"from":"2","id":"B","type":"text","text":{"body":"b"}}]}}]}
                ]}
                """);

        assertThat(mensagens).extracting(TradutorDeCanal.MensagemRecebidaDoCanal::idExterno)
                .containsExactly("A", "B");
    }

    @Test
    void preservaONumeroDeDestinoDaMeta() {
        var mensagens = tradutor.traduzir("""
                {"entry":[{"changes":[{"value":{"metadata":{"phone_number_id":"999999999999999"},
                  "messages":[{"from":"1","id":"A","type":"text","text":{"body":"a"}}]}}]}]}
                """);

        assertThat(mensagens.get(0).identificadorDestino()).isEqualTo("999999999999999");
    }

    private static String payload(String tipo, String titulo, String id) {
        return """
                {"entry":[{"changes":[{"value":{
                  "contacts":[{"profile":{"name":"Cliente"}}],
                  "messages":[{"from":"5561999999999","id":"wamid.interativo", "timestamp":"1720000000",
                    "type":"interactive","interactive":{"type":"%s","%s":{"id":"%s","title":"%s"}}}]
                }}]}]}
                """.formatted(tipo, tipo + "_reply", id, titulo);
    }

    private static String payloadComMensagens(String mensagens) {
        return """
                {"entry":[{"changes":[{"value":{
                  "contacts":[{"wa_id":"5561000000001","profile":{"name":"Cliente"}}],
                  "messages":[%s]
                }}]}]}
                """.formatted(mensagens);
    }
}
