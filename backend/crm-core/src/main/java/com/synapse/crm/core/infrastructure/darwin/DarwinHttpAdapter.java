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
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

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

/** ACL da documentação https://api.getdarwin.ai/. Somente GET; nunca recebe telefone do navegador. */
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
        this.ambienteLocal = ambiente.acceptsProfiles(Profiles.of("dev", "test"));
    }

    private synchronized Recursos recursos() {
        if (recursos != null) return recursos;
        var http = HttpClient.newBuilder().connectTimeout(config.timeoutConexao()).followRedirects(HttpClient.Redirect.NEVER).build();
        var isolamento = Bulkhead.of("darwin-identidade", BulkheadConfig.custom().maxConcurrentCalls(config.concorrencia()).maxWaitDuration(Duration.ZERO).build());
        var circuito = CircuitBreaker.of("darwin-identidade", CircuitBreakerConfig.custom()
                .slidingWindowSize(Math.max(1, config.circuitoMinimo())).minimumNumberOfCalls(Math.max(1, config.circuitoMinimo()))
                .failureRateThreshold(config.circuitoLimiar()).waitDurationInOpenState(config.circuitoEspera()).build());
        recursos = new Recursos(http, isolamento, circuito);
        return recursos;
    }

    @Override public boolean habilitado() { return config.habilitado() && pronta(); }
    private boolean pronta() { return config.pronta() && (ambienteLocal || "api.getdarwin.ai".equals(config.url().getHost())); }
    @Override public void exigirDisponivel() {
        if (!config.habilitado()) throw falha(FalhaDarwin.Codigo.DESABILITADA);
        if (!pronta()) throw falha(FalhaDarwin.Codigo.CONFIGURACAO);
    }

    @Override public DadosDarwin.Resultado consultar(String telefone) {
        exigirDisponivel();
        if (!telefone.matches("[1-9][0-9]{7,14}")) throw falha(FalhaDarwin.Codigo.IDENTIDADE_DIVERGENTE);
        var recurso = recursos();
        if (!recurso.isolamento().tryAcquirePermission()) throw falha(FalhaDarwin.Codigo.OCUPADO);
        try {
            return recurso.circuito().executeSupplier(() -> carregar(telefone));
        } catch (io.github.resilience4j.circuitbreaker.CallNotPermittedException erro) {
            throw falha(FalhaDarwin.Codigo.INDISPONIVEL);
        } catch (FalhaDarwin erro) { throw erro; }
        catch (RuntimeException erro) { throw falha(FalhaDarwin.Codigo.INDISPONIVEL); }
        finally { recurso.isolamento().onComplete(); }
    }

    private DadosDarwin.Resultado carregar(String telefone) {
        long limite = System.nanoTime() + config.timeoutConsulta().toNanos();
        var definicoes = get("/v1/custom-fields", limite);
        if (!definicoes.isArray()) throw falha(FalhaDarwin.Codigo.RESPOSTA_INVALIDA);
        var tipos = new HashMap<String, String>();
        for (var d : definicoes) tipos.put(d.path("id").asText(), d.path("type").asText());
        if (!"string".equals(tipos.get(config.campoNome())) || !"string".equals(tipos.get(config.campoCpf())))
            throw falha(FalhaDarwin.Codigo.CONFIGURACAO);
        var candidatos = new ArrayList<DadosDarwin.Candidato>();
        var vistos = new HashSet<String>();
        boolean limitado = false;
        int inspecionadas = 0;
        for (int pagina = 1; pagina <= config.maxPaginas(); pagina++) {
            var sessoes = get("/v1/sessions?id=" + URLEncoder.encode(telefone, StandardCharsets.UTF_8)
                    + "&channelId=" + config.canalId() + "&page=" + pagina, limite);
            if (!sessoes.isArray() || sessoes.size() > 100) throw falha(FalhaDarwin.Codigo.RESPOSTA_INVALIDA);
            for (var sessao : sessoes) {
                if (inspecionadas >= config.maxSessoes()) { limitado = true; break; }
                var id = sessao.path("sessionId").asText();
                if (!id.matches("[1-9][0-9]*") || !vistos.add(id)) throw falha(FalhaDarwin.Codigo.RESPOSTA_INVALIDA);
                inspecionadas++;
                var candidato = candidato(id, sessao.path("customFields"));
                if (candidato != null) candidatos.add(candidato);
            }
            if (limitado || sessoes.size() < 100) break;
            if (pagina == config.maxPaginas() || inspecionadas >= config.maxSessoes()) { limitado = true; break; }
        }
        // Só a sessão escolhível precisa de detalhe. Ambiguidade não gera N chamadas externas.
        if (!candidatos.isEmpty() && candidatos.stream().map(c -> java.util.Arrays.asList(c.nome(), c.cpf(), c.cpfInvalido())).distinct().count() == 1) {
            var candidato = candidatos.getFirst();
            var detalhe = get("/v1/session/" + candidato.sessaoId(), limite);
            if (!candidato.sessaoId().equals(detalhe.path("id").asText())) throw falha(FalhaDarwin.Codigo.RESPOSTA_INVALIDA);
            // Não converter BSUID/customer.id em telefone nem tentar variantes do nono dígito.
            String telefoneRetornado = detalhe.path("phoneNumber").asText("");
            if (!telefoneRetornado.matches("\\+?[1-9][0-9]{7,14}") || !telefone.equals(telefoneRetornado.replace("+", "")))
                throw falha(FalhaDarwin.Codigo.IDENTIDADE_DIVERGENTE);
            var atual = candidato(candidato.sessaoId(), detalhe.path("customFields"));
            return new DadosDarwin.Resultado(atual == null ? List.of() : List.of(atual), limitado);
        }
        return new DadosDarwin.Resultado(candidatos, limitado);
    }

    private DadosDarwin.Candidato candidato(String id, JsonNode campos) {
        if (!campos.isArray()) throw falha(FalhaDarwin.Codigo.RESPOSTA_INVALIDA);
        String nome = null, cpf = null;
        boolean invalido = false;
        var ids = new HashSet<String>();
        for (var campo : campos) {
            String campoId = campo.path("fieldId").asText();
            if (!campoId.equals(config.campoNome()) && !campoId.equals(config.campoCpf())) continue;
            if (!ids.add(campoId)) throw falha(FalhaDarwin.Codigo.RESPOSTA_INVALIDA);
            var valor = campo.path("value");
            if (valor.isNull() || valor.isMissingNode()) continue;
            if (!valor.isTextual()) throw falha(FalhaDarwin.Codigo.RESPOSTA_INVALIDA);
            if (valor.asText().isBlank()) continue;
            if (campoId.equals(config.campoNome())) {
                try { nome = NomeDoLead.normalizar(valor.asText()); }
                catch (IllegalArgumentException erro) { throw falha(FalhaDarwin.Codigo.RESPOSTA_INVALIDA); }
            } else {
                cpf = Cpf.validoOuNulo(valor.asText().trim());
                invalido = cpf == null;
            }
        }
        return nome == null && cpf == null && !invalido ? null : new DadosDarwin.Candidato(id, nome, cpf, invalido);
    }

    private JsonNode get(String caminho, long limite) {
        var restante = Duration.ofNanos(limite - System.nanoTime());
        if (restante.isNegative() || restante.isZero()) throw falha(FalhaDarwin.Codigo.INDISPONIVEL);
        var pedido = HttpRequest.newBuilder(URI.create(config.url().toString().replaceAll("/$", "") + caminho))
                .timeout(restante).header("X-API-KEY", config.apiKey()).header("Accept", "application/json").GET().build();
        var futuro = recursos().http().sendAsync(pedido, ignorada -> new CorpoHttpLimitado(config.maxRespostaBytes()));
        try {
            var resposta = futuro.get(Math.max(1, restante.toMillis()), TimeUnit.MILLISECONDS);
            int status = resposta.statusCode();
            if (status == 401 || status == 403) throw falha(FalhaDarwin.Codigo.AUTENTICACAO);
            if (status == 429) throw falha(FalhaDarwin.Codigo.LIMITE);
            if (status >= 500) throw falha(FalhaDarwin.Codigo.INDISPONIVEL);
            if (status != 200) throw falha(FalhaDarwin.Codigo.RESPOSTA_INVALIDA);
            var corpo = json.readTree(resposta.body());
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
                    Instant.now(relogio).plus(config.validadePrevia()), config.campoNome(), config.campoCpf(), config.canalId());
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
            if (!Instant.now(relogio).isBefore(recibo.expiraEm()) || !Objects.equals(config.campoNome(), recibo.campoNome())
                    || !Objects.equals(config.campoCpf(), recibo.campoCpf()) || !Objects.equals(config.canalId(), recibo.canalId()))
                throw falha(FalhaDarwin.Codigo.CONSULTA_INVALIDA);
            return recibo;
        } catch (Exception erro) { throw falha(FalhaDarwin.Codigo.CONSULTA_INVALIDA); }
    }

    private byte[] assinar(String corpo) throws java.security.GeneralSecurityException {
        var mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(config.apiKey().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return mac.doFinal(("synapse:darwin:previa:v1:" + corpo).getBytes(StandardCharsets.UTF_8));
    }
    private static FalhaDarwin falha(FalhaDarwin.Codigo codigo) { return new FalhaDarwin(codigo); }
}
