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

    @Test void tipoDesconhecidoTemDiagnosticoFixoSemExportarValorDoFornecedor() throws Exception {
        var payload=(com.fasterxml.jackson.databind.node.ObjectNode)json.readTree(recebido());
        payload.put("type","tipo-nao-suportado-com-conteudo-sensivel");
        var resultado=tradutor.traduzirComDescartes(payload.toString());
        assertThat(resultado.mensagens()).isEmpty();
        assertThat(resultado.descartes()).singleElement().satisfies(item->{
            assertThat(item.tipo()).isEqualTo("evento");
            assertThat(item.motivo()).isEqualTo(TradutorDeCanal.MotivoDeDescarte.TIPO_NAO_SUPORTADO);
            assertThat(item.toString()).doesNotContain("sensivel");
        });
    }

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
            assertThat(s.status().name()).isEqualTo(switch (estado) {
                case "SENT" -> "ENVIADO";
                case "RECEIVED" -> "ENTREGUE";
                default -> "LIDO";
            });
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

    @Test void deliveryDoRepositorioOficialSemMommentUsaIngressoPersistidoSemInventarHorarioDoProvedor() {
        String payload = "{\"type\":\"DeliveryCallback\",\"instanceId\":\"SINTETICA-A\",\"zaapId\":\"z-1\",\"messageId\":\"w-1\"}";
        var ingresso = java.time.Instant.parse("2026-10-26T12:00:00Z");
        assertThat(tradutor.statusComIdentidade(payload, ingresso)).singleElement().satisfies(status -> {
            assertThat(status.ocorridoEm()).isEqualTo(ingresso);
            assertThat(status.status().name()).isEqualTo("ENVIADO");
        });
        assertThat(tradutor.traduzirComDescartes(payload).descartes()).isEmpty();
        assertThat(tradutor.statusComIdentidade(payload.replace("\"type\"", "\"momment\":null,\"type\""), ingresso)).isEmpty();
        assertThat(tradutor.statusComIdentidade(status("READ").replace(
                "\"momment\":1791800002000,", ""), ingresso)).isEmpty();
    }

    @Test void downloadErrorVazioNaoDescartaMidiaMasErroRealContinuaBloqueado() {
        String documento = recebido().replace("\"text\":{\"message\":\"Teste\\nUnicode 🙂\"}",
                "\"document\":{\"documentUrl\":\"https://midia.invalid/foto.png\",\"downloadError\":\"\"}");
        assertThat(tradutor.traduzir(documento)).hasSize(1);
        assertThat(tradutor.traduzir(documento.replace("\"downloadError\":\"\"", "\"downloadError\":\"indisponivel\""))).isEmpty();
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

    @ParameterizedTest @ValueSource(strings={"buttonsResponseMessage","listResponseMessage"})
    void escolhaSemTextPreservaIdDoCliqueReferenciaEDadosEstruturados(String campo) throws Exception {
        var evento=(com.fasterxml.jackson.databind.node.ObjectNode)json.readTree(recebido());
        evento.remove("text"); evento.put("referenceMessageId","original-whatsapp");
        var escolha=evento.putObject(campo).put("message","Ótimo 🙂");
        escolha.put(campo.equals("buttonsResponseMessage")?"buttonId":"selectedRowId","opcao-1");
        if(campo.equals("listResponseMessage")) escolha.put("title","Opção escolhida");
        var habilitado=new ZApiWebhookTradutor(json,"SINTETICA-A","WHATSAPP",true);
        var resultado=habilitado.traduzirComDescartes(evento.toString());
        assertThat(resultado.descartes()).isEmpty();
        var mensagem=resultado.mensagens().getFirst();
        assertThat(mensagem.tipo()).isEqualTo("TEXTO");
        assertThat(mensagem.texto()).isEqualTo("Ótimo 🙂");
        assertThat(mensagem.contextoWamid()).isEqualTo("original-whatsapp");
        assertThat(habilitado.identidadeDaMensagem(evento.toString(),mensagem.idExterno()))
                .contains(new IdentidadeDeMensagemDoCanal("z-api","SINTETICA-A","WHATSAPP","recebida-1"));
        var dados=json.readTree(mensagem.metadados());
        assertThat(dados.at("/respostaInterativa/identificador").asText()).isEqualTo("opcao-1");
        assertThat(dados.at("/referenciaExterna/valor").asText()).isEqualTo("original-whatsapp");
        assertThat(habilitado.repasseSemGrupos(evento.toString(),null)).get()
                .extracting(TradutorDeCanal.RepasseParaAutomacao::payloadCru).isEqualTo(evento.toString());
        evento.remove("referenceMessageId");
        assertThat(habilitado.traduzir(evento.toString())).hasSize(1);
        assertThat(habilitado.repasseSemGrupos(evento.toString(),null)).isPresent();
    }

    @Test void votoDeEnquetePreservaIdsDistintosOpcoesOrdenadasEEnvelopeOriginal() throws Exception {
        var evento=(com.fasterxml.jackson.databind.node.ObjectNode)json.readTree(recebido());
        evento.remove("text");
        evento.put("messageId", "voto-whatsapp-001");
        var opcoes=evento.putObject("pollVote").put("pollMessageId", "enquete-whatsapp-009").putArray("options");
        opcoes.addObject().put("name", "Instalação");
        opcoes.addObject().put("name", "Orçamento");
        var habilitado=new ZApiWebhookTradutor(json,"SINTETICA-A","WHATSAPP",true);

        var traducao=habilitado.traduzirComDescartes(evento.toString());
        assertThat(traducao.descartes()).isEmpty();
        assertThat(traducao.mensagens()).singleElement().satisfies(m -> {
            assertThat(m.texto()).isEqualTo("Instalação, Orçamento");
            assertThat(m.contextoWamid()).isNull();
            assertThat(habilitado.identidadeDaMensagem(evento.toString(), m.idExterno()))
                    .contains(new IdentidadeDeMensagemDoCanal("z-api", "SINTETICA-A", "WHATSAPP", "voto-whatsapp-001"));
            try {
                var metadados=json.readTree(m.metadados()).path("respostaInterativa");
                assertThat(metadados.path("tipo").asText()).isEqualTo("ENQUETE");
                assertThat(metadados.path("pollMessageId").asText()).isEqualTo("enquete-whatsapp-009");
                assertThat(metadados.path("opcoes").size()).isEqualTo(2);
                assertThat(metadados.path("opcoes").get(0).asText()).isEqualTo("Instalação");
                assertThat(metadados.path("opcoes").get(1).asText()).isEqualTo("Orçamento");
            } catch (Exception e) { throw new AssertionError(e); }
        });
        assertThat(habilitado.repasseSemGrupos(evento.toString(), null)).get()
                .extracting(TradutorDeCanal.RepasseParaAutomacao::payloadCru).isEqualTo(evento.toString());
    }

    @ParameterizedTest @ValueSource(strings={"{}", "{\"pollMessageId\":\"poll-1\",\"options\":[]}",
            "{\"pollMessageId\":4,\"options\":[{\"name\":\"Opção\"}]}",
            "{\"pollMessageId\":\"poll-1\",\"options\":[{}]}",
            "{\"pollMessageId\":\"poll-1\",\"options\":\"Opção\"}"})
    void votoDeEnqueteIncompletoEDiagnosticadoSemInventarTextoOuRepassar(String valor) throws Exception {
        var evento=(com.fasterxml.jackson.databind.node.ObjectNode)json.readTree(recebido());
        evento.remove("text"); evento.set("pollVote", json.readTree(valor));
        var habilitado=new ZApiWebhookTradutor(json,"SINTETICA-A","WHATSAPP",true);
        var resultado=habilitado.traduzirComDescartes(evento.toString());
        assertThat(resultado.mensagens()).isEmpty();
        assertThat(resultado.descartes()).singleElement()
                .extracting(TradutorDeCanal.ItemDescartado::motivo)
                .isEqualTo(TradutorDeCanal.MotivoDeDescarte.ITEM_MALFORMADO);
        assertThat(habilitado.repasseSemGrupos(evento.toString(), null)).isEmpty();
    }

    @ParameterizedTest @ValueSource(strings={"{}","[]","null","{\"buttonId\":1,\"message\":\"x\"}",
            "{\"buttonId\":\"1\"}","{\"buttonId\":\"1\",\"message\":false}"})
    void botaoMalformadoNaoInventaTextoNemDisparaRepasse(String valor) throws Exception {
        var evento=(com.fasterxml.jackson.databind.node.ObjectNode)json.readTree(recebido());
        evento.remove("text"); evento.set("buttonsResponseMessage",json.readTree(valor));
        var habilitado=new ZApiWebhookTradutor(json,"SINTETICA-A","WHATSAPP",true);
        assertThat(habilitado.traduzirComDescartes(evento.toString()).mensagens()).isEmpty();
        assertThat(habilitado.traduzirComDescartes(evento.toString()).descartes()).singleElement()
                .extracting(TradutorDeCanal.ItemDescartado::motivo).isEqualTo(TradutorDeCanal.MotivoDeDescarte.ITEM_MALFORMADO);
        assertThat(habilitado.repasseSemGrupos(evento.toString(),null)).isEmpty();
    }

    @Test void referenciaMalformadaNaoApagaMensagemEReferenciaDeTextoNaoMudaIdentidade() throws Exception {
        var evento=(com.fasterxml.jackson.databind.node.ObjectNode)json.readTree(recebido());
        evento.put("referenceMessageId",42);
        var parcial=tradutor.traduzirComDescartes(evento.toString());
        assertThat(parcial.mensagens()).hasSize(1);
        assertThat(parcial.descartes()).singleElement().extracting(TradutorDeCanal.ItemDescartado::tipo).isEqualTo("reference");
        evento.put("referenceMessageId","original");
        var citada=tradutor.traduzir(evento.toString()).getFirst();
        assertThat(citada.contextoWamid()).isEqualTo("original");
        assertThat(citada.idExterno()).isEqualTo(tradutor.traduzir(recebido()).getFirst().idExterno());
    }

    private String status(String estado) {
        return "{\"type\":\"MessageStatusCallback\",\"instanceId\":\"SINTETICA-A\",\"status\":\"" + estado + "\",\"ids\":[\"w-1\"],\"momment\":1791800002000,\"isGroup\":false}";
    }
}
