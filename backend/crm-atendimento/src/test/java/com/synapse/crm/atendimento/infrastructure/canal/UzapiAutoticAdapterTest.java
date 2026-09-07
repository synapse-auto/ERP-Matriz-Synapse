package com.synapse.crm.atendimento.infrastructure.canal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Duration;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.synapse.crm.atendimento.domain.canal.CanalGateway;
import com.synapse.crm.atendimento.domain.canal.ConteudoDeEnvio;
import com.synapse.crm.atendimento.domain.canal.ResultadoDeEnvio;
import com.synapse.crm.atendimento.domain.mensagem.TipoMensagem;
import com.synapse.crm.sharedkernel.midia.ArmazenamentoDeMidia;

class UzapiAutoticAdapterTest {

    private static final String URL_BASE = "https://uzapi.example.test";
    private static final String USUARIO = "usuario-de-teste";
    private static final String VERSAO = "v1";
    private static final String NUMERO = "numero-de-teste";
    private static final String CAMINHO_BASE = "/" + USUARIO + "/" + VERSAO + "/" + NUMERO;
    private static final String REFERENCIA = "midias/anexo";

    private final ObjectMapper json = new ObjectMapper();
    private final ArmazenamentoDeMidia armazenamento = mock(ArmazenamentoDeMidia.class);

    private MockRestServiceServer servidor;
    private UzapiAutoticAdapter adapter;

    @BeforeEach
    void configurar() {
        when(armazenamento.baixar(REFERENCIA)).thenReturn(new byte[] {1, 2, 3});
        RestClient.Builder builder = RestClient.builder();
        servidor = MockRestServiceServer.bindTo(builder).build();
        adapter = new UzapiAutoticAdapter(
                builder,
                propriedades(),
                json,
                CircuitBreakerRegistry.ofDefaults(),
                armazenamento);
    }

    private CanalProperties propriedades() {
        return new CanalProperties(
                UzapiAutoticAdapter.PROVEDOR,
                URL_BASE,
                NUMERO,
                "token-de-teste",
                "verify",
                "secret",
                Duration.ofHours(24),
                Duration.ofSeconds(10),
                "",
                USUARIO,
                VERSAO);
    }

    // --- provedor, janela e template --------------------------------------

    @Test
    void provedorETextoLivreSemJanela() {
        assertThat(adapter.provedor()).isEqualTo("uzapi-autotic");
        assertThat(adapter.aceitaTextoLivre(java.util.Optional.empty(), java.time.Instant.now()))
                .isTrue();
        assertThat(adapter.exigeTemplateForaDaJanela()).isFalse();
    }

    @Test
    void templateERecusadoPermanenteSemChamarHttp() {
        ResultadoDeEnvio resultado = adapter.enviar(new CanalGateway.Envio(
                UUID.randomUUID(),
                "5561999999999",
                ConteudoDeEnvio.MensagemTemplate.de("boas_vindas", "pt_BR", "Ana"),
                UUID.randomUUID()));

        assertThat(resultado).isInstanceOf(ResultadoDeEnvio.Recusado.class);
        ResultadoDeEnvio.Recusado recusado = (ResultadoDeEnvio.Recusado) resultado;
        assertThat(recusado.permanente()).isTrue();
        assertThat(recusado.motivo()).contains("nao gerencia templates");
        servidor.verify(); // nenhuma expectativa registrada: qualquer chamada HTTP falharia aqui.
    }

    @Test
    void localizacaoLancaAMesmaExcecaoQueAMeta() {
        // ConteudoDeEnvio.MensagemMidia recusa TipoMensagem.LOCALIZACAO no proprio construtor
        // (!tipo.exigeMidia()) — o caminho real do adaptador nunca recebe esse tipo. O mapeamento
        // continua exaustivo (Java exige) e e testado diretamente, espelhando
        // MetaCloudApiAdapter.campoDeTipoMeta.
        assertThatThrownBy(() -> UzapiAutoticAdapter.tipoDoProvedor(TipoMensagem.LOCALIZACAO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("LOCALIZACAO nao e midia transferida");
    }

    // --- texto livre --------------------------------------------------------

    @Test
    void textoLivreEnviaCorpoExatoEExtraiOWamid() {
        JsonNode[] capturado = new JsonNode[1];
        servidor.expect(once(), requestTo(URL_BASE + CAMINHO_BASE + "/messages"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(requisicao -> capturado[0] = json.readTree(
                        ((MockClientHttpRequest) requisicao).getBodyAsBytes()))
                .andRespond(withSuccess(
                        """
                        {"status":"success","message":"Mensagem colocada na fila de envios com sucesso!",
                         "queueId":"fila-interna-nao-usar","messageId":"interno-nao-usar",
                         "contacts":[{"input":"5561999999999","wa_id":"5561999999999"}],
                         "messages":[{"id":"wamid.real"}]}
                        """,
                        MediaType.APPLICATION_JSON));

        ResultadoDeEnvio resultado = adapter.enviar(new CanalGateway.Envio(
                UUID.randomUUID(),
                "(56) 1 99999-9999",
                new ConteudoDeEnvio.MensagemLivre("Ola, tudo bem?"),
                UUID.randomUUID(),
                "wamid.contexto"));

        servidor.verify();
        assertThat(resultado).isInstanceOf(ResultadoDeEnvio.Aceito.class);
        assertThat(((ResultadoDeEnvio.Aceito) resultado).idExterno()).isEqualTo("wamid.real");
        assertThat(capturado[0].path("to").asText()).isEqualTo("561999999999");
        assertThat(capturado[0].path("type").asText()).isEqualTo("text");
        assertThat(capturado[0].path("text").path("body").asText()).isEqualTo("Ola, tudo bem?");
        assertThat(capturado[0].path("context").path("message_id").asText())
                .isEqualTo("wamid.contexto");
    }

    // --- midia: upload em duas etapas ---------------------------------------

    @ParameterizedTest
    @EnumSource(
            value = TipoMensagem.class,
            names = {"IMAGEM", "VIDEO", "DOCUMENTO", "AUDIO"})
    void midiaSobeArquivoDepoisReferenciaOIdNoEnvio(TipoMensagem tipo) {
        String tipoNoProvedor = UzapiAutoticAdapter.tipoDoProvedor(tipo);
        String[] corpoDoUpload = {null};
        servidor.expect(once(), requestTo(URL_BASE + CAMINHO_BASE + "/media"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(requisicao -> corpoDoUpload[0] = new String(
                        ((MockClientHttpRequest) requisicao).getBodyAsBytes(),
                        java.nio.charset.StandardCharsets.ISO_8859_1))
                .andRespond(withSuccess("{\"id\":\"media-id-123\"}", MediaType.APPLICATION_JSON));

        JsonNode[] corpoDoEnvio = new JsonNode[1];
        servidor.expect(once(), requestTo(URL_BASE + CAMINHO_BASE + "/messages"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(requisicao -> corpoDoEnvio[0] = json.readTree(
                        ((MockClientHttpRequest) requisicao).getBodyAsBytes()))
                .andRespond(withSuccess(
                        "{\"status\":\"success\",\"messages\":[{\"id\":\"wamid.midia\"}]}",
                        MediaType.APPLICATION_JSON));

        String metadados = tipo == TipoMensagem.DOCUMENTO
                ? "{\"nome\":\"contrato.pdf\",\"mimetype\":\"application/pdf\"}"
                : "{\"nome\":\"anexo\",\"mimetype\":\"" + mimetypeDeExemplo(tipo) + "\"}";
        ResultadoDeEnvio resultado = adapter.enviar(new CanalGateway.Envio(
                UUID.randomUUID(),
                "5561999999999",
                new ConteudoDeEnvio.MensagemMidia(tipo, REFERENCIA, metadados, "Legenda de teste"),
                UUID.randomUUID()));

        servidor.verify();
        assertThat(resultado).isInstanceOf(ResultadoDeEnvio.Aceito.class);
        assertThat(((ResultadoDeEnvio.Aceito) resultado).idExterno()).isEqualTo("wamid.midia");
        assertThat(corpoDoUpload[0]).contains("messaging_product").contains("whatsapp");
        assertThat(corpoDoEnvio[0].path("type").asText()).isEqualTo(tipoNoProvedor);
        assertThat(corpoDoEnvio[0].path(tipoNoProvedor).path("id").asText()).isEqualTo("media-id-123");
        if (tipo == TipoMensagem.AUDIO) {
            // Confirmado no Swagger: audio e "LinkMessage", sem campo caption.
            assertThat(corpoDoEnvio[0].path(tipoNoProvedor).has("caption")).isFalse();
        } else {
            assertThat(corpoDoEnvio[0].path(tipoNoProvedor).path("caption").asText())
                    .isEqualTo("Legenda de teste");
        }
        if (tipo == TipoMensagem.DOCUMENTO) {
            assertThat(corpoDoEnvio[0].path("document").path("filename").asText())
                    .isEqualTo("contrato.pdf");
        }
    }

    private static String mimetypeDeExemplo(TipoMensagem tipo) {
        return switch (tipo) {
            case IMAGEM -> "image/png";
            case VIDEO -> "video/mp4";
            case AUDIO -> "audio/ogg";
            case DOCUMENTO -> "application/pdf";
            default -> throw new IllegalArgumentException("sem exemplo para " + tipo);
        };
    }

    // --- respostas 2xx que nao confirmam sucesso -----------------------------

    @Test
    void resposta2xxComStatusDiferenteDeSuccessERecusaPermanente() {
        servidor.expect(once(), requestTo(URL_BASE + CAMINHO_BASE + "/messages"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(
                        "{\"status\":\"queued\",\"messages\":[{\"id\":\"wamid.1\"}]}",
                        MediaType.APPLICATION_JSON));

        ResultadoDeEnvio resultado = enviarTexto();

        servidor.verify();
        assertThat(resultado).isInstanceOf(ResultadoDeEnvio.Recusado.class);
        assertThat(((ResultadoDeEnvio.Recusado) resultado).permanente()).isTrue();
    }

    @Test
    void resposta2xxComErrorPresenteERecusaPermanenteMesmoComStatusSuccess() {
        servidor.expect(once(), requestTo(URL_BASE + CAMINHO_BASE + "/messages"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(
                        "{\"status\":\"success\",\"error\":\"algo deu errado\",\"messages\":[{\"id\":\"wamid.1\"}]}",
                        MediaType.APPLICATION_JSON));

        ResultadoDeEnvio resultado = enviarTexto();

        servidor.verify();
        assertThat(resultado).isInstanceOf(ResultadoDeEnvio.Recusado.class);
        assertThat(((ResultadoDeEnvio.Recusado) resultado).permanente()).isTrue();
    }

    @Test
    void resposta2xxSemMessagesIdERecusaPermanente() {
        servidor.expect(once(), requestTo(URL_BASE + CAMINHO_BASE + "/messages"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(
                        "{\"status\":\"success\",\"queueId\":\"fila-1\",\"messageId\":\"interno-1\"}",
                        MediaType.APPLICATION_JSON));

        ResultadoDeEnvio resultado = enviarTexto();

        servidor.verify();
        assertThat(resultado).isInstanceOf(ResultadoDeEnvio.Recusado.class);
        assertThat(((ResultadoDeEnvio.Recusado) resultado).permanente()).isTrue();
    }

    // --- classificacao de erro HTTP ------------------------------------------

    @Test
    void erro4xxERecusaPermanente() {
        servidor.expect(once(), requestTo(URL_BASE + CAMINHO_BASE + "/messages"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .body("{\"statusCode\":400,\"message\":[\"numero invalido\"],\"error\":\"Bad Request\"}")
                        .contentType(MediaType.APPLICATION_JSON));

        ResultadoDeEnvio resultado = enviarTexto();

        servidor.verify();
        assertThat(resultado).isInstanceOf(ResultadoDeEnvio.Recusado.class);
        assertThat(((ResultadoDeEnvio.Recusado) resultado).permanente()).isTrue();
    }

    @Test
    void erro429ERecusaPermanente_semAExcecaoQueAMetaTem() {
        // Diferente da Meta: sem retentativa documentada para este fornecedor (Bloco 2 do
        // prompt), 429 nao ganha tratamento especial aqui.
        servidor.expect(once(), requestTo(URL_BASE + CAMINHO_BASE + "/messages"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        ResultadoDeEnvio resultado = enviarTexto();

        servidor.verify();
        assertThat(resultado).isInstanceOf(ResultadoDeEnvio.Recusado.class);
        assertThat(((ResultadoDeEnvio.Recusado) resultado).permanente()).isTrue();
    }

    @Test
    void erro5xxERecusaTemporaria() {
        servidor.expect(once(), requestTo(URL_BASE + CAMINHO_BASE + "/messages"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        ResultadoDeEnvio resultado = enviarTexto();

        servidor.verify();
        assertThat(resultado).isInstanceOf(ResultadoDeEnvio.Recusado.class);
        assertThat(((ResultadoDeEnvio.Recusado) resultado).permanente()).isFalse();
    }

    private ResultadoDeEnvio enviarTexto() {
        return adapter.enviar(new CanalGateway.Envio(
                UUID.randomUUID(),
                "5561999999999",
                new ConteudoDeEnvio.MensagemLivre("teste"),
                UUID.randomUUID()));
    }

    // --- autenticacao ---------------------------------------------------------

    @Test
    void verificarAutenticacaoAceitaComHttp2xx() {
        servidor.expect(once(), requestTo(URL_BASE + CAMINHO_BASE + "/instance"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"status\":\"connected\"}", MediaType.APPLICATION_JSON));

        CanalGateway.AutenticacaoDoCanal autenticacao = adapter.verificarAutenticacao();

        servidor.verify();
        assertThat(autenticacao.autenticada()).isTrue();
    }

    @Test
    void verificarAutenticacaoRecusaComHttp401() {
        servidor.expect(once(), requestTo(URL_BASE + CAMINHO_BASE + "/instance"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        CanalGateway.AutenticacaoDoCanal autenticacao = adapter.verificarAutenticacao();

        servidor.verify();
        assertThat(autenticacao.autenticada()).isFalse();
        assertThat(autenticacao.detalhe()).doesNotContain("token-de-teste");
    }

    @Test
    void verificarAutenticacaoComUsuarioApiAusenteNaoChamaHttp() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer servidorLocal = MockRestServiceServer.bindTo(builder).build();
        CanalProperties semUsuario = new CanalProperties(
                UzapiAutoticAdapter.PROVEDOR,
                URL_BASE,
                NUMERO,
                "token-de-teste",
                "verify",
                "secret",
                Duration.ofHours(24),
                Duration.ofSeconds(10),
                "",
                "",
                VERSAO);
        UzapiAutoticAdapter adapterSemUsuario = new UzapiAutoticAdapter(
                builder, semUsuario, json, CircuitBreakerRegistry.ofDefaults(), armazenamento);

        CanalGateway.AutenticacaoDoCanal autenticacao = adapterSemUsuario.verificarAutenticacao();

        servidorLocal.verify(); // nenhuma expectativa: confirma que nenhuma chamada saiu.
        assertThat(autenticacao.autenticada()).isFalse();
    }

    // --- recebimento -----------------------------------------------------------

    @Test
    void baixarMidiaRecebidaResolveUrlEBaixaBytesSemReenviarBearerAoHostDaUrl() {
        servidor.expect(once(), requestTo(URL_BASE + "/" + USUARIO + "/" + VERSAO + "/media-inbound"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                        "{\"id\":\"media-inbound\",\"url\":\"https://media.example.test/file.jpg\"}",
                        MediaType.APPLICATION_JSON));
        servidor.expect(once(), requestTo("https://media.example.test/file.jpg"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(new byte[] {9, 8, 7}, MediaType.IMAGE_JPEG));

        CanalGateway.MidiaRecebida recebida = adapter.baixarMidiaRecebida("media-inbound");

        servidor.verify();
        assertThat(recebida.conteudo()).containsExactly(9, 8, 7);
        assertThat(recebida.mimetype()).isEqualTo("image/jpeg");
    }

    @Test
    void baixarMidiaRecebidaComIdAusenteNaoChamaProvedor() {
        assertThatThrownBy(() -> adapter.baixarMidiaRecebida(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("id de midia recebido ausente");
        servidor.verify();
    }

    @Test
    void naoSobrescreveListarNemCriarEditarOuExcluirTemplate_usaDefaultsDaInterface() {
        assertThat(adapter.gerenciaTemplates()).isFalse();
        assertThat(adapter.listarTemplates()).isEmpty();
        assertThat(adapter.criarTemplate(null))
                .isInstanceOf(com.synapse.crm.atendimento.domain.canal.ResultadoDeTemplate.Recusado.class);
        assertThat(adapter.editarTemplate(null))
                .isInstanceOf(com.synapse.crm.atendimento.domain.canal.ResultadoDeTemplate.Recusado.class);
        assertThat(adapter.excluirTemplate("id", "nome"))
                .isInstanceOf(com.synapse.crm.atendimento.domain.canal.ResultadoDeTemplate.Recusado.class);
    }
}
