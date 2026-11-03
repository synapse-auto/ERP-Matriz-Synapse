package com.synapse.crm.core.infrastructure.darwin;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

import com.synapse.crm.core.application.darwin.DadosDarwin;
import com.synapse.crm.core.application.darwin.DarwinGateway;
import com.synapse.crm.core.application.darwin.FalhaDarwin;
import com.synapse.crm.core.domain.lead.Cpf;
import com.synapse.crm.core.domain.lead.Lead;
import com.synapse.crm.core.domain.lead.NomeDoLead;

/** ACL da coleção Darwin médico: só consulta por CPF conhecido, nunca criação de paciente. */
@Component
class DarwinHttpAdapter implements DarwinGateway {
    private static final Base64.Encoder BASE64 = Base64.getUrlEncoder().withoutPadding();
    private final DarwinProperties config;
    private final ObjectMapper json;
    private final Clock relogio;
    private final boolean ambienteLocal;
    private volatile Recursos recursos;
    private record Recursos(HttpClient http, Bulkhead isolamento, CircuitBreaker circuito) {}

    DarwinHttpAdapter(DarwinProperties config, ObjectMapper json, Clock relogio, Environment ambiente) {
        this.config = config; this.json = json; this.relogio = relogio;
        ambienteLocal = ambiente.acceptsProfiles(Profiles.of("dev", "test"));
    }

    private synchronized Recursos recursos() {
        if (recursos != null) return recursos;
        var http = HttpClient.newBuilder().connectTimeout(config.timeoutConexao()).followRedirects(HttpClient.Redirect.NEVER).build();
        var isolamento = Bulkhead.of("darwin-identidade", BulkheadConfig.custom().maxConcurrentCalls(config.concorrencia()).maxWaitDuration(Duration.ZERO).build());
        var circuito = CircuitBreaker.of("darwin-identidade", CircuitBreakerConfig.custom()
                .slidingWindowSize(config.circuitoMinimo()).minimumNumberOfCalls(config.circuitoMinimo())
                .failureRateThreshold(config.circuitoLimiar()).waitDurationInOpenState(config.circuitoEspera()).build());
        return recursos = new Recursos(http, isolamento, circuito);
    }

    @Override public boolean habilitado() { return config.habilitado() && pronta(); }
    private boolean pronta() { return config.pronta() && (ambienteLocal || "developers.darwin.med.br".equals(config.url().getHost())); }
    @Override public void exigirDisponivel() {
        if (!config.habilitado()) throw falha(FalhaDarwin.Codigo.DESABILITADA);
        if (!pronta()) throw falha(FalhaDarwin.Codigo.CONFIGURACAO);
    }

    @Override public DadosDarwin.Resultado consultar(String documento) {
        exigirDisponivel();
        String cpf = Cpf.validoOuNulo(documento);
        if (cpf == null) throw falha(FalhaDarwin.Codigo.CPF_INVALIDO);
        var recurso = recursos();
        if (!recurso.isolamento().tryAcquirePermission()) throw falha(FalhaDarwin.Codigo.OCUPADO);
        try { return recurso.circuito().executeSupplier(() -> carregar(cpf)); }
        catch (io.github.resilience4j.circuitbreaker.CallNotPermittedException erro) { throw falha(FalhaDarwin.Codigo.INDISPONIVEL); }
        catch (FalhaDarwin erro) { throw erro; }
        catch (RuntimeException erro) { throw falha(FalhaDarwin.Codigo.INDISPONIVEL); }
        finally { recurso.isolamento().onComplete(); }
    }

    private DadosDarwin.Resultado carregar(String cpf) {
        String formatado = cpf.substring(0,3)+"."+cpf.substring(3,6)+"."+cpf.substring(6,9)+"-"+cpf.substring(9);
        JsonNode paciente = get("/api/patients/find/cpf?cpf=" + URLEncoder.encode(formatado, StandardCharsets.UTF_8));
        if (paciente == null) return new DadosDarwin.Resultado(List.of(), false); // 404: sem paciente no escopo do token.
        if (!paciente.isObject() || !paciente.path("cpf").isTextual()) throw falha(FalhaDarwin.Codigo.RESPOSTA_INVALIDA);
        String retornado = Cpf.validoOuNulo(paciente.path("cpf").asText());
        if (!cpf.equals(retornado)) throw falha(FalhaDarwin.Codigo.IDENTIDADE_DIVERGENTE);
        var campoNome = paciente.get("name");
        if (campoNome == null || (!campoNome.isNull() && !campoNome.isTextual())) throw falha(FalhaDarwin.Codigo.RESPOSTA_INVALIDA);
        String nome = null;
        if (campoNome.isTextual() && !campoNome.asText().isBlank()) {
            try { nome = NomeDoLead.normalizar(campoNome.asText()); }
            catch (IllegalArgumentException erro) { throw falha(FalhaDarwin.Codigo.RESPOSTA_INVALIDA); }
        }
        // Não há ID de sessão/paciente neste contrato. Nunca inferir IDs nem usar campos clínicos.
        return new DadosDarwin.Resultado(List.of(new DadosDarwin.Candidato(null, nome, retornado, false)), false);
    }

    private JsonNode get(String caminho) {
        var pedido = HttpRequest.newBuilder(URI.create(config.url().toString().replaceAll("/$", "") + caminho))
                .timeout(config.timeoutConsulta()).header("Authorization", "Bearer " + config.token())
                .header("Accept", "application/json").GET().build();
        var futuro = recursos().http().sendAsync(pedido, ignorada -> new CorpoHttpLimitado(config.maxRespostaBytes()));
        try {
            var resposta = futuro.get(Math.max(1, config.timeoutConsulta().toMillis()), TimeUnit.MILLISECONDS);
            int status = resposta.statusCode();
            if (status == 404) return null;
            if (status == 401 || status == 403) throw falha(FalhaDarwin.Codigo.AUTENTICACAO);
            if (status == 429) throw falha(FalhaDarwin.Codigo.LIMITE);
            if (status >= 500) throw falha(FalhaDarwin.Codigo.INDISPONIVEL);
            if (status != 200) throw falha(FalhaDarwin.Codigo.RESPOSTA_INVALIDA);
            var corpo = json.reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                    .with(JsonParser.Feature.STRICT_DUPLICATE_DETECTION).readTree(resposta.body());
            if (corpo == null) throw falha(FalhaDarwin.Codigo.RESPOSTA_INVALIDA);
            return corpo;
        } catch (FalhaDarwin erro) { throw erro; }
        catch (InterruptedException erro) { Thread.currentThread().interrupt(); throw falha(FalhaDarwin.Codigo.INDISPONIVEL); }
        catch (com.fasterxml.jackson.core.JsonProcessingException erro) { throw falha(FalhaDarwin.Codigo.RESPOSTA_INVALIDA); }
        catch (Exception erro) { throw falha(FalhaDarwin.Codigo.INDISPONIVEL); }
        finally { futuro.cancel(true); }
    }

    @Override public String comprovar(UUID usuarioId, Lead lead, DadosDarwin.Candidato candidato) {
        exigirDisponivel();
        try {
            var recibo = new DadosDarwin.Recibo(usuarioId, lead.id(), lead.telefone(), lead.nome(), lead.cpf(), candidato,
                    Instant.now(relogio).plus(config.validadePrevia()), config.url().toString());
            String corpo = BASE64.encodeToString(json.writeValueAsBytes(recibo));
            return corpo + "." + BASE64.encodeToString(assinar(corpo));
        } catch (Exception erro) { throw falha(FalhaDarwin.Codigo.INDISPONIVEL); }
    }

    @Override public DadosDarwin.Recibo verificar(String confirmacao) {
        exigirDisponivel();
        try {
            if (confirmacao == null || confirmacao.length() > 8192) throw falha(FalhaDarwin.Codigo.CONSULTA_INVALIDA);
            String[] partes = confirmacao.split("\\.", -1);
            if (partes.length != 2 || !MessageDigest.isEqual(assinar(partes[0]), Base64.getUrlDecoder().decode(partes[1])))
                throw falha(FalhaDarwin.Codigo.CONSULTA_INVALIDA);
            var recibo = json.readValue(Base64.getUrlDecoder().decode(partes[0]), DadosDarwin.Recibo.class);
            if (!Instant.now(relogio).isBefore(recibo.expiraEm()) || !Objects.equals(config.url().toString(), recibo.origem()))
                throw falha(FalhaDarwin.Codigo.CONSULTA_INVALIDA);
            return recibo;
        } catch (Exception erro) { throw falha(FalhaDarwin.Codigo.CONSULTA_INVALIDA); }
    }

    private byte[] assinar(String corpo) throws java.security.GeneralSecurityException {
        var mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(config.token().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        // Domínio novo invalida comprovantes do provedor incorreto, mesmo se reutilizarem um segredo.
        return mac.doFinal(("synapse:darwin-medico:previa:v2:" + corpo).getBytes(StandardCharsets.UTF_8));
    }
    private static FalhaDarwin falha(FalhaDarwin.Codigo codigo) { return new FalhaDarwin(codigo); }
}
