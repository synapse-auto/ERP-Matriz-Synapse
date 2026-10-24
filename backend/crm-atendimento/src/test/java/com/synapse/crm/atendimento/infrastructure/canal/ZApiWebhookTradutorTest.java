package com.synapse.crm.atendimento.infrastructure.canal;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.synapse.crm.atendimento.domain.canal.IdentidadeDeMensagemDoCanal;
import com.synapse.crm.atendimento.domain.canal.TradutorDeCanal;

class ZApiWebhookTradutorTest {
    private final ObjectMapper json = new ObjectMapper();
    private final ZApiWebhookTradutor tradutor = new ZApiWebhookTradutor(json, "SINTETICA-A");

    @Test
    void nenhumaCredencialPresumidaAbreEntrada() {
        assertThat(tradutor.assinaturaValida("{}", "Client-Token", "segredo-url")).isFalse();
        assertThat(tradutor.tokenDeVerificacaoValido("teste")).isFalse();
        assertThat(tradutor.repasseSemGrupos(recebido(), "qualquer")).isEmpty();
    }

    @Test
    void traduzTextoOficialComIdentidadeIsoladaENaoPerdeUnicode() {
        var traducao = tradutor.traduzirComDescartes(recebido());
        assertThat(traducao.descartes()).isEmpty();
        assertThat(traducao.mensagens()).singleElement().satisfies(m -> {
            assertThat(m.texto()).isEqualTo("Teste\nUnicode 🙂");
            assertThat(m.telefoneRemetente()).isEqualTo("5561999990000");
            assertThat(m.identificadorDestino()).isEqualTo("SINTETICA-A");
            assertThat(m.idExterno()).isNotEqualTo("recebida-1");
        });
        assertThat(tradutor.identidadeDaMensagem(recebido(), traducao.mensagens().getFirst().idExterno()))
                .contains(new IdentidadeDeMensagemDoCanal("z-api", "SINTETICA-A", "WHATSAPP", "recebida-1"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"isGroup", "isNewsletter", "isStatusReply", "waitingMessage", "isEdit"})
    void flagsNaoSuportadasNaoCriamConversa(String flag) throws Exception {
        var payload = json.readTree(recebido()).deepCopy();
        ((com.fasterxml.jackson.databind.node.ObjectNode) payload).put(flag, true);
        assertThat(tradutor.traduzir(payload.toString())).isEmpty();
        assertThat(tradutor.traduzirComDescartes(payload.toString()).descartes()).isNotEmpty();
    }

    @Test
    void ecoNaoViraEntradaNemRepasseParaIa() {
        String eco = recebido().replace("\"fromMe\":false", "\"fromMe\":true");
        assertThat(tradutor.traduzir(eco)).isEmpty();
        assertThat(tradutor.repasseSemGrupos(eco, null)).isEmpty();
    }

    @Test
    void lidOnlyEOutraInstanciaFicamRastreaveisSemInventarTelefone() {
        String lid = recebido().replace("5561999990000", "123456@lid");
        assertThat(tradutor.traduzirComDescartes(lid).descartes()).singleElement()
                .extracting(TradutorDeCanal.ItemDescartado::motivo)
                .isEqualTo(TradutorDeCanal.MotivoDeDescarte.IDENTIDADE_NAO_SUPORTADA);
        assertThat(tradutor.traduzir(recebido().replace("SINTETICA-A", "OUTRA"))).isEmpty();
        assertThat(tradutor.statusComIdentidade(status("READ"))).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"SENT", "RECEIVED", "READ"})
    void statusExigeNamespaceExplicitoENaoUsaMapaGlobal(String estado) {
        var local = new ZApiWebhookTradutor(json, "SINTETICA-A", "WHATSAPP");
        assertThat(local.statusComIdentidade(status(estado))).singleElement().satisfies(s -> {
            assertThat(s.identidade().namespace()).isEqualTo("WHATSAPP");
            assertThat(s.identidade().valor()).isEqualTo("w-1");
        });
        assertThat(local.statusDeEntrega(status(estado))).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"READ_BY_ME", "PLAYED", "DESCONHECIDO", "PENDING"})
    void estadoNaoComprovadoNaoViraLidoOuEntregue(String estado) {
        assertThat(new ZApiWebhookTradutor(json, "SINTETICA-A", "WHATSAPP").statusComIdentidade(status(estado))).isEmpty();
    }

    @Test
    void deliveryConfirmaSomenteEnvioENaoEntrega() {
        String payload = "{\"type\":\"DeliveryCallback\",\"instanceId\":\"SINTETICA-A\",\"zaapId\":\"z-1\",\"messageId\":\"w-1\",\"momment\":1791800000000}";
        assertThat(tradutor.statusComIdentidade(payload)).singleElement()
                .extracting(s -> s.status().name()).isEqualTo("ENVIADO");
        assertThat(tradutor.statusComIdentidade(payload.replace("\"momment\"", "\"error\":\"segredo privado\",\"momment\"")))
                .singleElement().extracting(s -> s.status().name()).isEqualTo("FALHOU");
    }

    @Test void deliveryComErroNaoEDeduplicadoComoSucessoAnterior() {
        String payload = "{\"type\":\"DeliveryCallback\",\"instanceId\":\"SINTETICA-A\",\"zaapId\":\"z-1\",\"messageId\":\"w-1\",\"momment\":1791800000000}";
        assertThat(tradutor.idsExternos(payload)).isNotEqualTo(tradutor.idsExternos(payload.replace("\"momment\"", "\"error\":\"falha\",\"momment\"")));
        assertThat(tradutor.idsExternos(payload.replace("\"momment\"", "\"error\":\"\",\"momment\""))).isEqualTo(tradutor.idsExternos(payload));
        assertThat(tradutor.statusComIdentidade(payload.replace("\"momment\"", "\"errorCode\":\"SHADOW_BAN\",\"momment\"")))
                .singleElement().extracting(s -> s.status().name()).isEqualTo("FALHOU");
        assertThat(tradutor.statusComIdentidade(payload.replace("\"momment\"", "\"error\":{},\"momment\""))).isEmpty();
    }

    @ParameterizedTest @ValueSource(strings = {"\"false\"", "null", "0"})
    void flagMalformadaNaoPassaComoFalsa(String valor) {
        assertThat(tradutor.traduzir(recebido().replace("\"fromMe\":false", "\"fromMe\":" + valor))).isEmpty();
    }

    @ParameterizedTest @ValueSource(strings = {"http://midia.invalid/a", "https://usuario@midia.invalid/a", "https://midia.invalid/a#fragmento", "javascript:alert(1)"})
    void referenciaDeMidiaInvalidaNaoEProcessada(String url) {
        String documento = recebido().replace("\"text\":{\"message\":\"Teste\\nUnicode 🙂\"}",
                "\"document\":{\"documentUrl\":\"" + url + "\"}");
        assertThat(tradutor.traduzir(documento)).isEmpty();
    }

    @Test
    void pngComoDocumentoMantemTipoEUrlNaoViraIdDeOutraApi() {
        String documento = recebido().replace("\"text\":{\"message\":\"Teste\\nUnicode 🙂\"}",
                "\"document\":{\"documentUrl\":\"https://midia.invalid/foto.png\",\"mimeType\":\"image/png\",\"fileName\":\"foto.png\"}");
        assertThat(tradutor.traduzir(documento)).singleElement().satisfies(m -> {
            assertThat(m.tipo()).isEqualTo("DOCUMENTO");
            assertThat(m.mimetype()).isEqualTo("image/png");
            assertThat(m.midiaIdExterno()).isEqualTo("https://midia.invalid/foto.png");
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "[]", "null", "invalido"})
    void malformadoNaoCriaStatusNemMensagem(String payload) {
        assertThat(tradutor.traduzir(payload)).isEmpty();
        assertThat(tradutor.statusComIdentidade(payload)).isEmpty();
    }

    private String recebido() {
        return "{\"type\":\"ReceivedCallback\",\"instanceId\":\"SINTETICA-A\",\"messageId\":\"recebida-1\",\"phone\":\"5561999990000\",\"fromMe\":false,\"momment\":1791800000000,\"text\":{\"message\":\"Teste\\nUnicode 🙂\"}}";
    }

    private String status(String estado) {
        return "{\"type\":\"MessageStatusCallback\",\"instanceId\":\"SINTETICA-A\",\"status\":\"" + estado + "\",\"ids\":[\"w-1\"],\"momment\":1791800002000,\"isGroup\":false}";
    }
}
