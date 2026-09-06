package com.synapse.crm.atendimento.infrastructure.canal;

import java.util.Base64;
import java.util.Optional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import com.synapse.crm.atendimento.domain.canal.CanalGateway;
import com.synapse.crm.atendimento.domain.canal.ConteudoDeEnvio;
import com.synapse.crm.atendimento.domain.canal.ResultadoDeEnvio;
import com.synapse.crm.atendimento.domain.mensagem.TipoMensagem;
import com.synapse.crm.sharedkernel.midia.ArmazenamentoDeMidia;

/**
 * Anti-Corruption Layer do envio pela UazAPI.
 *
 * <p>O adaptador traduz o vocabulario do CRM para os corpos de {@code /send/text} e
 * {@code /send/media}. O recebimento continua deliberadamente bloqueado ate que o contrato do
 * webhook seja confirmado empiricamente no E148b.
 */
@Component
class UazApiAdapter implements CanalGateway {

    static final String PROVEDOR = "uazapi";

    private static final String NOME_DO_BREAKER = "canal-uazapi";
    private static final String NOME_DO_BREAKER_MIDIA = "canal-uazapi-midia";
    private static final String NOME_DO_BREAKER_SAUDE = "canal-uazapi-saude";

    private final RestClient http;
    private final CanalProperties propriedades;
    private final ObjectMapper json;
    private final CircuitBreaker breaker;
    private final CircuitBreaker breakerMidia;
    private final CircuitBreaker breakerSaude;
    private final ArmazenamentoDeMidia armazenamento;

    UazApiAdapter(
            RestClient.Builder builder,
            CanalProperties propriedades,
            ObjectMapper json,
            CircuitBreakerRegistry breakers,
            ArmazenamentoDeMidia armazenamento) {
        this.http = builder.baseUrl(propriedades.urlBase()).build();
        this.propriedades = propriedades;
        this.json = json;
        this.breaker = breakers.circuitBreaker(NOME_DO_BREAKER);
        this.breakerMidia = breakers.circuitBreaker(NOME_DO_BREAKER_MIDIA);
        this.breakerSaude = breakers.circuitBreaker(NOME_DO_BREAKER_SAUDE);
        this.armazenamento = armazenamento;
    }

    @Override
    public String provedor() {
        return PROVEDOR;
    }

    @Override
    public boolean aceitaTextoLivre(Optional<java.time.Instant> ultimaInteracaoDoLead,
            java.time.Instant agora) {
        return true;
    }

    @Override
    public boolean exigeTemplateForaDaJanela() {
        return false;
    }

    @Override
    public AutenticacaoDoCanal verificarAutenticacao() {
        if (propriedades.token() == null || propriedades.token().isBlank()) {
            return AutenticacaoDoCanal.recusada("token do canal ausente");
        }
        try {
            return breakerSaude.executeSupplier(this::consultarStatusDaInstancia);
        } catch (CallNotPermittedException e) {
            return AutenticacaoDoCanal.recusada("circuit breaker do provedor aberto");
        } catch (RestClientResponseException e) {
            return AutenticacaoDoCanal.recusada(
                    "provedor recusou a credencial com HTTP " + e.getStatusCode().value());
        } catch (RestClientException e) {
            return AutenticacaoDoCanal.recusada(
                    "provedor indisponivel: " + e.getClass().getSimpleName());
        }
    }

    private AutenticacaoDoCanal consultarStatusDaInstancia() {
        http.get()
                .uri("/instance/status")
                .header("token", propriedades.token())
                .retrieve()
                .toBodilessEntity();
        return AutenticacaoDoCanal.aceita();
    }

    @Override
    public ResultadoDeEnvio enviar(Envio envio) {
        try {
            return switch (envio.conteudo()) {
                case ConteudoDeEnvio.MensagemLivre livre ->
                    breaker.executeSupplier(() -> enviarTexto(envio, livre));
                case ConteudoDeEnvio.MensagemMidia midia ->
                    breakerMidia.executeSupplier(() -> enviarMidia(envio, midia));
                case ConteudoDeEnvio.MensagemTemplate ignored ->
                    ResultadoDeEnvio.Recusado.permanente("uazapi nao gerencia templates");
            };
        } catch (CallNotPermittedException e) {
            return ResultadoDeEnvio.Recusado.temporario("circuit breaker aberto para " + PROVEDOR);
        } catch (RestClientResponseException e) {
            return traduzirErro(e);
        } catch (RestClientException e) {
            // A excecao sai do supplier para que o breaker conte a falha antes de a traduzirmos.
            return ResultadoDeEnvio.Recusado.temporario(
                    "provedor indisponivel: " + e.getClass().getSimpleName());
        }
    }

    private ResultadoDeEnvio enviarTexto(Envio envio, ConteudoDeEnvio.MensagemLivre livre) {
        ObjectNode corpo = json.createObjectNode();
        corpo.put("number", envio.telefoneDestino());
        corpo.put("text", livre.texto());
        adicionarResposta(corpo, envio);
        return interpretarAceite(chamar("/send/text", corpo));
    }

    private ResultadoDeEnvio enviarMidia(Envio envio, ConteudoDeEnvio.MensagemMidia midia) {
        byte[] bytes = armazenamento.baixar(midia.referenciaStorage());
        ObjectNode corpo = json.createObjectNode();
        corpo.put("number", envio.telefoneDestino());
        corpo.put("type", tipoDaUazApi(midia.tipo(), campoDeMetadados(midia.metadados(), "mimetype")));
        corpo.put("file", Base64.getEncoder().encodeToString(bytes));

        if (midia.legenda() != null && !midia.legenda().isBlank()) {
            corpo.put("text", midia.legenda());
        }
        String mimetype = campoDeMetadados(midia.metadados(), "mimetype");
        if (mimetype != null && !mimetype.isBlank()) {
            corpo.put("mimetype", mimetype);
        }
        if (midia.tipo() == TipoMensagem.DOCUMENTO) {
            String nome = campoDeMetadados(midia.metadados(), "nome");
            if (nome != null && !nome.isBlank()) {
                corpo.put("docName", nome);
            }
        }
        adicionarResposta(corpo, envio);
        return interpretarAceite(chamar("/send/media", corpo));
    }

    private static void adicionarResposta(ObjectNode corpo, Envio envio) {
        if (envio.contextoWamid() != null && !envio.contextoWamid().isBlank()) {
            corpo.put("replyid", envio.contextoWamid());
        }
    }

    private String chamar(String caminho, ObjectNode corpo) {
        return http.post()
                .uri(caminho)
                .header("token", propriedades.token())
                .contentType(MediaType.APPLICATION_JSON)
                .body(corpo)
                .retrieve()
                .body(String.class);
    }

    private ResultadoDeEnvio traduzirErro(RestClientResponseException e) {
        int status = e.getStatusCode().value();
        String motivo = resumoDoErro(e.getResponseBodyAsString());
        String detalhe = "HTTP " + status + (motivo.isBlank() ? "" : " " + motivo);
        return status == 429 || status >= 500
                ? ResultadoDeEnvio.Recusado.temporario(detalhe)
                : ResultadoDeEnvio.Recusado.permanente(detalhe);
    }

    private ResultadoDeEnvio interpretarAceite(String resposta) {
        if (resposta == null || resposta.isBlank()) {
            return new ResultadoDeEnvio.Aceito("");
        }
        try {
            JsonNode raiz = json.readTree(resposta);
            return new ResultadoDeEnvio.Aceito(idExternoDaResposta(raiz));
        } catch (JsonProcessingException | RuntimeException e) {
            return new ResultadoDeEnvio.Aceito("");
        }
    }

    /** A resposta documentada da UazAPI usa {@code messageid}; {@code id} e {@code data} sao fallback. */
    private static String idExternoDaResposta(JsonNode raiz) {
        String messageId = raiz.path("messageid").asText("");
        if (!messageId.isBlank()) {
            return messageId;
        }
        String id = raiz.path("id").asText("");
        if (!id.isBlank()) {
            return id;
        }
        JsonNode data = raiz.path("data");
        messageId = data.path("messageid").asText("");
        return messageId.isBlank() ? data.path("id").asText("") : messageId;
    }

    static String tipoDaUazApi(TipoMensagem tipo, String mimetype) {
        return switch (tipo) {
            case IMAGEM -> "image";
            case VIDEO -> "video";
            case DOCUMENTO -> "document";
            case AUDIO -> MetaCloudMidiaUpload.ehNotaDeVoz(mimetype) ? "ptt" : "audio";
            case LOCALIZACAO -> throw new IllegalArgumentException("LOCALIZACAO nao e midia transferida");
            case TEXTO -> throw new IllegalArgumentException("TEXTO nao e um tipo de midia");
            case BOTOES, LISTA -> throw new IllegalArgumentException("mensagem interativa nao e midia");
        };
    }

    private String campoDeMetadados(String metadados, String campo) {
        if (metadados == null || metadados.isBlank()) {
            return null;
        }
        try {
            JsonNode valor = json.readTree(metadados).path(campo);
            return valor.isMissingNode() || valor.isNull() ? null : valor.asText();
        } catch (JsonProcessingException | RuntimeException e) {
            return null;
        }
    }

    private String resumoDoErro(String corpo) {
        if (corpo == null || corpo.isBlank()) {
            return "";
        }
        try {
            JsonNode raiz = json.readTree(corpo);
            JsonNode erro = raiz.path("error");
            if (erro.isTextual()) {
                return semCredencial(erro.asText());
            }
            String mensagem = erro.path("message").asText("");
            if (!mensagem.isBlank()) {
                return semCredencial(mensagem);
            }
            return semCredencial(raiz.path("message").asText(""));
        } catch (JsonProcessingException | RuntimeException e) {
            return "";
        }
    }

    private String semCredencial(String texto) {
        String token = propriedades.token();
        return token == null || token.isBlank() ? texto : texto.replace(token, "[redacted]");
    }

    @Override
    public MidiaRecebida baixarMidiaRecebida(String midiaIdExterno) {
        throw new UnsupportedOperationException("recebimento uazapi depende do levantamento do E148b");
    }
}
