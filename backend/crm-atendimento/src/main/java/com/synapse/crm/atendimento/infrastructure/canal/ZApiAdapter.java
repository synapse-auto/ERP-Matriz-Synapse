package com.synapse.crm.atendimento.infrastructure.canal;

import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.synapse.crm.atendimento.domain.canal.CanalGateway;
import com.synapse.crm.atendimento.domain.canal.ConteudoDeEnvio;
import com.synapse.crm.atendimento.domain.canal.MidiaRecebidaInvalidaException;
import com.synapse.crm.atendimento.domain.canal.ResultadoDeEnvio;
import com.synapse.crm.atendimento.domain.mensagem.TipoMensagem;

/**
 * Saida experimental e desligada. Sem tradutor de entrada nao e selecionavel em uma instancia.
 * Nenhum erro bruto do cliente HTTP atravessa esta fronteira: o token faz parte do path.
 */
@Component
@ConditionalOnProperty(prefix = "synapse.canal.z-api", name = "habilitado", havingValue = "true")
class ZApiAdapter implements CanalGateway {
    private static final Logger log = LoggerFactory.getLogger(ZApiAdapter.class);
    private static final Pattern CODIGO_FORNECEDOR = Pattern.compile("[A-Z][A-Z0-9_]{0,63}");
    private static final String URL_BASE = "https://api.z-api.io";
    private final ZApiProperties propriedades;
    private final ObjectMapper json;
    private final RestClient http;
    private final CircuitBreaker envioBreaker;
    private final CircuitBreaker saudeBreaker;
    private final Bulkhead limite;
    private final String instanciaEsperada;
    private final ZApiMidiaSaida midias;
    private final ZApiMidiaEntrada entrada;

    @Autowired
    ZApiAdapter(ZApiProperties propriedades, CanalProperties canal, ObjectMapper json, CircuitBreakerRegistry breakers,
            ZApiMidiaSaida midias, ZApiMidiaEntrada entrada) {
        this(clienteIsolado(propriedades), propriedades, json, breakers, canal.numeroPrincipal(), midias, entrada);
    }

    /** Builder nao gerenciado; testes substituem somente o transporte, nunca o contrato. */
    ZApiAdapter(RestClient.Builder cliente, ZApiProperties propriedades,
            ObjectMapper json, CircuitBreakerRegistry breakers) {
        this(cliente, propriedades, json, breakers, propriedades.instanciaId());
    }

    ZApiAdapter(RestClient.Builder cliente, ZApiProperties propriedades,
            ObjectMapper json, CircuitBreakerRegistry breakers, String instanciaEsperada) {
        this(cliente, propriedades, json, breakers, instanciaEsperada, null);
    }

    ZApiAdapter(RestClient.Builder cliente, ZApiProperties propriedades,
            ObjectMapper json, CircuitBreakerRegistry breakers, String instanciaEsperada, ZApiMidiaSaida midias) {
        this(cliente, propriedades, json, breakers, instanciaEsperada, midias, null);
    }

    ZApiAdapter(RestClient.Builder cliente, ZApiProperties propriedades,
            ObjectMapper json, CircuitBreakerRegistry breakers, String instanciaEsperada, ZApiMidiaSaida midias, ZApiMidiaEntrada entrada) {
        this.propriedades = propriedades;
        this.instanciaEsperada = instanciaEsperada;
        this.json = json;
        this.midias = midias;
        this.entrada = entrada;
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
        if (!configurada()) {
            return ResultadoDeEnvio.Recusado.permanente("Z_API_CONFIGURACAO_INDISPONIVEL");
        }
        if (!propriedades.credencialId().equals(envio.credencialId())) {
            return ResultadoDeEnvio.Recusado.permanente("Z_API_CREDENCIAL_DIVERGENTE");
        }
        if (!(envio.conteudo() instanceof ConteudoDeEnvio.MensagemLivre)
                && !(envio.conteudo() instanceof ConteudoDeEnvio.MensagemMidia && midias != null)) {
            return ResultadoDeEnvio.Recusado.permanente("Z_API_CONTEUDO_NAO_SUPORTADO");
        }
        if ((envio.identidadeDaCitacao()!=null || (envio.contextoWamid() != null && !envio.contextoWamid().isBlank()))
                && (envio.identidadeDaCitacao()==null || !"z-api".equals(envio.identidadeDaCitacao().provedor())
                || !propriedades.instanciaId().equals(envio.identidadeDaCitacao().instancia())
                || !"WHATSAPP".equals(envio.identidadeDaCitacao().namespace())
                || !Objects.equals(envio.contextoWamid(),envio.identidadeDaCitacao().valor()))) {
            return ResultadoDeEnvio.Recusado.permanente("Z_API_CITACAO_SEM_CORRELACAO");
        }
        if(envio.identidadeDaCitacao()!=null && envio.conteudo() instanceof ConteudoDeEnvio.MensagemMidia midia
                && midia.tipo()==TipoMensagem.AUDIO)
            return ResultadoDeEnvio.Recusado.permanente("CITACAO_NAO_SUPORTADA_PELO_CANAL");
        if (envio.telefoneDestino() == null || !envio.telefoneDestino().matches("[1-9][0-9]{7,14}")) {
            return ResultadoDeEnvio.Recusado.permanente("Z_API_DESTINO_INVALIDO");
        }
        try {
            return limite.executeSupplier(() -> envioBreaker.executeSupplier(() -> enviarConteudo(envio)));
        } catch (CallNotPermittedException e) {
            return ResultadoDeEnvio.Recusado.temporario("Z_API_DISJUNTOR_ABERTO");
        } catch (BulkheadFullException e) {
            return ResultadoDeEnvio.Recusado.temporario("Z_API_LIMITE_DE_CONCORRENCIA");
        } catch (ZApiMidiaSaida.MidiaInvalida e) {
            return ResultadoDeEnvio.Recusado.permanente("Z_API_MIDIA_INVALIDA");
        } catch (ZApiMidiaSaida.StorageIndisponivel e) {
            return ResultadoDeEnvio.Recusado.temporario("Z_API_STORAGE_INDISPONIVEL");
        } catch (RespostaNaoConfirmada e) {
            registrarFalha(e);
            if (e.recusaComprovada()) {
                return ResultadoDeEnvio.Recusado.permanente(e.motivoDaRecusa());
            }
            return new ResultadoDeEnvio.Ambiguo("Z_API_RESPOSTA_NAO_CONFIRMADA");
        } catch (RuntimeException e) {
            // Inclusive timeout/5xx: a ausencia de resposta nao prova ausencia de efeito do POST.
            log.warn("Z_API_FALHA etapa=POST_ENVIO http=indisponivel codigo=ERRO_NAO_CLASSIFICADO");
            return new ResultadoDeEnvio.Ambiguo("Z_API_RESPOSTA_NAO_CONFIRMADA");
        }
    }

    private ResultadoDeEnvio enviarConteudo(Envio envio) {
        JsonNode resposta;
        if (envio.conteudo() instanceof ConteudoDeEnvio.MensagemMidia midia) {
            try (var preparada = midias.preparar(midia)) {
                resposta = requisitarMidia(envio, preparada);
            }
        } else {
            resposta = requisitarTexto(envio, (ConteudoDeEnvio.MensagemLivre) envio.conteudo());
        }
        String processamento = identificador(resposta, "zaapId");
        String mensagem = identificador(resposta, "messageId");
        return new ResultadoDeEnvio.Enfileirado(propriedades.instanciaId(), processamento, mensagem);
    }

    private JsonNode requisitarMidia(Envio envio, ZApiMidiaSaida.Preparada preparada) {
        try {
            var pedido = http.post().uri(url(preparada.rota()));
            if (propriedades.enviarClientToken()) pedido.header("Client-Token", propriedades.clientToken());
            return pedido
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(saida -> preparada.escrever(saida, json, envio.telefoneDestino(), envio.contextoWamid()))
                    .exchange((requisicao, retorno) -> lerResposta(retorno, "POST_MIDIA"));
        } catch (RespostaNaoConfirmada e) {
            throw e;
        } catch (RuntimeException e) {
            throw new RespostaNaoConfirmada("POST_MIDIA", null, "FALHA_DE_TRANSPORTE");
        }
    }

    private JsonNode requisitarTexto(Envio envio, ConteudoDeEnvio.MensagemLivre texto) {
        try {
            var pedido = http.post().uri(url("send-text"));
            if (propriedades.enviarClientToken()) pedido.header("Client-Token", propriedades.clientToken());
            return pedido
                .contentType(MediaType.APPLICATION_JSON)
                // Streaming evita logBody do RestClient, que em DEBUG imprimiria telefone/texto.
                .body(saida -> {
                    var corpo=json.createObjectNode().put("phone",envio.telefoneDestino()).put("message",texto.texto());
                    if(envio.identidadeDaCitacao()!=null) corpo.put("messageId",envio.identidadeDaCitacao().valor());
                    json.writeValue(saida,corpo);
                })
                .exchange((requisicao, retorno) -> lerResposta(retorno, "POST_SEND_TEXT"));
        } catch (RespostaNaoConfirmada e) {
            throw e;
        } catch (RuntimeException e) {
            // Antes do circuit breaker: seus listeners/logs tambem nao podem receber URI/causa bruta.
            throw new RespostaNaoConfirmada("POST_SEND_TEXT", null, "FALHA_DE_TRANSPORTE");
        }
    }

    private JsonNode lerResposta(ClientHttpResponse retorno, String etapa) {
        // Fechar o stream ANTES do auto-close do RestClient impede o drain sem limite
        // do JdkClientHttpResponse. Vale tambem para corpos de erro/redirecionamento.
        Integer status = null;
        try {
            status = retorno.getStatusCode().value();
            try (InputStream entrada = retorno.getBody()) {
                byte[] corpo = entrada.readNBytes(propriedades.limiteRespostaBytes() + 1);
                if (corpo.length > propriedades.limiteRespostaBytes()) {
                    throw new RespostaNaoConfirmada(etapa, status, "RESPOSTA_EXCEDE_LIMITE");
                }
                if (!retorno.getStatusCode().is2xxSuccessful()) {
                    throw new RespostaNaoConfirmada(etapa, status, codigoFornecedor(corpo));
                }
                JsonNode resposta = json.reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readTree(corpo);
                if (resposta == null || !resposta.isObject()) {
                    throw new RespostaNaoConfirmada(etapa, status, "RESPOSTA_2XX_INVALIDA");
                }
                if (!etapa.equals("GET_STATUS")) validarRespostaDeEnvio(resposta, etapa, status);
                return resposta;
            }
        } catch (RespostaNaoConfirmada e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            throw new RespostaNaoConfirmada(etapa, status, "RESPOSTA_NAO_INTERPRETAVEL");
        }
    }

    private String codigoFornecedor(byte[] corpo) {
        try {
            JsonNode erro = json.readTree(corpo);
            for (String campo : new String[] {"code", "errorCode", "error_code"}) {
                JsonNode valor = erro.path(campo);
                if (valor.isTextual()) {
                    String codigo = valor.asText().trim().toUpperCase(Locale.ROOT);
                    if (CODIGO_FORNECEDOR.matcher(codigo).matches()
                            && !contemCredencial(codigo)) {
                        return codigo;
                    }
                }
            }
            String mensagem = erro.path("error").isTextual()
                    ? erro.path("error").asText().toLowerCase(Locale.ROOT) : "";
            if (mensagem.contains("client-token") && mensagem.contains("not configured")) {
                return "CLIENT_TOKEN_NOT_CONFIGURED";
            }
            if (mensagem.equals("null not allowed")) return "CLIENT_TOKEN_REQUIRED";
        } catch (IOException | RuntimeException ignorado) {
            // O corpo e deliberadamente descartado: nunca registrar mensagem bruta do fornecedor.
        }
        return "FORNECEDOR_SEM_CODIGO";
    }

    private boolean contemCredencial(String codigo) {
        return java.util.stream.Stream.of(propriedades.token(), propriedades.clientToken(), propriedades.instanciaId())
                .filter(valor -> valor != null && !valor.isBlank())
                .map(valor -> valor.toUpperCase(Locale.ROOT)).anyMatch(codigo::contains);
    }

    private static void validarRespostaDeEnvio(JsonNode resposta, String etapa, int status) {
        if (resposta.hasNonNull("error") && (!resposta.path("error").isTextual()
                || !resposta.path("error").asText().isBlank())) {
            throw new RespostaNaoConfirmada(etapa, status, "RESPOSTA_2XX_COM_ERRO");
        }
        String mensagem = identificador(resposta, "messageId");
        if (identificador(resposta, "zaapId") == null || mensagem == null
                || (resposta.has("id") && !mensagem.equals(identificador(resposta, "id")))) {
            throw new RespostaNaoConfirmada(etapa, status, "RESPOSTA_2XX_SEM_IDS_VALIDOS");
        }
    }

    private static void registrarFalha(RespostaNaoConfirmada falha) {
        log.warn("Z_API_FALHA etapa={} http={} codigo={}", falha.etapa,
                falha.status == null ? "indisponivel" : falha.status, falha.codigoFornecedor);
    }

    private static String identificador(JsonNode resposta, String campo) {
        JsonNode valor = resposta.get(campo);
        return valor != null && valor.isTextual() && !valor.asText().isBlank()
                && valor.asText().chars().noneMatch(Character::isISOControl) ? valor.asText() : null;
    }

    @Override
    public AutenticacaoDoCanal verificarAutenticacao() {
        if (!configurada()) {
            return AutenticacaoDoCanal.recusada("Z_API_CONFIGURACAO_INDISPONIVEL");
        }
        try {
            JsonNode estado = limite.executeSupplier(() -> saudeBreaker.executeSupplier(this::consultarEstado));
            return estado.path("connected").isBoolean() && estado.path("connected").booleanValue()
                    ? AutenticacaoDoCanal.aceita() : AutenticacaoDoCanal.recusada("Z_API_NAO_CONECTADA");
        } catch (RespostaNaoConfirmada e) {
            registrarFalha(e);
            return AutenticacaoDoCanal.recusada("Z_API_SAUDE_NAO_CONFIRMADA");
        } catch (RuntimeException e) {
            log.warn("Z_API_FALHA etapa=GET_STATUS http=indisponivel codigo=ERRO_NAO_CLASSIFICADO");
            return AutenticacaoDoCanal.recusada("Z_API_SAUDE_NAO_CONFIRMADA");
        }
    }

    private boolean configurada() {
        // O snapshot de origem da outbox usa numeroPrincipal; nao permitir que a
        // credencial HTTP aponte para outra instancia apesar daquele snapshot.
        return propriedades.configurada() && Objects.equals(instanciaEsperada, propriedades.instanciaId());
    }

    private JsonNode consultarEstado() {
        try {
            var pedido = http.get().uri(url("status"));
            if (propriedades.enviarClientToken()) pedido.header("Client-Token", propriedades.clientToken());
            return pedido.exchange((requisicao, retorno) -> lerResposta(retorno, "GET_STATUS"));
        } catch (RespostaNaoConfirmada e) {
            throw e;
        } catch (RuntimeException e) {
            throw new RespostaNaoConfirmada("GET_STATUS", null, "FALHA_DE_TRANSPORTE");
        }
    }

    private String url(String operacao) {
        return URL_BASE + "/instances/" + propriedades.instanciaId() + "/token/" + propriedades.token() + "/" + operacao;
    }

    @Override
    public MidiaRecebida baixarMidiaRecebida(String idExterno) {
        // Compatibilidade da porta antiga; o worker usa o recurso em fluxo para nao materializar documentos.
        try (var midia = recurso(idExterno, TipoMensagem.DOCUMENTO)) {
            return new MidiaRecebida(midia.conteudo().readAllBytes(), midia.mimetype());
        } catch (IOException e) { throw new MidiaRecebidaInvalidaException(); }
    }

    @Override public Optional<MidiaRecebidaEmFluxo> abrirMidiaRecebida(String referencia, TipoMensagem tipo) {
        return Optional.of(recurso(referencia, tipo));
    }

    private MidiaRecebidaEmFluxo recurso(String referencia, TipoMensagem tipo) {
        if (entrada == null || !configurada()) throw new MidiaRecebidaInvalidaException();
        return entrada.abrir(referencia, tipo);
    }

    private static final class RespostaNaoConfirmada extends RuntimeException {
        private static final long serialVersionUID = 1L;
        private final String etapa;
        private final Integer status;
        private final String codigoFornecedor;

        private RespostaNaoConfirmada(String etapa, Integer status, String codigoFornecedor) {
            super("Z_API_RESPOSTA_NAO_CONFIRMADA");
            this.etapa = etapa;
            this.status = status;
            this.codigoFornecedor = codigoFornecedor;
        }

        boolean recusaComprovada() {
            // Nao inferir que um 429/5xx/timeout ou 2xx malformado deixou de produzir efeito.
            return status != null && (status == 401 || status == 403 || status == 405 || status == 415
                    || (status == 400 && (codigoFornecedor.equals("CLIENT_TOKEN_NOT_CONFIGURED")
                    || codigoFornecedor.equals("CLIENT_TOKEN_REQUIRED"))));
        }

        String motivoDaRecusa() {
            return "Z_API_REJEITADO_HTTP_" + status + "_" + codigoFornecedor;
        }
    }
}
