package com.synapse.crm.atendimento.infrastructure.canal;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import com.synapse.crm.atendimento.domain.canal.TradutorDeCanal;
import com.synapse.crm.atendimento.domain.canal.TradutorDeCanal.ItemDescartado;
import com.synapse.crm.atendimento.domain.canal.TradutorDeCanal.MotivoDeDescarte;
import com.synapse.crm.atendimento.domain.canal.TradutorDeCanal.ReacaoRecebidaDoCanal;

/** E214 — {@code type: reaction} tem o mesmo formato na Meta e no Swagger da Uzapi. */
class ReacaoRecebidaTradutoresTest {

    static Stream<TradutorDeCanal> tradutores() {
        ObjectMapper json = new ObjectMapper();
        return Stream.of(
                new MetaCloudWebhookTradutor(
                        new CanalProperties("meta-cloud", null, null, null, "verify", "secret", null, null, null, null, null),
                        json),
                new UzapiAutoticWebhookTradutor(
                        new CanalProperties(
                                UzapiAutoticAdapter.PROVEDOR, "https://uzapi.example.test", "5511999999999",
                                "token", null, "secret", Duration.ofHours(24), Duration.ofSeconds(10), "", "u", "v1"),
                        json));
    }

    @ParameterizedTest
    @MethodSource("tradutores")
    void reacaoViraReacaoDoClienteENaoMensagem(TradutorDeCanal tradutor) {
        var traducao = tradutor.traduzirComDescartes(payload(
                reacao("r1", "wamid.alvo", "❤️", "1758370000") + ","
                        + "{\"from\":\"5561000000001\",\"id\":\"t1\",\"timestamp\":\"1758370001\",\"type\":\"text\","
                        + "\"text\":{\"body\":\"oi\"}}"));

        assertThat(traducao.mensagens()).extracting(TradutorDeCanal.MensagemRecebidaDoCanal::idExterno)
                .containsExactly("t1");
        assertThat(traducao.descartes()).isEmpty();
        assertThat(traducao.reacoes()).singleElement().satisfies(reacao -> {
            assertThat(reacao.idExterno()).isEqualTo("r1");
            assertThat(reacao.telefoneRemetente()).isEqualTo("5561000000001");
            assertThat(reacao.idExternoAlvo()).isEqualTo("wamid.alvo");
            assertThat(reacao.emoji()).isEqualTo("❤️");
            assertThat(reacao.remocao()).isFalse();
            assertThat(reacao.reagidoEm()).isEqualTo(Instant.ofEpochSecond(1758370000L));
            assertThat(reacao.identificadorDestino()).isEqualTo("phone-id-1");
        });
        // A fila deduplica pelo id do proprio evento de reacao.
        assertThat(tradutor.idsExternos(payload(reacao("r1", "wamid.alvo", "❤️", "1")))).containsExactly("r1");
    }

    @ParameterizedTest
    @MethodSource("tradutores")
    void emojiVazioOuAusenteERemocao(TradutorDeCanal tradutor) {
        var traducao = tradutor.traduzirComDescartes(payload(
                reacao("r1", "wamid.alvo", "", "1758370000") + ","
                        + "{\"from\":\"5561000000001\",\"id\":\"r2\",\"timestamp\":\"1758370001\",\"type\":\"reaction\","
                        + "\"reaction\":{\"message_id\":\"wamid.alvo\"}}"));

        assertThat(traducao.reacoes()).extracting(ReacaoRecebidaDoCanal::remocao).containsExactly(true, true);
        assertThat(traducao.descartes()).isEmpty();
    }

    @ParameterizedTest
    @MethodSource("tradutores")
    void reacaoSemAlvoComTextoOuMalformadaEDescartadaSemDerrubarOLote(TradutorDeCanal tradutor) {
        var traducao = tradutor.traduzirComDescartes(payload(
                reacao("r1", "", "❤️", "1") + ","
                        + reacao("r2", "wamid.alvo", "ok", "1") + ","
                        + "{\"from\":\"5561000000001\",\"id\":\"r3\",\"type\":\"reaction\",\"reaction\":\"quebrada\"},"
                        + "{\"from\":\"\",\"id\":\"r4\",\"type\":\"reaction\",\"reaction\":{\"message_id\":\"w\",\"emoji\":\"❤️\"}},"
                        + reacao("r5", "wamid.alvo", "👍", "2")));

        assertThat(traducao.reacoes()).extracting(ReacaoRecebidaDoCanal::idExterno).containsExactly("r5");
        assertThat(traducao.descartes()).containsExactly(
                new ItemDescartado("reaction", MotivoDeDescarte.CONTEUDO_INVALIDO),
                new ItemDescartado("reaction", MotivoDeDescarte.CONTEUDO_INVALIDO),
                new ItemDescartado("reaction", MotivoDeDescarte.CONTEUDO_INVALIDO),
                new ItemDescartado("reaction", MotivoDeDescarte.SEM_IDENTIFICADOR));
    }

    private static String reacao(String id, String alvo, String emoji, String quando) {
        return "{\"from\":\"5561000000001\",\"id\":\"" + id + "\",\"isGroup\":false,\"timestamp\":\"" + quando
                + "\",\"type\":\"reaction\",\"reaction\":{\"message_id\":\"" + alvo + "\",\"emoji\":\"" + emoji
                + "\"}}";
    }

    private static String payload(String mensagens) {
        return "{\"object\":\"whatsapp_business_account\",\"entry\":[{\"changes\":[{\"value\":{"
                + "\"metadata\":{\"phone_number_id\":\"phone-id-1\"},"
                + "\"contacts\":[{\"wa_id\":\"5561000000001\",\"profile\":{\"name\":\"Cliente\"}}],"
                + "\"messages\":[" + mensagens + "]}}]}]}";
    }
}
