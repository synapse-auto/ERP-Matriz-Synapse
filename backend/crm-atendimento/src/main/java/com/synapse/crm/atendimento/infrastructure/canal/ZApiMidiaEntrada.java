package com.synapse.crm.atendimento.infrastructure.canal;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Proxy;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import okhttp3.Dns;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.synapse.crm.atendimento.domain.canal.CanalGateway.MidiaRecebidaEmFluxo;
import com.synapse.crm.atendimento.domain.canal.MidiaRecebidaInvalidaException;
import com.synapse.crm.atendimento.domain.canal.MidiaRecebidaRemovidaNoProvedorException;
import com.synapse.crm.atendimento.domain.canal.MidiaRecebidaTemporariamenteIndisponivelException;
import com.synapse.crm.atendimento.domain.mensagem.TipoMensagem;
import com.synapse.crm.atendimento.domain.midia.TiposDeMidiaPermitidos;
import com.synapse.crm.sharedkernel.midia.CategoriaDeMidia;
import com.synapse.crm.sharedkernel.midia.DetectorDeTipoReal;
import com.synapse.crm.sharedkernel.midia.LimiteDeAnexoRepositorio;
import com.synapse.crm.sharedkernel.midia.ValidadorDeOggOpus;

/** Download do URL documentado no callback, nunca resolvedor mediaId e nunca com credenciais da API. */
@Component
@ConditionalOnProperty(prefix = "synapse.canal.z-api", name = "habilitado", havingValue = "true")
class ZApiMidiaEntrada {
    private final OkHttpClient http;
    private final Set<String> hosts;
    private final LimiteDeAnexoRepositorio limites;
    private final DetectorDeTipoReal detector;
    private final CircuitBreaker breaker;
    private final Bulkhead bulkhead;

    @Autowired ZApiMidiaEntrada(ZApiProperties api, ZApiMidiaEntradaProperties props,
            LimiteDeAnexoRepositorio limites, DetectorDeTipoReal detector, CircuitBreakerRegistry breakers) {
        this(cliente(api.timeout()), props, limites, detector, breakers);
    }
    ZApiMidiaEntrada(OkHttpClient http, ZApiMidiaEntradaProperties props,
            LimiteDeAnexoRepositorio limites, DetectorDeTipoReal detector, CircuitBreakerRegistry breakers) {
        this.http = http;
        this.hosts = props.hostsPermitidos().stream().map(h -> h.toLowerCase(Locale.ROOT)).collect(Collectors.toUnmodifiableSet());
        if (hosts.stream().anyMatch(h -> !h.matches("[a-z0-9](?:[a-z0-9.-]*[a-z0-9])?")
                || !h.contains(".") || h.matches("[0-9.]+") || h.contains("..") || h.endsWith(".localhost")))
            throw new IllegalArgumentException("Z_API_MIDIA_HOST_INVALIDO");
        if (props.concorrencia() <= 0) throw new IllegalArgumentException("Z_API_MIDIA_CONCORRENCIA_INVALIDA");
        this.limites = limites; this.detector = detector;
        this.breaker = breakers.circuitBreaker("canal-z-api-midia-entrada");
        this.bulkhead = Bulkhead.of("canal-z-api-midia-entrada", BulkheadConfig.custom()
                .maxConcurrentCalls(props.concorrencia()).maxWaitDuration(Duration.ZERO).build());
    }
    static OkHttpClient cliente(Duration timeout) {
        return new OkHttpClient.Builder().dns(new ZApiDnsPublico(Dns.SYSTEM)).proxy(Proxy.NO_PROXY)
                .followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false)
                .connectTimeout(timeout).readTimeout(timeout).callTimeout(timeout).build();
    }
    MidiaRecebidaEmFluxo abrir(String referencia, TipoMensagem tipo) {
        URI uri;
        try { uri = URI.create(referencia); }
        catch (IllegalArgumentException | NullPointerException e) { throw new MidiaRecebidaInvalidaException(MidiaRecebidaInvalidaException.Motivo.REFERENCIA); }
        String host = uri.getHost();
        if (!"https".equalsIgnoreCase(uri.getScheme()) || host == null
                || uri.getRawUserInfo() != null || uri.getFragment() != null || (uri.getPort() != -1 && uri.getPort() != 443))
            throw new MidiaRecebidaInvalidaException(MidiaRecebidaInvalidaException.Motivo.REFERENCIA);
        if (!hosts.contains(host.toLowerCase(Locale.ROOT)))
            throw new MidiaRecebidaInvalidaException(MidiaRecebidaInvalidaException.Motivo.HOST_NAO_PERMITIDO);
        long teto = TiposDeMidiaPermitidos.tetoDaMetaEmBytes(tipo);
        long limite = Math.min(teto, limites.limiteEmBytes(CategoriaDeMidia.valueOf(tipo.name())).orElse(teto));
        if (limite <= 0) throw new MidiaRecebidaInvalidaException(MidiaRecebidaInvalidaException.Motivo.TAMANHO);
        try { return bulkhead.executeSupplier(() -> breaker.executeSupplier(() -> baixar(uri, tipo, limite))); }
        catch (MidiaRecebidaInvalidaException | MidiaRecebidaRemovidaNoProvedorException e) { throw e; }
        catch (MidiaRecebidaTemporariamenteIndisponivelException e) { throw e; }
        catch (RuntimeException e) {
            // Nao propagar causa/URL: exceptions de DNS/HTTP podem conter URL assinada.
            throw new MidiaRecebidaTemporariamenteIndisponivelException("provedor=z-api; etapa=download; status=temporario");
        }
    }
    private MidiaRecebidaEmFluxo baixar(URI uri, TipoMensagem tipo, long limite) {
        Path arquivo = null;
        try (Response resposta = http.newCall(new Request.Builder().url(uri.toString()).get().build()).execute()) {
            int status = resposta.code();
            if (status == 410) throw new MidiaRecebidaRemovidaNoProvedorException("provedor=z-api; etapa=download; http_status=410");
            if (status == 408 || status == 429 || status >= 500)
                throw new MidiaRecebidaTemporariamenteIndisponivelException("provedor=z-api; etapa=download; http_status=" + status);
            if (status != 200) throw new MidiaRecebidaInvalidaException(status);
            if (resposta.body() == null || resposta.body().contentLength() > limite)
                throw new MidiaRecebidaInvalidaException(MidiaRecebidaInvalidaException.Motivo.TAMANHO);
            arquivo = Files.createTempFile("z-api-entrada-", ".tmp");
            long tamanho = 0;
            try (InputStream entrada = resposta.body().byteStream(); var saida = Files.newOutputStream(arquivo)) {
                byte[] buffer = new byte[8192]; int n;
                while ((n = entrada.read(buffer, 0, (int) Math.min(buffer.length, limite - tamanho + 1))) != -1) {
                    tamanho += n;
                    if (tamanho > limite) throw new MidiaRecebidaInvalidaException(MidiaRecebidaInvalidaException.Motivo.TAMANHO);
                    saida.write(buffer, 0, n);
                }
            }
            if (tamanho == 0) throw new MidiaRecebidaInvalidaException(MidiaRecebidaInvalidaException.Motivo.CONTEUDO_VAZIO);
            String mime = detector.detectar(arquivo);
            // Audio/video precisam inspecionar trilhas; sao limitados a 16 MiB, nao documentos de 100 MiB.
            boolean container = Set.of("application/ogg", "audio/opus", "audio/x-opus+ogg", "video/mp4", "video/quicktime", "video/3gpp", "audio/mp4").contains(mime);
            boolean inspecionar = container || tipo == TipoMensagem.AUDIO || tipo == TipoMensagem.VIDEO;
            if (inspecionar && tamanho > TiposDeMidiaPermitidos.tetoDaMetaEmBytes(TipoMensagem.AUDIO)) throw new MidiaRecebidaInvalidaException(MidiaRecebidaInvalidaException.Motivo.TAMANHO);
            byte[] bytes = inspecionar ? Files.readAllBytes(arquivo) : new byte[0];
            // tika-core reconhece Opus como container generico. So normalizar apos verificar
            // estrutura completa, OpusHead/mono/48 kHz, EOS e duracao pelo validador existente.
            if (Set.of("application/ogg", "audio/opus", "audio/x-opus+ogg").contains(mime)
                    || (tipo == TipoMensagem.AUDIO && "audio/ogg".equals(mime))) {
                boolean valido = ValidadorDeOggOpus.ehValido(bytes)
                        || (tipo == TipoMensagem.AUDIO && OggOpusRecebido.semEosIntegro(bytes));
                if (!valido) throw new MidiaRecebidaInvalidaException(MidiaRecebidaInvalidaException.Motivo.OGG_INVALIDO);
                mime = "audio/ogg";
            }
            var classificacao = TiposDeMidiaPermitidos.classificar(mime, bytes)
                    .orElseThrow(() -> new MidiaRecebidaInvalidaException(MidiaRecebidaInvalidaException.Motivo.TIPO_INCOMPATIVEL));
            if (tipo != TipoMensagem.DOCUMENTO && classificacao.tipo() != tipo)
                throw new MidiaRecebidaInvalidaException(MidiaRecebidaInvalidaException.Motivo.TIPO_INCOMPATIVEL);
            Path pronto = arquivo;
            InputStream entrada = new FilterInputStream(Files.newInputStream(pronto)) {
                @Override public void close() throws IOException { try { super.close(); } finally { remover(pronto); } }
            };
            arquivo = null;
            return new MidiaRecebidaEmFluxo(entrada, tamanho, classificacao.mimetype());
        } catch (IOException e) {
            throw new MidiaRecebidaTemporariamenteIndisponivelException("provedor=z-api; etapa=download; status=transporte");
        } catch (MidiaRecebidaInvalidaException | MidiaRecebidaRemovidaNoProvedorException | MidiaRecebidaTemporariamenteIndisponivelException e) {
            throw e;
        } catch (RuntimeException e) {
            // Sanitizar ANTES de devolver ao circuit breaker (tambem protege seus logs DEBUG).
            throw new MidiaRecebidaInvalidaException(MidiaRecebidaInvalidaException.Motivo.PROCESSAMENTO);
        } finally { remover(arquivo); }
    }
    private static void remover(Path arquivo) {
        if (arquivo == null) return;
        try { Files.deleteIfExists(arquivo); }
        catch (IOException e) { arquivo.toFile().deleteOnExit(); }
    }
}
