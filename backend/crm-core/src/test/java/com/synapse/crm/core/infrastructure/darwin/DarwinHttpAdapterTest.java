package com.synapse.crm.core.infrastructure.darwin;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.env.MockEnvironment;

import com.synapse.crm.core.application.darwin.FalhaDarwin;
import com.synapse.crm.core.domain.lead.Lead;

class DarwinHttpAdapterTest {
    static final String DEFINICOES = "[{\"id\":11,\"name\":\"campo A\",\"type\":\"string\"},{\"id\":22,\"name\":\"campo B\",\"type\":\"string\"}]";
    static final String CAMPOS = "[{\"fieldId\":\"11\",\"value\":\"Pessoa Sintética\"},{\"fieldId\":\"22\",\"value\":\"529.982.247-25\"}]";
    private HttpServer servidor;
    private final List<String> caminhos = new CopyOnWriteArrayList<>();
    private volatile int status = 200;
    private volatile String sessoes = "[{\"sessionId\":123,\"customFields\":" + CAMPOS + "}]";
    private volatile String detalhe = "{\"id\":123,\"phoneNumber\":\"+5561999991234\",\"customFields\":" + CAMPOS + "}";
    private volatile CompletableFuture<Void> liberar;
    private final CompletableFuture<Void> recebeu = new CompletableFuture<>();
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
    private final Clock relogio = Clock.fixed(Instant.parse("2026-11-02T12:00:00Z"), ZoneOffset.UTC);

    @BeforeEach void abrir() throws Exception {
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/", troca -> {
            caminhos.add(troca.getRequestURI().toString());
            assertThat(troca.getRequestMethod()).isEqualTo("GET");
            assertThat(troca.getRequestHeaders().getFirst("X-API-KEY")).isEqualTo("chave-sintetica-nao-produtiva");
            recebeu.complete(null);
            try {
                if (liberar != null) liberar.get(3, TimeUnit.SECONDS);
                var caminho = troca.getRequestURI().getPath();
                String corpo = status != 200 ? "erro-sintetico-sem-uso" : caminho.equals("/v1/custom-fields") ? DEFINICOES
                        : caminho.equals("/v1/sessions") ? sessoes : detalhe;
                var bytes = corpo.getBytes(StandardCharsets.UTF_8);
                troca.getResponseHeaders().set("Content-Type", "application/json");
                troca.sendResponseHeaders(status, bytes.length); troca.getResponseBody().write(bytes);
            } catch (Exception erro) { /* Cancelamento/timeout do cliente é parte deste fixture. */ }
            finally { troca.close(); }
        });
        servidor.start();
    }
    @AfterEach void fechar() { if (liberar != null) liberar.complete(null); servidor.stop(0); }
    private DarwinProperties config(boolean habilitado, int paginas, int maxSessoes, Duration timeout) {
        return new DarwinProperties(habilitado, URI.create("http://127.0.0.1:" + servidor.getAddress().getPort()),
                "chave-sintetica-nao-produtiva", "11", "22", 7L, paginas, maxSessoes, 1,
                Duration.ofSeconds(1), timeout, Duration.ofMinutes(5), 65536, 2, 50, Duration.ofSeconds(30));
    }
    private DarwinHttpAdapter adapter(DarwinProperties config, Clock clock) {
        var ambiente = new MockEnvironment(); ambiente.setActiveProfiles("test");
        return new DarwinHttpAdapter(config, json, clock, ambiente);
    }
    private DarwinHttpAdapter adapter() { return adapter(config(true, 1, 100, Duration.ofSeconds(2)), relogio); }

    @Test void contratoRealMapeiaIdsConfiguradosSemUsarLabelsEValidaTelefoneDoDetalhe() {
        var resposta = adapter().consultar("5561999991234");
        assertThat(resposta.candidatos()).singleElement().satisfies(c -> {
            assertThat(c.sessaoId()).isEqualTo("123"); assertThat(c.nome()).isEqualTo("Pessoa Sintética");
            assertThat(c.cpf()).isEqualTo("52998224725");
        });
        assertThat(caminhos).containsExactly("/v1/custom-fields", "/v1/sessions?id=5561999991234&channelId=7&page=1", "/v1/session/123");
    }
    @Test void desligadaNaoChamaHttpNemAceitaRecibo() {
        var adaptador = adapter(config(false, 1, 100, Duration.ofSeconds(2)), relogio);
        assertThat(adaptador.habilitado()).isFalse();
        assertThatThrownBy(() -> adaptador.consultar("5561999991234")).hasMessage("DARWIN_DESABILITADA");
        assertThatThrownBy(() -> adaptador.verificar("qualquer")).hasMessage("DARWIN_DESABILITADA");
        assertThat(caminhos).isEmpty();
    }
    @ParameterizedTest @CsvSource({"401,AUTENTICACAO", "403,AUTENTICACAO", "429,LIMITE", "500,INDISPONIVEL", "503,INDISPONIVEL", "302,RESPOSTA_INVALIDA"})
    void recusaHttpSanitizadaSemRetry(int codigo, String motivo) {
        status = codigo;
        assertThatThrownBy(() -> adapter().consultar("5561999991234")).hasMessage("DARWIN_" + motivo)
                .hasNoCause().hasMessageNotContaining("5561999991234");
        assertThat(caminhos).hasSize(1);
    }
    @Test void circuitoAbreSemRetryAgressivo() {
        status = 500; var adaptador = adapter();
        for (int i = 0; i < 3; i++) assertThatThrownBy(() -> adaptador.consultar("5561999991234")).hasMessage("DARWIN_INDISPONIVEL");
        assertThat(caminhos).hasSize(2);
    }
    @Test void ausentesCpfInvalidoENenhumaSessaoNaoSimulamZeroValido() {
        sessoes = "[]";
        assertThat(adapter().consultar("5561999991234").candidatos()).isEmpty();
        sessoes = "[{\"sessionId\":123,\"customFields\":[{\"fieldId\":\"22\",\"value\":\"11111111111\"}]}]";
        detalhe = "{\"id\":123,\"phoneNumber\":\"+5561999991234\",\"customFields\":[{\"fieldId\":\"22\",\"value\":\"11111111111\"}]}";
        assertThat(adapter().consultar("5561999991234").candidatos()).singleElement().satisfies(c -> {
            assertThat(c.cpf()).isNull(); assertThat(c.cpfInvalido()).isTrue();
        });
    }
    @Test void rejeitaIdentidadeDivergenteEBsuidNaoViraTelefone() {
        detalhe = "{\"id\":123,\"phoneNumber\":null,\"bsuid\":\"AR.123\",\"customer\":{\"id\":5561999991234},\"customFields\":[]}";
        assertThatThrownBy(() -> adapter().consultar("5561999991234")).hasMessage("DARWIN_IDENTIDADE_DIVERGENTE");
        detalhe = detalhe.replace("null", "\"+5561999994321\"");
        assertThatThrownBy(() -> adapter().consultar("5561999991234")).hasMessage("DARWIN_IDENTIDADE_DIVERGENTE");
    }
    @Test void respostaMalformadaNaoViraAusenciaDeDados() {
        sessoes = "{malformado";
        assertThatThrownBy(() -> adapter().consultar("5561999991234")).hasMessage("DARWIN_RESPOSTA_INVALIDA");
        sessoes = "{}";
        assertThatThrownBy(() -> adapter().consultar("5561999991234")).hasMessage("DARWIN_RESPOSTA_INVALIDA");
    }
    @Test void paginacaoRespeitaJanelaConfiguradaSemLerHistoricoInteiro() {
        sessoes = java.util.stream.IntStream.rangeClosed(1,100).mapToObj(n -> "{\"sessionId\":"+n+",\"customFields\":[]}")
                .collect(java.util.stream.Collectors.joining(",","[","]"));
        var resultado = adapter().consultar("5561999991234");
        assertThat(resultado.limiteAtingido()).isTrue();
        assertThat(caminhos).hasSize(2).noneMatch(c -> c.contains("page=2"));
        caminhos.clear();
        // O mesmo ID em outra página é violação do contrato, nunca duplicação silenciosa.
        assertThatThrownBy(() -> adapter(config(true, 2, 200, Duration.ofSeconds(2)), relogio).consultar("5561999991234"))
                .hasMessage("DARWIN_RESPOSTA_INVALIDA");
        assertThat(caminhos).hasSize(3).anyMatch(c -> c.contains("page=2"));
    }
    @Test void timeoutEConcorrenciaIsoladosNaoAcumulamFila() throws Exception {
        liberar = new CompletableFuture<>();
        var adaptador = adapter(config(true,1,100,Duration.ofMillis(250)),relogio);
        try (var executor = java.util.concurrent.Executors.newSingleThreadExecutor()) {
            var primeira = executor.submit(() -> adaptador.consultar("5561999991234"));
            recebeu.get(2, TimeUnit.SECONDS);
            assertThatThrownBy(() -> adaptador.consultar("5561999991234")).hasMessage("DARWIN_OCUPADO");
            assertThatThrownBy(() -> primeira.get(2,TimeUnit.SECONDS)).hasCauseInstanceOf(FalhaDarwin.class);
            assertThat(caminhos).hasSize(1);
        } finally { liberar.complete(null); }
    }
    @Test void comprovanteAssinadoExpiraERecusaAlteracao() {
        var adaptador = adapter(); var candidato = adaptador.consultar("5561999991234").candidatos().getFirst();
        var lead = mock(Lead.class);
        when(lead.id()).thenReturn(UUID.randomUUID()); when(lead.telefone()).thenReturn("5561999991234"); when(lead.nome()).thenReturn("Nome atual");
        UUID usuario = UUID.randomUUID(); String recibo = adaptador.comprovar(usuario, lead, candidato);
        assertThat(adaptador.verificar(recibo).usuarioId()).isEqualTo(usuario);
        assertThat(adaptador.verificar(recibo).candidato()).isEqualTo(candidato);
        assertThatThrownBy(() -> adaptador.verificar("x" + recibo)).hasMessage("DARWIN_CONSULTA_INVALIDA");
        var depois = adapter(config(true,1,100,Duration.ofSeconds(2)), Clock.offset(relogio,Duration.ofMinutes(6)));
        assertThatThrownBy(() -> depois.verificar(recibo)).hasMessage("DARWIN_CONSULTA_INVALIDA");
    }
}
