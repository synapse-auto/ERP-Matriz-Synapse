package com.synapse.crm.atendimento.infrastructure.canal;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import com.synapse.crm.atendimento.domain.canal.TradutorDeCanal;

class UzapiAutoticWebhookTradutorTest {

    private static final String SEGREDO = "segredo-de-webhook";

    private final UzapiAutoticWebhookTradutor tradutor = new UzapiAutoticWebhookTradutor(
            new CanalProperties(
                    UzapiAutoticAdapter.PROVEDOR,
                    "https://uzapi.example.test",
                    "5511999999999",
                    "token-de-teste",
                    null,
                    SEGREDO,
                    Duration.ofHours(24),
                    Duration.ofSeconds(10),
                    "",
                    "usuario",
                    "v1"),
            new ObjectMapper());

    @Test
    void assinaturaUsaSegredoDaQueryEmTempoConstanteEIgnoraCabecalho() {
        assertThat(tradutor.assinaturaValida("{}", "qualquer-cabecalho", SEGREDO)).isTrue();
        assertThat(tradutor.assinaturaValida("{}", null, "outro-segredo")).isFalse();
        assertThat(tradutor.assinaturaValida("{}", null, null)).isFalse();
    }

    @Test
    void segredoAusenteRecusaMesmoQueQueryBata() {
        UzapiAutoticWebhookTradutor semSegredo = new UzapiAutoticWebhookTradutor(
                new CanalProperties(
                        UzapiAutoticAdapter.PROVEDOR,
                        "https://uzapi.example.test",
                        "5511999999999",
                        "token-de-teste",
                        null,
                        "",
                        null,
                        null,
                        null,
                        "usuario",
                        "v1"),
                new ObjectMapper());

        assertThat(semSegredo.assinaturaValida("{}", null, "")).isFalse();
    }

    @Test
    void textoPreservaDestinoNomeTimestampEContexto() {
        var payload = payloadComMensagem(
                "{\"from\":\"556188888888\",\"id\":\"uz-text-1\","
                        + "\"timestamp\":\"1768842483\",\"type\":\"text\","
                        + "\"text\":{\"body\":\"Ola da Uzapi\"},"
                        + "\"context\":{\"message_id\":\"wamid-origem\"}}");
        var mensagem = tradutor.traduzir(payload);

        assertThat(mensagem).singleElement().satisfies(item -> {
            assertThat(item.tipo()).isEqualTo("TEXTO");
            assertThat(item.texto()).isEqualTo("Ola da Uzapi");
            assertThat(item.telefoneRemetente()).isEqualTo("556188888888");
            assertThat(item.nomeExibicao()).isEqualTo("Cliente Uzapi");
            assertThat(item.identificadorDestino()).isEqualTo("phone-id-1");
            assertThat(item.contextoWamid()).isEqualTo("wamid-origem");
        });
    }

    @Test
    void midiasUsamAliasesDeIdMimeENomeEStickersVirarmImagem() {
        var mensagens = tradutor.traduzir(payloadComMensagens(
                "{\"from\":\"556188888888\",\"id\":\"img\",\"timestamp\":\"1\","
                        + "\"type\":\"image\",\"image\":{\"mediaId\":\"m-img\","
                        + "\"mimeType\":\"image/jpeg\",\"caption\":\"foto\"}},"
                        + "{\"from\":\"556188888888\",\"id\":\"doc\",\"timestamp\":\"2\","
                        + "\"type\":\"document\",\"document\":{\"id\":\"m-doc\","
                        + "\"mimetype\":\"application/pdf\",\"fileName\":\"contrato.pdf\"}},"
                        + "{\"from\":\"556188888888\",\"id\":\"stk\",\"timestamp\":\"3\","
                        + "\"type\":\"sticker\",\"sticker\":{\"id\":\"m-stk\","
                        + "\"mime_type\":\"image/webp\"}}"));

        assertThat(mensagens).extracting(TradutorDeCanal.MensagemRecebidaDoCanal::tipo)
                .containsExactly("IMAGEM", "DOCUMENTO", "IMAGEM");
        assertThat(mensagens.get(0).midiaIdExterno()).isEqualTo("m-img");
        assertThat(mensagens.get(0).legenda()).isEqualTo("foto");
        assertThat(mensagens.get(1).nomeArquivo()).isEqualTo("contrato.pdf");
    }

    @Test
    void localizacaoETraduzidaSemMidiaEComCoordenadasValidas() {
        var mensagem = tradutor.traduzir(payloadComMensagem(
                "{\"from\":\"556188888888\",\"id\":\"loc\",\"timestamp\":\"3\","
                        + "\"type\":\"location\",\"location\":{\"latitude\":-15.79,"
                        + "\"longitude\":-47.88,\"name\":\"Esplanada\",\"address\":\"Brasilia\"}}"));

        assertThat(mensagem).singleElement().satisfies(item -> {
            assertThat(item.tipo()).isEqualTo("LOCALIZACAO");
            assertThat(item.midiaIdExterno()).isNull();
            assertThat(item.texto()).contains("\"latitude\":-15.79")
                    .contains("\"longitude\":-47.88")
                    .contains("\"nome\":\"Esplanada\"")
                    .contains("\"endereco\":\"Brasilia\"");
        });
    }

    @Test
    void statusDeEntregaMapeiaEstadosDocumentados() {
        var statuses = tradutor.statusDeEntrega(
                "{\"entry\":[{\"changes\":[{\"value\":{\"statuses\":["
                        + "{\"id\":\"s\",\"status\":\"sent\"},"
                        + "{\"id\":\"d\",\"status\":\"delivered\"},"
                        + "{\"id\":\"r\",\"status\":\"read\"},"
                        + "{\"id\":\"f\",\"status\":\"failed\",\"errors\":[{\"code\":400,\"title\":\"Falhou\"}]}"
                        + "]}}]}]}");

        assertThat(statuses).extracting(TradutorDeCanal.StatusDeEntregaDoCanal::statusEntrega)
                .containsExactly("ENVIADO", "ENTREGUE", "LIDO", "FALHOU");
        assertThat(statuses.get(3).codigoErro()).isEqualTo(400);
    }

    @Test
    void statusStoryEIgnoradoEIdExternoNaoVazaParaFilaDeMensagens() {
        var mensagens = tradutor.traduzir(payloadComMensagens(
                "{\"from\":\"status@broadcast\",\"id\":\"story\",\"timestamp\":\"1\",\"type\":\"text\",\"text\":{\"body\":\"status\"}},"
                        + "{\"key\":{\"remotejid\":\"status@broadcast\"},\"from\":\"556188888888\",\"id\":\"story-2\",\"type\":\"text\",\"text\":{\"body\":\"status\"}},"
                        + "{\"from\":\"556188888888\",\"id\":\"ok\",\"type\":\"text\",\"text\":{\"body\":\"ok\"}}"));

        assertThat(mensagens).extracting(TradutorDeCanal.MensagemRecebidaDoCanal::idExterno)
                .containsExactly("ok");
        assertThat(tradutor.idsExternos(payloadComMensagens(
                "{\"from\":\"status@broadcast\",\"id\":\"story\",\"type\":\"text\"},"
                        + "{\"from\":\"556188888888\",\"id\":\"ok\",\"type\":\"text\"}")))
                .containsExactly("ok");
    }

    @Test
    void itemDesconhecidoOuMalformadoNaoDerrubaOsDemais() {
        var mensagens = tradutor.traduzir(payloadComMensagens(
                "{\"type\":\"text\",\"text\":{}},"
                        + "{\"from\":\"556188888888\",\"id\":\"desconhecido\",\"type\":\"poll\"},"
                        + "{\"from\":\"556188888888\",\"id\":\"ok\",\"type\":\"text\",\"text\":{\"body\":\"ok\"}}"));

        assertThat(mensagens).extracting(TradutorDeCanal.MensagemRecebidaDoCanal::idExterno)
                .containsExactly("ok");
    }

    @Test
    void coordenadaInvalidaDescartaApenasLocalizacao() {
        var mensagens = tradutor.traduzir(payloadComMensagens(
                "{\"from\":\"556188888888\",\"id\":\"loc-invalida\",\"type\":\"location\",\"location\":{\"latitude\":-91,\"longitude\":-47}},"
                        + "{\"from\":\"556188888888\",\"id\":\"ok\",\"type\":\"text\",\"text\":{\"body\":\"ok\"}}"));

        assertThat(mensagens).extracting(TradutorDeCanal.MensagemRecebidaDoCanal::idExterno)
                .containsExactly("ok");
    }

    @Test
    void tokenGetNaoEAceitoPorqueUzapiNaoDocumentaDesafio() {
        assertThat(tradutor.tokenDeVerificacaoValido("qualquer-token")).isFalse();
    }

    private static String payloadComMensagem(String mensagem) {
        return payloadComMensagens(mensagem);
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
