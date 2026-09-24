package com.synapse.crm.atendimento.infrastructure.canal;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import com.synapse.crm.atendimento.domain.canal.TradutorDeCanal.ItemDescartado;
import com.synapse.crm.atendimento.domain.canal.TradutorDeCanal.MensagemRecebidaDoCanal;
import com.synapse.crm.atendimento.domain.canal.TradutorDeCanal.MotivoDeDescarte;
import com.synapse.crm.atendimento.domain.canal.TradutorDeCanal.Traducao;

/**
 * Contato compartilhado e registro de descarte no tradutor da Uzapi/Autotic.
 *
 * <p>O formato segue o schema {@code ContactsMessage} de {@code /webhook/message/contacts} do
 * Swagger oficial (sem {@code wa_id} no telefone), com nomes e numeros sinteticos.
 */
class UzapiAutoticWebhookTradutorContatoTest {

    private final ObjectMapper json = new ObjectMapper();
    private final UzapiAutoticWebhookTradutor tradutor = new UzapiAutoticWebhookTradutor(
            new CanalProperties(
                    UzapiAutoticAdapter.PROVEDOR, "https://uzapi.example.test", "5511999999999",
                    "token-de-teste", null, "segredo", Duration.ofHours(24), Duration.ofSeconds(10), "",
                    "", "v1"),
            json);

    @Test
    void cartaoNoFormatoDoSwaggerViraContatoEstruturado() throws Exception {
        Traducao traducao = tradutor.traduzirComDescartes(payloadComMensagens(
                "{\"from\":\"556188888888\",\"id\":\"A547D0238F6686F8\",\"isGroup\":false,"
                        + "\"timestamp\":\"1768843447\",\"type\":\"contacts\",\"contacts\":[{"
                        + "\"name\":{\"first_name\":\"Loja\",\"formatted_name\":\"Loja Exemplo\"},"
                        + "\"phones\":[{\"phone\":\"+55 43 3300-0000\",\"type\":\"Comercial\"}]}]}"));

        assertThat(traducao.descartes()).isEmpty();
        MensagemRecebidaDoCanal mensagem = traducao.mensagens().get(0);
        assertThat(mensagem.tipo()).isEqualTo("CONTATO");
        assertThat(mensagem.idExterno()).isEqualTo("A547D0238F6686F8");
        assertThat(mensagem.telefoneRemetente()).isEqualTo("556188888888");
        assertThat(mensagem.nomeExibicao()).isEqualTo("Cliente Uzapi");
        JsonNode contato = json.readTree(mensagem.texto()).path("contatos").get(0);
        assertThat(contato.path("nome").asText()).isEqualTo("Loja Exemplo");
        assertThat(contato.path("telefones").get(0).path("numero").asText()).isEqualTo("+55 43 3300-0000");
        assertThat(contato.path("telefones").get(0).path("tipo").asText()).isEqualTo("Comercial");
    }

    @Test
    void storyNaoViraMensagemNemDescarte() {
        Traducao traducao = tradutor.traduzirComDescartes(payloadComMensagens(
                "{\"from\":\"556188888888\",\"id\":\"story-1\",\"group_id\":\"status@broadcast\","
                        + "\"type\":\"contacts\",\"contacts\":[{\"name\":{\"formatted_name\":\"X\"}}]}"));

        assertThat(traducao.mensagens()).isEmpty();
        assertThat(traducao.descartes()).isEmpty();
    }

    @Test
    void itemSemIdTipoDesconhecidoEMalformadoFicamRegistradosSemDerrubarOTexto() {
        Traducao traducao = tradutor.traduzirComDescartes(payloadComMensagens(
                "{\"type\":\"text\",\"text\":{}},"
                        + "{\"from\":\"556188888888\",\"id\":\"poll-1\",\"type\":\"poll\"},"
                        + "{\"from\":\"556188888888\",\"id\":\"ok\",\"type\":\"contacts\","
                        + "\"contacts\":[{\"name\":{\"formatted_name\":\"Contato\"}}]},"
                        + "{\"from\":\"556188888888\",\"id\":\"txt\",\"type\":\"text\",\"text\":{\"body\":\"ok\"}}"));

        assertThat(traducao.mensagens()).extracting(MensagemRecebidaDoCanal::idExterno)
                .containsExactly("ok", "txt");
        assertThat(traducao.descartes()).containsExactly(
                new ItemDescartado("text", MotivoDeDescarte.SEM_IDENTIFICADOR),
                new ItemDescartado("poll", MotivoDeDescarte.TIPO_NAO_SUPORTADO));
    }

    @Test
    void postSoDeStatusNaoGeraDescarte() {
        Traducao traducao = tradutor.traduzirComDescartes(
                "{\"entry\":[{\"changes\":[{\"value\":{\"metadata\":{\"phone_number_id\":\"phone-id-1\"},"
                        + "\"statuses\":[{\"id\":\"wamid.x\",\"status\":\"played\"}]}}]}]}");

        assertThat(traducao.mensagens()).isEmpty();
        assertThat(traducao.descartes()).isEmpty();
    }

    private static String payloadComMensagens(String mensagens) {
        return "{\"entry\":[{\"changes\":[{\"value\":{"
                + "\"metadata\":{\"phone_number_id\":\"phone-id-1\"},"
                + "\"contacts\":[{\"wa_id\":\"556188888888\",\"profile\":{\"name\":\"Cliente Uzapi\"}}],"
                + "\"messages\":["
                + mensagens
                + "]}}]}]}";
    }
}
