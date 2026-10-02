package com.synapse.crm.atendimento.infrastructure.canal;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.synapse.crm.atendimento.domain.canal.TradutorDeCanal;
import com.synapse.crm.atendimento.domain.canal.TradutorDeCanal.MotivoDeDescarte;

/**
 * Traducao do webhook de midia da Uzapi para o CRM: o que o tradutor reconhece, que campos exige e
 * o que descarta. Payloads sanitizados (telefone, ids e nomes ficticios). A extensao do arquivo
 * nunca participa da decisao: o tipo vem de {@code type}.
 */
class UzapiAutoticWebhookTradutorMidiaTest {

    private final UzapiAutoticWebhookTradutor tradutor = new UzapiAutoticWebhookTradutor(
            new CanalProperties(
                    UzapiAutoticAdapter.PROVEDOR,
                    "https://uzapi.example.test",
                    "5511999999999",
                    "token-de-teste",
                    null,
                    "segredo-de-webhook",
                    Duration.ofHours(24),
                    Duration.ofSeconds(10),
                    "",
                    "usuario",
                    "v1"),
            new ObjectMapper());

    static Stream<Arguments> tiposDeMidia() {
        return Stream.of(
                Arguments.of("image", "IMAGEM", "FOTO.JPG", "image/jpeg"),
                Arguments.of("image", "IMAGEM", "foto.jpeg", "image/jpeg"),
                Arguments.of("image", "IMAGEM", "foto.png", "image/png"),
                Arguments.of("image", "IMAGEM", null, "image/jpeg"),
                Arguments.of("document", "DOCUMENTO", "contrato.pdf", "application/pdf"),
                Arguments.of("document", "DOCUMENTO", "FOTO.JPG", "image/jpeg"),
                Arguments.of("audio", "AUDIO", null, "audio/ogg; codecs=opus"),
                Arguments.of("video", "VIDEO", "video.mp4", "video/mp4"),
                Arguments.of("sticker", "IMAGEM", null, "image/webp"));
    }

    @ParameterizedTest(name = "{0} nome={2}")
    @MethodSource("tiposDeMidia")
    void cadaTipoReconhecidoLevaIdMimeENomeSemDependerDaExtensao(
            String tipo, String tipoNoCrm, String nome, String mime) {
        String arquivo = nome == null ? "" : ",\"filename\":\"" + nome + "\"";
        var traducao = tradutor.traduzirComDescartes(payload(
                "{\"from\":\"5561988887777\",\"id\":\"wamid.fake\",\"timestamp\":\"1790866800\","
                        + "\"type\":\"" + tipo + "\",\"" + tipo + "\":{\"id\":\"media-fake\","
                        + "\"mime_type\":\"" + mime + "\"" + arquivo + "}}"));

        assertThat(traducao.descartes()).isEmpty();
        assertThat(traducao.mensagens()).singleElement().satisfies(mensagem -> {
            assertThat(mensagem.tipo()).isEqualTo(tipoNoCrm);
            assertThat(mensagem.midiaIdExterno()).isEqualTo("media-fake");
            assertThat(mensagem.mimetype()).isEqualTo(mime);
            assertThat(mensagem.nomeArquivo()).isEqualTo(nome);
            assertThat(mensagem.ehMidia()).isTrue();
        });
    }

    @Test
    void tipoEmCaixaAltaEReconhecidoPorqueOTipoELidoEmMinusculas() {
        // O objeto da midia e procurado pelo type ja normalizado: "IMAGE" acha "image".
        var traducao = tradutor.traduzirComDescartes(payload(
                "{\"from\":\"5561988887777\",\"id\":\"wamid.fake\",\"type\":\"IMAGE\","
                        + "\"image\":{\"id\":\"media-fake\",\"mime_type\":\"image/jpeg\"}}"));

        assertThat(traducao.mensagens()).singleElement()
                .satisfies(mensagem -> assertThat(mensagem.tipo()).isEqualTo("IMAGEM"));
    }

    @ParameterizedTest(name = "objeto de midia: {0}")
    @MethodSource("midiasSemReferencia")
    void midiaSemMediaIdUtilizavelNaoViraMensagemEFicaRegistradaComoDescarte(String objetoDaMidia) {
        var traducao = tradutor.traduzirComDescartes(payload(
                "{\"from\":\"5561988887777\",\"id\":\"wamid.fake\",\"type\":\"image\"" + objetoDaMidia + "}"));

        assertThat(traducao.mensagens()).isEmpty();
        assertThat(traducao.descartes()).singleElement().satisfies(descarte -> {
            assertThat(descarte.tipo()).isEqualTo("image");
            assertThat(descarte.motivo()).isEqualTo(MotivoDeDescarte.SEM_IDENTIFICADOR);
        });
    }

    static Stream<Arguments> midiasSemReferencia() {
        return Stream.of(
                Arguments.of(""), // type=image sem o objeto image
                Arguments.of(",\"image\":{}"),
                Arguments.of(",\"image\":{\"mime_type\":\"image/jpeg\"}"),
                Arguments.of(",\"image\":{\"id\":\"\"}"),
                Arguments.of(",\"image\":{\"id\":\"   \"}"),
                Arguments.of(",\"image\":{\"id\":null}"));
    }

    @Test
    void midiaSemIdNaoDerrubaOPdfDoMesmoPost() {
        var traducao = tradutor.traduzirComDescartes(payload(
                "{\"from\":\"5561988887777\",\"id\":\"sem-id\",\"type\":\"image\",\"image\":{}},"
                        + "{\"from\":\"5561988887777\",\"id\":\"pdf\",\"type\":\"document\","
                        + "\"document\":{\"id\":\"media-pdf\",\"mime_type\":\"application/pdf\","
                        + "\"filename\":\"contrato.pdf\"}}"));

        assertThat(traducao.mensagens()).extracting(TradutorDeCanal.MensagemRecebidaDoCanal::midiaIdExterno)
                .containsExactly("media-pdf");
        assertThat(traducao.descartes()).hasSize(1);
    }

    private static String payload(String mensagens) {
        return "{\"entry\":[{\"changes\":[{\"value\":{"
                + "\"metadata\":{\"phone_number_id\":\"phone-id-1\"},"
                + "\"contacts\":[{\"wa_id\":\"5561988887777\",\"profile\":{\"name\":\"Cliente Fake\"}}],"
                + "\"messages\":[" + mensagens + "]}}]}]}";
    }
}
