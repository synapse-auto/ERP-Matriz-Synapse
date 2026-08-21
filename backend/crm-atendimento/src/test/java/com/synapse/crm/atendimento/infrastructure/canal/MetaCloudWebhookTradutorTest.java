package com.synapse.crm.atendimento.infrastructure.canal;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class MetaCloudWebhookTradutorTest {

    private final MetaCloudWebhookTradutor tradutor = new MetaCloudWebhookTradutor(
            new CanalProperties("meta-cloud", null, null, null, "verify", "secret", null, null),
            new ObjectMapper());

    @Test
    void respostaDeBotaoUsaTituloNoHistorico() {
        var mensagem = tradutor.traduzir(payload("button", "Agendar consulta", "agendar"));

        assertThat(mensagem).isPresent();
        assertThat(mensagem.orElseThrow().tipo()).isEqualTo("TEXTO");
        assertThat(mensagem.orElseThrow().texto()).isEqualTo("Agendar consulta");
    }

    @Test
    void respostaDeListaUsaTituloNoHistorico() {
        var mensagem = tradutor.traduzir(payload("list", "Orçamento", "orcamento"));

        assertThat(mensagem).isPresent();
        assertThat(mensagem.orElseThrow().texto()).isEqualTo("Orçamento");
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
}
