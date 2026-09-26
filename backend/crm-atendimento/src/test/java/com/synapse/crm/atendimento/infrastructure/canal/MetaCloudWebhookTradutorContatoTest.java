package com.synapse.crm.atendimento.infrastructure.canal;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import com.synapse.crm.atendimento.domain.canal.TradutorDeCanal.ItemDescartado;
import com.synapse.crm.atendimento.domain.canal.TradutorDeCanal.MensagemRecebidaDoCanal;
import com.synapse.crm.atendimento.domain.canal.TradutorDeCanal.MotivoDeDescarte;
import com.synapse.crm.atendimento.domain.canal.TradutorDeCanal.Traducao;

/**
 * Contato compartilhado e registro de descarte no tradutor da Meta.
 *
 * <p>Payloads no formato da referencia oficial ({@code webhooks/reference/messages/contacts}), com
 * nomes e numeros sinteticos.
 */
class MetaCloudWebhookTradutorContatoTest {

    private final ObjectMapper json = new ObjectMapper();
    private final MetaCloudWebhookTradutor tradutor = new MetaCloudWebhookTradutor(
            new CanalProperties("meta-cloud", null, null, null, "verify", "secret", null, null, null, null, null),
            json);

    @Test
    void cartaoComDoisNumerosViraContatoEstruturadoPreservandoRemetente() throws Exception {
        Traducao traducao = tradutor.traduzirComDescartes(payloadComMensagens("""
                {"from":"5561000000001","id":"wamid.contato-1","timestamp":"1720000000","type":"contacts",
                 "contacts":[{"name":{"formatted_name":"Arquiteta Exemplo","first_name":"Arquiteta"},
                   "phones":[{"phone":"+55 61 3333-0000","type":"WORK"},
                             {"phone":"+55 61 98888-0000","wa_id":"5561988880000","type":"CELL"}]}]}
                """));

        assertThat(traducao.descartes()).isEmpty();
        assertThat(traducao.mensagens()).singleElement().satisfies(mensagem -> {
            assertThat(mensagem.tipo()).isEqualTo("CONTATO");
            assertThat(mensagem.idExterno()).isEqualTo("wamid.contato-1");
            assertThat(mensagem.telefoneRemetente()).isEqualTo("5561000000001");
            assertThat(mensagem.nomeExibicao()).isEqualTo("Cliente");
            assertThat(mensagem.identificadorDestino()).isEqualTo("phone-id-1");
            assertThat(mensagem.ehMidia()).isFalse();
        });
        JsonNode contato = contatos(traducao.mensagens().get(0)).get(0);
        assertThat(contato.path("nome").asText()).isEqualTo("Arquiteta Exemplo");
        assertThat(contato.path("telefones")).hasSize(2);
        assertThat(contato.path("telefones").get(0).path("numero").asText()).isEqualTo("+55 61 3333-0000");
        assertThat(contato.path("telefones").get(0).has("waId")).isFalse();
        assertThat(contato.path("telefones").get(1).path("waId").asText()).isEqualTo("5561988880000");
        assertThat(contato.path("telefones").get(1).path("tipo").asText()).isEqualTo("CELL");
    }

    @Test
    void variosContatosEContatoSemTelefoneSaoPreservados() throws Exception {
        Traducao traducao = tradutor.traduzirComDescartes(payloadComMensagens("""
                {"from":"5561000000001","id":"wamid.contato-2","timestamp":"1720000000","type":"contacts",
                 "contacts":[{"name":{"formatted_name":"Sem Telefone"},"phones":[]},
                             {"name":{"first_name":"Bruno","last_name":"Exemplo"}}]}
                """));

        JsonNode contatos = contatos(traducao.mensagens().get(0));
        assertThat(contatos).hasSize(2);
        assertThat(contatos.get(0).path("nome").asText()).isEqualTo("Sem Telefone");
        assertThat(contatos.get(0).path("telefones")).isEmpty();
        assertThat(contatos.get(1).path("nome").asText()).isEqualTo("Bruno Exemplo");
    }

    @Test
    void contactsDoEnvelopeSozinhoNuncaViraContatoCompartilhado() {
        // value.contacts[] identifica o remetente. Sem messages[].contacts[], nao ha cartao.
        Traducao traducao = tradutor.traduzirComDescartes(payloadComMensagens("""
                {"from":"5561000000001","id":"wamid.texto","timestamp":"1720000000","type":"text",
                 "text":{"body":"ola"}}
                """));

        assertThat(traducao.mensagens()).extracting(MensagemRecebidaDoCanal::tipo).containsExactly("TEXTO");
        assertThat(traducao.descartes()).isEmpty();
    }

    @Test
    void cartaoSemNomeNemTelefoneEDescartadoComMotivo() {
        Traducao traducao = tradutor.traduzirComDescartes(payloadComMensagens("""
                {"from":"5561000000001","id":"wamid.vazio","timestamp":"1720000000","type":"contacts",
                 "contacts":[{"name":{},"phones":[{}]}]}
                """));

        assertThat(traducao.mensagens()).isEmpty();
        assertThat(traducao.descartes())
                .containsExactly(new ItemDescartado("contacts", MotivoDeDescarte.CONTEUDO_INVALIDO));
    }

    @Test
    void postMistoPreservaOrdemERegistraSoOItemRuim() {
        // Long.MAX_VALUE como timestamp estoura Instant.ofEpochSecond: leitura realmente quebrada.
        Traducao traducao = tradutor.traduzirComDescartes(payloadComMensagens("""
                {"from":"5561000000001","id":"wamid.a","timestamp":"1720000000","type":"contacts",
                 "contacts":[{"name":{"formatted_name":"Contato A"}}]},
                {"from":"5561000000001","id":"wamid.b","timestamp":9223372036854775807,"type":"text",
                 "text":{"body":"quebrado"}},
                {"from":"5561000000001","id":"wamid.c","timestamp":"1720000001","type":"text",
                 "text":{"body":"ok"}}
                """));

        assertThat(traducao.mensagens()).extracting(MensagemRecebidaDoCanal::idExterno)
                .containsExactly("wamid.a", "wamid.c");
        assertThat(traducao.descartes())
                .containsExactly(new ItemDescartado("text", MotivoDeDescarte.ITEM_MALFORMADO));
    }

    @Test
    void tipoNaoSuportadoFicaRegistradoComVocabularioFechado() {
        Traducao traducao = tradutor.traduzirComDescartes(payloadComMensagens("""
                {"from":"5561000000001","id":"wamid.r","timestamp":"1720000000","type":"order",
                 "order":{"catalog_id":"x"}},
                {"from":"5561000000001","id":"wamid.n","timestamp":"1720000000","type":"tipo_inventado_123"}
                """));

        assertThat(traducao.mensagens()).isEmpty();
        // E214: reaction deixou de ser exemplo de tipo nao suportado; order continua sendo.
        assertThat(traducao.descartes()).containsExactly(
                new ItemDescartado("order", MotivoDeDescarte.TIPO_NAO_SUPORTADO),
                new ItemDescartado("outro", MotivoDeDescarte.TIPO_NAO_SUPORTADO));
    }

    @Test
    void itemOuMidiaSemIdSaoDescartadosComOMesmoVocabularioDaUzapi() {
        Traducao traducao = tradutor.traduzirComDescartes(payloadComMensagens("""
                {"from":"5561000000001","id":"","timestamp":"1720000000","type":"text","text":{"body":"a"}},
                {"from":"5561000000001","id":"wamid.img","timestamp":"1720000000","type":"image",
                 "image":{"mime_type":"image/jpeg"}},
                {"from":"5561000000001","id":"wamid.ok","timestamp":"1720000000","type":"text","text":{"body":"b"}}
                """));

        assertThat(traducao.mensagens()).extracting(MensagemRecebidaDoCanal::idExterno)
                .containsExactly("wamid.ok");
        assertThat(traducao.descartes()).containsExactly(
                new ItemDescartado("text", MotivoDeDescarte.SEM_IDENTIFICADOR),
                new ItemDescartado("image", MotivoDeDescarte.SEM_IDENTIFICADOR));
    }

    @Test
    void postSoDeStatusNaoGeraMensagemNemDescarte() {
        Traducao traducao = tradutor.traduzirComDescartes("""
                {"entry":[{"changes":[{"value":{"metadata":{"phone_number_id":"phone-id-1"},
                  "statuses":[{"id":"wamid.enviada","status":"delivered","recipient_id":"5561000000001"}]
                }}]}]}
                """);

        assertThat(traducao.mensagens()).isEmpty();
        assertThat(traducao.descartes()).isEmpty();
    }

    private JsonNode contatos(MensagemRecebidaDoCanal mensagem) throws Exception {
        return json.readTree(mensagem.texto()).path("contatos");
    }

    private static String payloadComMensagens(String mensagens) {
        return """
                {"entry":[{"changes":[{"value":{
                  "metadata":{"phone_number_id":"phone-id-1"},
                  "contacts":[{"wa_id":"5561000000001","profile":{"name":"Cliente"}}],
                  "messages":[%s]
                }}]}]}
                """.formatted(mensagens);
    }
}
