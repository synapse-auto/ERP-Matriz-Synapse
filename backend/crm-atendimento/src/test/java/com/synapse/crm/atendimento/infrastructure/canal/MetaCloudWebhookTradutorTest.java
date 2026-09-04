package com.synapse.crm.atendimento.infrastructure.canal;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import com.synapse.crm.atendimento.domain.canal.TradutorDeCanal;

class MetaCloudWebhookTradutorTest {

    private final MetaCloudWebhookTradutor tradutor = new MetaCloudWebhookTradutor(
            new CanalProperties("meta-cloud", null, null, null, "verify", "secret", null, null, null),
            new ObjectMapper());

    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();
    private Logger logger;

    @BeforeEach
    void anexarLog() {
        logger = (Logger) LoggerFactory.getLogger(MetaCloudWebhookTradutor.class);
        logs.start();
        logger.addAppender(logs);
    }

    @AfterEach
    void desanexarLog() {
        logger.detachAppender(logs);
        logs.stop();
        logs.list.clear();
    }

    @Test
    void respostaDeBotaoUsaTituloNoHistorico() {
        var mensagem = tradutor.traduzir(payload("button_reply", "Agendar consulta", "agendar"));

        assertThat(mensagem).hasSize(1);
        assertThat(mensagem.get(0).tipo()).isEqualTo("TEXTO");
        assertThat(mensagem.get(0).texto()).isEqualTo("Agendar consulta");
        assertThat(mensagem.get(0).identificadorDestino()).isNull();
    }

    @Test
    void respostaDeListaUsaTituloNoHistorico() {
        var mensagem = tradutor.traduzir(payload("list_reply", "Orçamento", "orcamento"));

        assertThat(mensagem).hasSize(1);
        assertThat(mensagem.get(0).texto()).isEqualTo("Orçamento");
    }

    @Test
    void payloadLiteralDeProducaoGravaTituloDaEscolhaDoCliente() {
        // Trecho real de webhook_entrada (02/09): type=list_reply|button_reply — o formato
        // que a fixture inventada com type=button|list nunca exercitava.
        var mensagens = tradutor.traduzir(
                """
                {"object":"whatsapp_business_account","entry":[{"changes":[{"value":{
                  "metadata":{"phone_number_id":"1307417749115229"},
                  "contacts":[{"profile":{"name":"Cliente"},"wa_id":"5561998765432"}],
                  "messages":[
                    {"from":"5561998765432","id":"wamid.HBgNNTU2MTk5ODc2NTQzMg==","timestamp":"1756839300",
                     "type":"interactive",
                     "interactive":{"type":"list_reply",
                       "list_reply":{"id":"ev03_atendente_6701a2f8-1234-5678-9abc-def012345678","title":"Michael"}}},
                    {"from":"5561998765432","id":"wamid.HBgNNTU2MTk5ODc2NTQzMg==.2","timestamp":"1756839400",
                     "type":"interactive",
                     "interactive":{"type":"button_reply",
                       "button_reply":{"id":"ev08_avaliacao_bom","title":"Bom"}}}
                  ]
                }}]}]}
                """);

        assertThat(mensagens).hasSize(2);
        assertThat(mensagens.get(0).texto()).isEqualTo("Michael");
        assertThat(mensagens.get(0).tipo()).isEqualTo("TEXTO");
        assertThat(mensagens.get(0).identificadorDestino()).isEqualTo("1307417749115229");
        assertThat(mensagens.get(0).telefoneRemetente()).isEqualTo("5561998765432");
        assertThat(mensagens.get(0).idExterno()).isEqualTo("wamid.HBgNNTU2MTk5ODc2NTQzMg==");
        assertThat(mensagens.get(1).texto()).isEqualTo("Bom");
        assertThat(mensagens.get(1).idExterno()).isEqualTo("wamid.HBgNNTU2MTk5ODc2NTQzMg==.2");
    }

    @Test
    void interactiveDeTipoDesconhecidoDescartaSemDerrubarAsDemaisELogaWarn() {
        var mensagens = tradutor.traduzir(payloadComMensagens(
                """
                {"from":"5561000000001","id":"I","timestamp":"1720000000","type":"interactive",
                 "interactive":{"type":"produto_reply","produto_reply":{"id":"x","title":"Ignorado"}}},
                {"from":"5561000000001","id":"A","timestamp":"1720000001","type":"text","text":{"body":"ok"}}
                """));

        assertThat(mensagens).extracting(TradutorDeCanal.MensagemRecebidaDoCanal::idExterno)
                .containsExactly("A");
        assertThat(logs.list)
                .anySatisfy(evento -> {
                    assertThat(evento.getLevel()).isEqualTo(Level.WARN);
                    assertThat(evento.getFormattedMessage())
                            .contains("Resposta interativa sem titulo reconhecido")
                            .contains("type=produto_reply")
                            .contains("produto_reply")
                            .doesNotContain("Ignorado")
                            .doesNotContain("5561000000001");
                });
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
        assertThat(logs.list)
                .anySatisfy(evento -> {
                    assertThat(evento.getLevel()).isEqualTo(Level.WARN);
                    assertThat(evento.getFormattedMessage())
                            .contains("Tipo de mensagem Meta desconhecido")
                            .contains("type=status");
                });
    }

    @Test
    void tipoDesconhecidoUnsupportedContinuaSendoIgnorado() {
        var mensagens = tradutor.traduzir(payloadComMensagens(
                """
                {"from":"5561000000001","id":"U","type":"unsupported","unsupported":{}},
                {"from":"5561000000001","id":"A","type":"text","text":{"body":"ok"}}
                """));

        assertThat(mensagens).extracting(TradutorDeCanal.MensagemRecebidaDoCanal::idExterno)
                .containsExactly("A");
        assertThat(logs.list)
                .anySatisfy(evento -> {
                    assertThat(evento.getLevel()).isEqualTo(Level.WARN);
                    assertThat(evento.getFormattedMessage()).contains("type=unsupported");
                });
    }

    @Test
    void videoEhTraduzidoComoVideoComMetadadosDaMeta() {
        var mensagens = tradutor.traduzir(payloadComMensagens(
                """
                {"from":"5561000000001","id":"V","type":"video",
                 "video":{"id":"media-video","mime_type":"video/mp4","caption":"Veja isto"}}
                """));

        assertThat(mensagens).singleElement().satisfies(mensagem -> {
            assertThat(mensagem.tipo()).isEqualTo("VIDEO");
            assertThat(mensagem.midiaIdExterno()).isEqualTo("media-video");
            assertThat(mensagem.mimetype()).isEqualTo("video/mp4");
            assertThat(mensagem.legenda()).isEqualTo("Veja isto");
            assertThat(mensagem.texto()).isNull();
        });
    }

    @Test
    void figurinhaEhTraduzidaComoImagem() {
        var mensagens = tradutor.traduzir(payloadComMensagens(
                """
                {"from":"5561000000001","id":"S","type":"sticker",
                 "sticker":{"id":"media-sticker","mime_type":"image/webp","sha256":"hash"}}
                """));

        assertThat(mensagens).singleElement().satisfies(mensagem -> {
            assertThat(mensagem.tipo()).isEqualTo("IMAGEM");
            assertThat(mensagem.midiaIdExterno()).isEqualTo("media-sticker");
            assertThat(mensagem.mimetype()).isEqualTo("image/webp");
            assertThat(mensagem.legenda()).isNull();
        });
    }

    @Test
    void videoETextoNaMesmaChangeSaoTraduzidos() {
        var mensagens = tradutor.traduzir(payloadComMensagens(
                """
                {"from":"5561000000001","id":"V","type":"video","video":{"id":"media-video"}},
                {"from":"5561000000001","id":"T","type":"text","text":{"body":"depois"}}
                """));

        assertThat(mensagens).extracting(TradutorDeCanal.MensagemRecebidaDoCanal::tipo)
                .containsExactly("VIDEO", "TEXTO");
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
    void statusesSoComEntregaTraduzSentDeliveredReadFailed() {
        var statuses = tradutor.statusDeEntrega(
                """
                {"entry":[{"changes":[{"value":{"statuses":[
                  {"id":"wamid.s","status":"sent"},
                  {"id":"wamid.d","status":"delivered"},
                  {"id":"wamid.r","status":"read"},
                  {"id":"wamid.f","status":"failed","errors":[{"code":131053,"title":"Media upload error"}]}
                ]}}]}]}
                """);

        assertThat(statuses).extracting(TradutorDeCanal.StatusDeEntregaDoCanal::wamid)
                .containsExactly("wamid.s", "wamid.d", "wamid.r", "wamid.f");
        assertThat(statuses).extracting(TradutorDeCanal.StatusDeEntregaDoCanal::statusEntrega)
                .containsExactly("ENVIADO", "ENTREGUE", "LIDO", "FALHOU");
        assertThat(statuses.get(3).codigoErro()).isEqualTo(131053);
        assertThat(statuses.get(3).tituloErro()).isEqualTo("Media upload error");
    }

    @Test
    void payloadSemStatusesDevolveListaVazia() {
        assertThat(tradutor.statusDeEntrega(payloadComMensagens(
                        """
                        {"from":"5561000000001","id":"A","type":"text","text":{"body":"ok"}}
                        """)))
                .isEmpty();
    }

    @Test
    void statusDesconhecidoDaMetaEIgnorado() {
        var statuses = tradutor.statusDeEntrega(
                """
                {"entry":[{"changes":[{"value":{"statuses":[
                  {"id":"wamid.p","status":"played"},
                  {"id":"wamid.d","status":"delivered"}
                ]}}]}]}
                """);

        assertThat(statuses).extracting(TradutorDeCanal.StatusDeEntregaDoCanal::wamid).containsExactly("wamid.d");
    }

    @Test
    void preservaONumeroDeDestinoDaMeta() {
        var mensagens = tradutor.traduzir("""
                {"entry":[{"changes":[{"value":{"metadata":{"phone_number_id":"999999999999999"},
                  "messages":[{"from":"1","id":"A","type":"text","text":{"body":"a"}}]}}]}]}
                """);

        assertThat(mensagens.get(0).identificadorDestino()).isEqualTo("999999999999999");
    }

    @Test
    void preservaWamidDaMensagemCitada() {
        var mensagens = tradutor.traduzir("""
                {"entry":[{"changes":[{"value":{"messages":[
                  {"from":"5561000000001","id":"wamid-resposta","type":"text",
                   "text":{"body":"pode ser"},"context":{"id":"wamid-origem"}}
                ]}}]}]}
                """);

        assertThat(mensagens).singleElement()
                .extracting(TradutorDeCanal.MensagemRecebidaDoCanal::contextoWamid)
                .isEqualTo("wamid-origem");
    }

    @Test
    void mensagemSemContextoContinuaSemReferencia() {
        var mensagens = tradutor.traduzir(payloadComMensagens(
                "{\"from\":\"5561000000001\",\"id\":\"sem-contexto\",\"type\":\"text\",\"text\":{\"body\":\"oi\"}}"));

        assertThat(mensagens).singleElement()
                .extracting(TradutorDeCanal.MensagemRecebidaDoCanal::contextoWamid)
                .isNull();
    }

    /**
     * Fixture no formato real da Meta no webhook de <em>entrada</em>: {@code type} e a chave do
     * objeto coincidem ({@code button_reply}/{@code list_reply}). A versão antiga usava
     * {@code type=button|list} e aprovava o defeito da E134.
     */
    private static String payload(String tipoResposta, String titulo, String id) {
        return """
                {"entry":[{"changes":[{"value":{
                  "contacts":[{"profile":{"name":"Cliente"}}],
                  "messages":[{"from":"5561999999999","id":"wamid.interativo", "timestamp":"1720000000",
                    "type":"interactive","interactive":{"type":"%s","%s":{"id":"%s","title":"%s"}}}]
                }}]}]}
                """.formatted(tipoResposta, tipoResposta, id, titulo);
    }

    private static String payloadComMensagens(String mensagens) {
        return """
                {"entry":[{"changes":[{"value":{
                  "contacts":[{"wa_id":"5561000000001","profile":{"name":"Cliente"}}],
                  "messages":[%s]
                }}]}]}
                """.formatted(mensagens);
    }
    @Test
    void traduzLocalizacaoComNomeEEndereco() {
        var payload = """
        {
          "object": "whatsapp_business_account",
          "entry": [{
            "id": "112233",
            "changes": [{
              "value": {
                "messaging_product": "whatsapp",
                "metadata": { "display_phone_number": "556199999999", "phone_number_id": "12345" },
                "contacts": [{ "profile": { "name": "Joao" }, "wa_id": "556188888888" }],
                "messages": [{
                  "from": "556188888888",
                  "id": "wamid.location-1",
                  "timestamp": "1720000000",
                  "type": "location",
                  "location": {
                    "latitude": -7.115,
                    "longitude": -34.864,
                    "name": "Condominio Park Cowboy",
                    "address": "R. Dr. Valdevino, 800"
                  }
                }]
              },
              "field": "messages"
            }]
          }]
        }""";

        var resultado = tradutor.traduzir(payload);
        assertThat(resultado).hasSize(1);
        
        TradutorDeCanal.MensagemRecebidaDoCanal msg = resultado.get(0);
        assertThat(msg.tipo()).isEqualTo("LOCALIZACAO");
        assertThat(msg.texto()).contains("\"latitude\":-7.115");
        assertThat(msg.texto()).contains("\"longitude\":-34.864");
        assertThat(msg.texto()).contains("\"nome\":\"Condominio Park Cowboy\"");
        assertThat(msg.texto()).contains("\"endereco\":\"R. Dr. Valdevino, 800\"");
    }

    @Test
    void traduzLocalizacaoSemNomeNemEndereco() {
        var payload = """
        {
          "object": "whatsapp_business_account",
          "entry": [{
            "id": "112233",
            "changes": [{
              "value": {
                "messaging_product": "whatsapp",
                "metadata": { "display_phone_number": "556199999999", "phone_number_id": "12345" },
                "contacts": [{ "profile": { "name": "Joao" }, "wa_id": "556188888888" }],
                "messages": [{
                  "from": "556188888888",
                  "id": "wamid.location-2",
                  "timestamp": "1720000000",
                  "type": "location",
                  "location": {
                    "latitude": -7.115,
                    "longitude": -34.864
                  }
                }]
              },
              "field": "messages"
            }]
          }]
        }""";

        var resultado = tradutor.traduzir(payload);
        assertThat(resultado).hasSize(1);
        
        TradutorDeCanal.MensagemRecebidaDoCanal msg = resultado.get(0);
        assertThat(msg.tipo()).isEqualTo("LOCALIZACAO");
        assertThat(msg.texto()).contains("\"latitude\":-7.115");
        assertThat(msg.texto()).contains("\"longitude\":-34.864");
        assertThat(msg.texto()).doesNotContain("nome");
        assertThat(msg.texto()).doesNotContain("endereco");
    }

    @Test
    void descartaLocalizacaoComLatOuLonInvalidas() {
        var payload = """
        {
          "object": "whatsapp_business_account",
          "entry": [{
            "id": "112233",
            "changes": [{
              "value": {
                "messaging_product": "whatsapp",
                "metadata": { "display_phone_number": "556199999999", "phone_number_id": "12345" },
                "contacts": [{ "profile": { "name": "Joao" }, "wa_id": "556188888888" }],
                "messages": [{
                  "from": "556188888888",
                  "id": "wamid.location-invalid",
                  "timestamp": "1720000000",
                  "type": "location",
                  "location": {
                    "latitude": -91.0,
                    "longitude": -34.864
                  }
                }]
              },
              "field": "messages"
            }]
          }]
        }""";

        var resultado = tradutor.traduzir(payload);
        assertThat(resultado).isEmpty();
        assertThat(logs.list).anyMatch(e -> e.getLevel() == Level.WARN
                && e.getFormattedMessage().contains("Coordenadas de localizacao invalidas"));
    }
}
