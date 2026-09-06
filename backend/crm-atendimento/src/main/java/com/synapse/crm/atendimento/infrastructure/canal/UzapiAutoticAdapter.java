package com.synapse.crm.atendimento.infrastructure.canal;

import java.time.Instant;
import java.util.Optional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
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
 * Anti-Corruption Layer de envio da Uzapi/Autotic.
 *
 * <p>O contrato confirmado do fornecedor fica restrito a este adaptador: a API usa o usuario, a
 * versao e o identificador do numero na URL, Bearer token e os recursos {@code messages}, {@code
 * media} e {@code instance}. Nenhum payload do fornecedor atravessa a porta do canal.
 *
 * <p>O recebimento ainda nao foi investigado. Por isso este adaptador deliberadamente recusa o
 * download de midia recebida e nao fornece tradutor de webhook; uma etapa posterior precisa fechar
 * essa parte antes de habilitar recebimento por este provedor.
 */
@Component
class UzapiAutoticAdapter implements CanalGateway {

    /** Casa com {@code synapse.canal.whatsapp.provedor}. */
    static final String PROVEDOR = "uzapi-autotic";

    private static final String NOME_DO_BREAKER = "canal-uzapi-autotic";
    private static final String NOME_DO_BREAKER_MIDIA = "canal-uzapi-autotic-midia";
    private static final String NOME_DO_BREAKER_SAUDE = "canal-uzapi-autotic-saude";

    private final RestClient http;
    private final CanalProperties propriedades;
    private final ObjectMapper json;
    private final CircuitBreaker breaker;
    private final CircuitBreaker breakerMidia;
    private final CircuitBreaker breakerSaude;
    private final ArmazenamentoDeMidia armazenamento;

    UzapiAutoticAdapter(
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

    /** O contrato Uzapi/Autotic nao impoe janela de 24 horas. */
    @Override
    public boolean aceitaTextoLivre(Optional<Instant> ultimaInteracaoDoLead, Instant agora) {
        return true;
    }

    @Override
    public boolean exigeTemplateForaDaJanela() {
        return false;
    }

    @Override
    public AutenticacaoDoCanal verificarAutenticacao() {
        if (credencialIncompleta()) {
            return AutenticacaoDoCanal.recusada("credencial ou identificador do canal ausente");
        }
        try {
            return breakerSaude.executeSupplier(this::consultarInstancia);
        } catch (CallNotPermittedException e) {
            return AutenticacaoDoCanal.recusada("circuit breaker do provedor aberto");
        } catch (RestClientResponseException e) {
            return AutenticacaoDoCanal.recusada(
                    "provedor recusou a credencial com HTTP " + e.getStatusCode().value());
        } catch (RestClientException e) {
            return AutenticacaoDoCanal.recusada("provedor indisponivel");
        } catch (RuntimeException e) {
            return AutenticacaoDoCanal.recusada("provedor indisponivel");
        }
    }

    private AutenticacaoDoCanal consultarInstancia() {
        http.get()
                .uri(
                        "/{username}/{version}/{phone_number_id}/instance",
                        propriedades.usuarioApi(),
                        propriedades.versaoApi(),
                        propriedades.numeroPrincipal())
                .header("Authorization", "Bearer " + propriedades.token())
                .retrieve()
                .toBodilessEntity();
        return AutenticacaoDoCanal.aceita();
    }

    @Override
    public ResultadoDeEnvio enviar(Envio envio) {
        if (credencialIncompleta()) {
            return ResultadoDeEnvio.Recusado.permanente("configuracao do canal incompleta");
        }
        try {
            return breaker.executeSupplier(() -> enviarDentroDoBreaker(envio));
        } catch (CallNotPermittedException e) {
            return ResultadoDeEnvio.Recusado.temporario("circuit breaker aberto para " + PROVEDOR);
        } catch (RespostaDoProvedorInvalidaException e) {
            return ResultadoDeEnvio.Recusado.permanente(e.getMessage());
        } catch (RestClientResponseException e) {
            return traduzirErro(e);
        } catch (RestClientException e) {
            return ResultadoDeEnvio.Recusado.temporario("provedor indisponivel");
        }
    }

    private ResultadoDeEnvio enviarDentroDoBreaker(Envio envio) {
        try {
            return switch (envio.conteudo()) {
                case ConteudoDeEnvio.MensagemLivre livre -> enviarTexto(envio, livre);
                case ConteudoDeEnvio.MensagemMidia midia -> enviarMidia(envio, midia);
                case ConteudoDeEnvio.MensagemTemplate template ->
                        ResultadoDeEnvio.Recusado.permanente(
                                "uzapi-autotic nao gerencia templates");
            };
        } catch (RestClientResponseException e) {
            return traduzirErro(e);
        }
    }

    private ResultadoDeEnvio enviarTexto(Envio envio, ConteudoDeEnvio.MensagemLivre livre) {
        ObjectNode corpo = corpoBase(envio, "text");
        corpo.putObject("text").put("body", livre.texto());
        return postarMensagem(corpo);
    }

    private ResultadoDeEnvio enviarMidia(Envio envio, ConteudoDeEnvio.MensagemMidia midia) {
        String mediaId = breakerMidia.executeSupplier(() -> subirMidia(midia));
        String tipo = tipoDoProvedor(midia.tipo());
        ObjectNode corpo = corpoBase(envio, tipo);
        ObjectNode conteudo = corpo.putObject(tipo);
        conteudo.put("id", mediaId);
        if (midia.tipo() != TipoMensagem.AUDIO
                && midia.legenda() != null
                && !midia.legenda().isBlank()) {
            conteudo.put("caption", midia.legenda());
        }
        if (midia.tipo() == TipoMensagem.DOCUMENTO) {
            String nome = campoDeMetadados(midia.metadados(), "nome");
            if (nome != null && !nome.isBlank()) {
                conteudo.put("filename", nome);
            }
        }
        return postarMensagem(corpo);
    }

    private ResultadoDeEnvio postarMensagem(ObjectNode corpo) {
        String resposta = http.post()
                .uri(
                        "/{username}/{version}/{phone_number_id}/messages",
                        propriedades.usuarioApi(),
                        propriedades.versaoApi(),
                        propriedades.numeroPrincipal())
                .header("Authorization", "Bearer " + propriedades.token())
                .contentType(MediaType.APPLICATION_JSON)
                .body(corpo)
                .retrieve()
                .body(String.class);
        return interpretarAceite(resposta);
    }

    private ObjectNode corpoBase(Envio envio, String tipo) {
        ObjectNode corpo = json.createObjectNode();
        corpo.put("to", somenteDigitos(envio.telefoneDestino()));
        corpo.put("type", tipo);
        if (envio.contextoWamid() != null && !envio.contextoWamid().isBlank()) {
            corpo.putObject("context").put("message_id", envio.contextoWamid());
        }
        return corpo;
    }

    private String subirMidia(ConteudoDeEnvio.MensagemMidia midia) {
        byte[] bytes = armazenamento.baixar(midia.referenciaStorage());
        String mimetype = campoDeMetadados(midia.metadados(), "mimetype");
        String tipoDoArquivo = MetaCloudMidiaUpload.tipoDoArquivo(
                MetaCloudMidiaUpload.tipoDoCampo(mimetype, midia.tipo()));
        String nome = MetaCloudMidiaUpload.nomeDoArquivo(
                campoDeMetadados(midia.metadados(), "nome"), tipoDoArquivo);

        MultipartBodyBuilder multipart = new MultipartBodyBuilder();
        multipart.part("messaging_product", "whatsapp");
        final byte[] bytesDoArquivo = bytes;
        multipart.part(
                "file",
                new ByteArrayResource(bytesDoArquivo) {
                    @Override
                    public String getFilename() {
                        return nome;
                    }
                },
                MetaCloudMidiaUpload.contentType(tipoDoArquivo));

        String resposta = http.post()
                .uri(
                        "/{username}/{version}/{phone_number_id}/media",
                        propriedades.usuarioApi(),
                        propriedades.versaoApi(),
                        propriedades.numeroPrincipal())
                .header("Authorization", "Bearer " + propriedades.token())
                .body(multipart.build())
                .retrieve()
                .body(String.class);
        return idDoUpload(resposta);
    }

    private ResultadoDeEnvio interpretarAceite(String resposta) {
        JsonNode raiz = lerJson(resposta, "resposta do envio");
        boolean sucesso = "success".equalsIgnoreCase(raiz.path("status").asText())
                && !raiz.has("error");
        JsonNode mensagens = raiz.path("messages");
        String id = mensagens.isArray() && !mensagens.isEmpty()
                ? mensagens.get(0).path("id").asText("").trim()
                : "";
        if (!sucesso || id.isBlank()) {
            throw new RespostaDoProvedorInvalidaException(
                    "resposta de sucesso do provedor invalida: id da mensagem ausente");
        }
        return new ResultadoDeEnvio.Aceito(id);
    }

    private String idDoUpload(String resposta) {
        JsonNode raiz = lerJson(resposta, "resposta do upload");
        String id = raiz.path("id").asText("").trim();
        if (id.isBlank() || raiz.has("error")) {
            throw new RespostaDoProvedorInvalidaException(
                    "resposta de sucesso do upload invalida: id de midia ausente");
        }
        return id;
    }

    private JsonNode lerJson(String resposta, String operacao) {
        if (resposta == null || resposta.isBlank()) {
            throw new RespostaDoProvedorInvalidaException(
                    "resposta de sucesso do provedor invalida: corpo vazio em " + operacao);
        }
        try {
            return json.readTree(resposta);
        } catch (JsonProcessingException | RuntimeException e) {
            throw new RespostaDoProvedorInvalidaException(
                    "resposta de sucesso do provedor invalida: JSON ilegivel em " + operacao);
        }
    }

    private ResultadoDeEnvio traduzirErro(RestClientResponseException e) {
        int status = e.getStatusCode().value();
        String motivo = status >= 400 && status < 500
                ? "provedor recusou a requisicao com HTTP " + status
                : "provedor indisponivel com HTTP " + status;
        return status >= 400 && status < 500
                ? ResultadoDeEnvio.Recusado.permanente(motivo)
                : ResultadoDeEnvio.Recusado.temporario(motivo);
    }

    private boolean credencialIncompleta() {
        return vazio(propriedades.token())
                || vazio(propriedades.numeroPrincipal())
                || vazio(propriedades.usuarioApi())
                || vazio(propriedades.versaoApi());
    }

    private static boolean vazio(String valor) {
        return valor == null || valor.isBlank();
    }

    private static String somenteDigitos(String telefone) {
        String digitos = telefone == null ? "" : telefone.replaceAll("\\D", "");
        if (digitos.isBlank()) {
            throw new RespostaDoProvedorInvalidaException("numero de destino ausente");
        }
        return digitos;
    }

    static String tipoDoProvedor(TipoMensagem tipo) {
        return switch (tipo) {
            case IMAGEM -> "image";
            case AUDIO -> "audio";
            case DOCUMENTO -> "document";
            case VIDEO -> "video";
            case TEXTO -> throw new IllegalArgumentException("TEXTO nao e um tipo de midia");
            case LOCALIZACAO -> throw new IllegalArgumentException("LOCALIZACAO nao e midia transferida");
            case BOTOES, LISTA -> throw new IllegalArgumentException("mensagem interativa nao e midia");
        };
    }

    private String campoDeMetadados(String metadadosJson, String campo) {
        if (metadadosJson == null || metadadosJson.isBlank()) {
            return null;
        }
        try {
            JsonNode valor = json.readTree(metadadosJson).path(campo);
            return valor.isMissingNode() || valor.isNull() ? null : valor.asText();
        } catch (JsonProcessingException | RuntimeException e) {
            return null;
        }
    }

    @Override
    public MidiaRecebida baixarMidiaRecebida(String midiaIdExterno) {
        throw new UnsupportedOperationException("recebimento uzapi-autotic ainda nao investigado");
    }

    private static final class RespostaDoProvedorInvalidaException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        RespostaDoProvedorInvalidaException(String mensagem) {
            super(mensagem);
        }
    }
}
