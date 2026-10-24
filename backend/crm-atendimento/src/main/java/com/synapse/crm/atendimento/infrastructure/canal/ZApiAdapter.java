package com.synapse.crm.atendimento.infrastructure.canal;

import java.io.IOException;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.synapse.crm.atendimento.domain.canal.CanalGateway;
import com.synapse.crm.atendimento.domain.canal.CanalIndisponivelException;
import com.synapse.crm.atendimento.domain.canal.ConteudoDeEnvio;
import com.synapse.crm.atendimento.domain.canal.ResultadoDeEnvio;

/**
 * Saida experimental e desligada. Sem tradutor de entrada nao e selecionavel em uma instancia.
 * Nenhum erro bruto do cliente HTTP atravessa esta fronteira: o token faz parte do path.
 */
@Component
@ConditionalOnProperty(prefix = "synapse.canal.z-api", name = "habilitado", havingValue = "true")
class ZApiAdapter implements CanalGateway {
    private static final String URL_BASE = "https://api.z-api.io";
    private final ZApiProperties propriedades;
    private final ObjectMapper json;
    private final RestClient http;
    private final CircuitBreaker envioBreaker;
    private final CircuitBreaker saudeBreaker;
    private final Bulkhead limite;

    @Autowired
    ZApiAdapter(ZApiProperties propriedades, ObjectMapper json, CircuitBreakerRegistry breakers) {
        this(clienteIsolado(propriedades), propriedades, json, breakers);
    }

    /** Builder nao gerenciado; testes substituem somente o transporte, nunca o contrato. */
    ZApiAdapter(RestClient.Builder cliente, ZApiProperties propriedades,
            ObjectMapper json, CircuitBreakerRegistry breakers) {
        this.propriedades = propriedades;
        this.json = json;
        // Nao herda interceptadores, tracing ou metricas com URI contendo segredo.
        this.http = cliente.observationRegistry(ObservationRegistry.NOOP).build();
        this.envioBreaker = breakers.circuitBreaker("canal-z-api");
        this.saudeBreaker = breakers.circuitBreaker("canal-z-api-saude");
        this.limite = Bulkhead.of("canal-z-api", BulkheadConfig.custom()
                .maxConcurrentCalls(Math.max(1, propriedades.concorrencia()))
                .maxWaitDuration(Duration.ZERO).build());
    }

    private static RestClient.Builder clienteIsolado(ZApiProperties props) {
        HttpClient.Builder cliente = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER);
        if (props.timeout() != null && props.timeout().isPositive()) {
            cliente.connectTimeout(props.timeout());
        }
        JdkClientHttpRequestFactory fabrica = new JdkClientHttpRequestFactory(cliente.build());
        if (props.timeout() != null && props.timeout().isPositive()) {
            fabrica.setReadTimeout(props.timeout());
        }
        return RestClient.builder().requestFactory(fabrica);
    }

    @Override
    public String provedor() { return "z-api"; }

    @Override
    public boolean aceitaTextoLivre(Optional<Instant> ultimaInteracaoDoLead, Instant agora) { return true; }

    @Override
    public boolean exigeTemplateForaDaJanela() { return false; }

    @Override
    public ResultadoDeEnvio enviar(Envio envio) {
        if (!propriedades.configurada()) {
            return ResultadoDeEnvio.Recusado.permanente("Z_API_CONFIGURACAO_INDISPONIVEL");
        }
        if (!propriedades.credencialId().equals(envio.credencialId())) {
            return ResultadoDeEnvio.Recusado.permanente("Z_API_CREDENCIAL_DIVERGENTE");
        }
        if (!(envio.conteudo() instanceof ConteudoDeEnvio.MensagemLivre texto)) {
            return ResultadoDeEnvio.Recusado.permanente("Z_API_CONTEUDO_NAO_SUPORTADO");
        }
        if (envio.contextoWamid() != null && !envio.contextoWamid().isBlank()) {
            return ResultadoDeEnvio.Recusado.permanente("Z_API_CITACAO_SEM_CORRELACAO");
        }
        if (envio.telefoneDestino() == null || !envio.telefoneDestino().matches("[1-9][0-9]{7,14}")) {
            return ResultadoDeEnvio.Recusado.permanente("Z_API_DESTINO_INVALIDO");
        }
        try {
            return limite.executeSupplier(() -> envioBreaker.executeSupplier(() -> enviarTexto(envio, texto)));
        } catch (CallNotPermittedException e) {
            return ResultadoDeEnvio.Recusado.temporario("Z_API_DISJUNTOR_ABERTO");
        } catch (BulkheadFullException e) {
            return ResultadoDeEnvio.Recusado.temporario("Z_API_LIMITE_DE_CONCORRENCIA");
        } catch (RuntimeException e) {
            // Inclusive timeout/5xx: a ausencia de resposta nao prova ausencia de efeito do POST.
            return new ResultadoDeEnvio.Ambiguo("Z_API_RESPOSTA_NAO_CONFIRMADA");
        }
    }

    private ResultadoDeEnvio enviarTexto(Envio envio, ConteudoDeEnvio.MensagemLivre texto) {
        JsonNode resposta = requisitarTexto(envio, texto);
        if (resposta.hasNonNull("error") && (!resposta.path("error").isTextual()
                || !resposta.path("error").asText().isBlank())) {
            throw new RespostaNaoConfirmada();
        }
        String processamento = identificador(resposta, "zaapId");
        String mensagem = identificador(resposta, "messageId");
        String alias = identificador(resposta, "id");
        if (processamento == null || mensagem == null || (resposta.has("id") && !mensagem.equals(alias))) {
            throw new RespostaNaoConfirmada();
        }
        return new ResultadoDeEnvio.Enfileirado(propriedades.instanciaId(), processamento, mensagem);
    }

    private JsonNode requisitarTexto(Envio envio, ConteudoDeEnvio.MensagemLivre texto) {
        try {
            return http.post().uri(url("send-text"))
                .header("Client-Token", propriedades.clientToken())
                .contentType(MediaType.APPLICATION_JSON)
                // Streaming evita logBody do RestClient, que em DEBUG imprimiria telefone/texto.
                .body(saida -> json.writeValue(saida, Map.of("phone", envio.telefoneDestino(), "message", texto.texto())))
                .exchange((pedido, retorno) -> lerResposta(retorno));
        } catch (RuntimeException e) {
            // Antes do circuit breaker: seus listeners/logs tambem nao podem receber URI/causa bruta.
            throw new RespostaNaoConfirmada();
        }
    }

    private JsonNode lerResposta(ClientHttpResponse retorno) throws IOException {
        if (!retorno.getStatusCode().is2xxSuccessful()) { throw new RespostaNaoConfirmada(); }
        byte[] corpo = retorno.getBody().readNBytes(propriedades.limiteRespostaBytes() + 1);
        if (corpo.length > propriedades.limiteRespostaBytes()) { throw new RespostaNaoConfirmada(); }
        JsonNode resposta = json.reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readTree(corpo);
        if (resposta == null || !resposta.isObject()) {
            throw new RespostaNaoConfirmada();
        }
        return resposta;
    }

    private static String identificador(JsonNode resposta, String campo) {
        JsonNode valor = resposta.get(campo);
        return valor != null && valor.isTextual() && !valor.asText().isBlank()
                && valor.asText().chars().noneMatch(Character::isISOControl) ? valor.asText() : null;
    }

    @Override
    public AutenticacaoDoCanal verificarAutenticacao() {
        if (!propriedades.configurada()) {
            return AutenticacaoDoCanal.recusada("Z_API_CONFIGURACAO_INDISPONIVEL");
        }
        try {
            JsonNode estado = limite.executeSupplier(() -> saudeBreaker.executeSupplier(this::consultarEstado));
            return estado.path("connected").isBoolean() && estado.path("connected").booleanValue()
                    ? AutenticacaoDoCanal.aceita() : AutenticacaoDoCanal.recusada("Z_API_NAO_CONECTADA");
        } catch (RuntimeException e) {
            return AutenticacaoDoCanal.recusada("Z_API_SAUDE_NAO_CONFIRMADA");
        }
    }

    private JsonNode consultarEstado() {
        try {
            return http.get().uri(url("status")).header("Client-Token", propriedades.clientToken())
                    .exchange((pedido, retorno) -> lerResposta(retorno));
        } catch (RuntimeException e) {
            throw new RespostaNaoConfirmada();
        }
    }

    private String url(String operacao) {
        return URL_BASE + "/instances/" + propriedades.instanciaId() + "/token/" + propriedades.token() + "/" + operacao;
    }

    @Override
    public MidiaRecebida baixarMidiaRecebida(String idExterno) {
        throw new CanalIndisponivelException("Z_API_ENTRADA_NAO_IMPLEMENTADA");
    }

    private static final class RespostaNaoConfirmada extends RuntimeException {
        private static final long serialVersionUID = 1L;
        private RespostaNaoConfirmada() { super("Z_API_RESPOSTA_NAO_CONFIRMADA"); }
    }
}
