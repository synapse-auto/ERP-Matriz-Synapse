package com.synapse.crm.atendimento.infrastructure.canal;

import java.io.ByteArrayInputStream;
import java.net.Proxy;
import java.net.URI;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;

import javax.imageio.ImageIO;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import okhttp3.Dns;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import org.apache.tika.Tika;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.synapse.crm.atendimento.domain.canal.CanalGateway.MidiaRecebida;
import com.synapse.crm.atendimento.domain.canal.ProvedorTemporariamenteIndisponivelException;

/** ACL de fotos: nenhum segredo da consulta acompanha o GET ao CDN. */
@Component
public class ZApiFotoDePerfil {
    private final ZApiProperties api;
    private final ZApiFotoProperties foto;
    private final ObjectMapper json;
    private final OkHttpClient http;
    private final String base;
    private final CircuitBreaker breaker;
    private final Bulkhead limite;

    @Autowired
    public ZApiFotoDePerfil(ZApiProperties api, ZApiFotoProperties foto, ObjectMapper json,
            CircuitBreakerRegistry breakers) {
        this(api, foto, json, breakers, new OkHttpClient.Builder().dns(new ZApiDnsPublico(Dns.SYSTEM))
                .proxy(Proxy.NO_PROXY).followRedirects(false).followSslRedirects(false)
                .retryOnConnectionFailure(false).connectTimeout(foto.timeout()).readTimeout(foto.timeout())
                .callTimeout(foto.timeout()).build(), "https://api.z-api.io");
    }

    /** Testes substituem somente transporte e destino da API. */
    ZApiFotoDePerfil(ZApiProperties api, ZApiFotoProperties foto, ObjectMapper json,
            CircuitBreakerRegistry breakers, OkHttpClient http, String base) {
        this.api = api; this.foto = foto; this.json = json; this.http = http; this.base = base;
        breaker = breakers.circuitBreaker("canal-z-api-foto");
        limite = Bulkhead.of("canal-z-api-foto", BulkheadConfig.custom()
                .maxConcurrentCalls(foto.concorrencia()).maxWaitDuration(Duration.ZERO).build());
    }

    public Optional<MidiaRecebida> buscar(String telefone) {
        if (!foto.habilitado() || !api.configurada()) return Optional.empty();
        if (telefone == null || !telefone.matches("[1-9][0-9]{7,14}"))
            throw new IllegalArgumentException("Z_API_FOTO_IDENTIDADE_INVALIDA");
        try {
            return limite.executeSupplier(() -> breaker.executeSupplier(() -> consultar(telefone)));
        } catch (RuntimeException falha) {
            // Inclusive DNS/HTTP/breaker: não anexar causa com URL, telefone ou segredo.
            var detalhe = falha instanceof Falha f ? f : new Falha("LIMITE_OU_TRANSPORTE", null);
            org.slf4j.LoggerFactory.getLogger(getClass()).warn("Z_API_FOTO_FALHOU etapa={} http={}", detalhe.etapa, detalhe.status);
            throw new ProvedorTemporariamenteIndisponivelException("Z_API_FOTO_CONSULTA_OU_DOWNLOAD_RECUSADO");
        }
    }

    private Optional<MidiaRecebida> consultar(String telefone) {
        var url = okhttp3.HttpUrl.get(base + "/instances/" + api.instanciaId() + "/token/" + api.token()
                + "/profile-picture").newBuilder().addQueryParameter("phone", telefone).build();
        var pedido = new Request.Builder().url(url).get();
        if (api.enviarClientToken()) pedido.header("Client-Token", api.clientToken());
        String link;
        try (var resposta = http.newCall(pedido.build()).execute()) {
            if (resposta.code() != 200 || resposta.body() == null) throw new Falha("CONSULTA", resposta.code());
            try (var entrada = resposta.body().byteStream()) {
                byte[] bytes = entrada.readNBytes(api.limiteRespostaBytes() + 1);
                if (bytes.length > api.limiteRespostaBytes()) throw new IllegalStateException("CONSULTA_TAMANHO");
                var no = json.reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readTree(bytes);
                if (no == null || !no.isObject() || no.hasNonNull("error") || !no.has("link"))
                    throw new IllegalStateException("CONSULTA_CONTRATO");
                if (no.path("link").isNull() || (no.path("link").isTextual() && no.path("link").asText().isBlank()))
                    return Optional.empty(); // HTTP 200 sem foto fornecida; não inferir a causa (privacidade/ausência).
                if (!no.path("link").isTextual()) throw new IllegalStateException("CONSULTA_CONTRATO");
                link = no.path("link").asText();
            }
        } catch (Falha falha) { throw falha; }
        catch (Exception falha) { throw new Falha("CONSULTA", null); }
        try {
            URI uri = URI.create(link);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                    || uri.getUserInfo() != null || uri.getFragment() != null || (uri.getPort() != -1 && uri.getPort() != 443)
                    || foto.hostsPermitidos().stream().noneMatch(h -> h.equalsIgnoreCase(uri.getHost())))
                throw new IllegalArgumentException("DESTINO_BLOQUEADO");
            // Request novo: sem autenticação, cookie ou interceptor herdado do GET à API.
            try (var resposta = http.newCall(new Request.Builder().url(link).get().build()).execute()) {
                if (resposta.code() != 200 || resposta.body() == null
                        || resposta.body().contentLength() > foto.limiteBytes()) throw new Falha("DOWNLOAD", resposta.code());
                byte[] bytes;
                try (var entrada = resposta.body().byteStream()) { bytes = entrada.readNBytes(foto.limiteBytes() + 1); }
                if (bytes.length == 0 || bytes.length > foto.limiteBytes()) throw new IllegalStateException("DOWNLOAD_TAMANHO");
                String mime = new Tika().detect(bytes).toLowerCase(Locale.ROOT);
                if (!java.util.Set.of("image/jpeg", "image/png", "image/webp").contains(mime))
                    throw new IllegalStateException("DOWNLOAD_MIME");
                try (var imagens = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
                    var leitores = ImageIO.getImageReaders(imagens);
                    if (!leitores.hasNext()) throw new IllegalStateException("DOWNLOAD_IMAGEM");
                    var leitor = leitores.next();
                    try {
                        leitor.setInput(imagens);
                        long largura = leitor.getWidth(0), altura = leitor.getHeight(0);
                        if (largura < 1 || altura < 1 || largura * altura > foto.pixelsMaximos())
                            throw new IllegalStateException("DOWNLOAD_PIXELS");
                    } finally { leitor.dispose(); }
                }
                return Optional.of(new MidiaRecebida(bytes, mime));
            }
        } catch (Falha falha) { throw falha; }
        catch (Exception falha) { throw new Falha("DOWNLOAD_OU_VALIDACAO", null); }
    }
    private static final class Falha extends RuntimeException {
        private final String etapa;
        private final Integer status;
        Falha(String etapa, Integer status) { super("Z_API_FOTO_FALHA_SANITIZADA"); this.etapa=etapa; this.status=status; }
    }
}
