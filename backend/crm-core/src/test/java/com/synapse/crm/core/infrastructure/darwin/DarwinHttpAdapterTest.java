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
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.env.MockEnvironment;

import com.synapse.crm.core.application.darwin.FalhaDarwin;
import com.synapse.crm.core.domain.lead.Lead;

/** Contrato sanitizado da coleção médica, sem seus exemplos/segredos de pacientes. */
class DarwinHttpAdapterTest {
    private HttpServer servidor;
    private final List<String> caminhos = new CopyOnWriteArrayList<>();
    private final List<String> metodos = new CopyOnWriteArrayList<>();
    private final List<String> autorizacoes = new CopyOnWriteArrayList<>();
    private volatile int status = 200;
    private volatile String paciente = "{\"cpf\":\"529.982.247-25\",\"name\":\"Pessoa Sintética\",\"phoneNumber\":null,\"motherName\":\"Dado que não deve sair\"}";
    private volatile CompletableFuture<Void> liberar;
    private final CompletableFuture<Void> recebeu = new CompletableFuture<>();
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
    private final Clock relogio = Clock.fixed(Instant.parse("2026-11-03T12:00:00Z"), ZoneOffset.UTC);

    @BeforeEach void abrir() throws Exception {
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/", troca -> {
            caminhos.add(troca.getRequestURI().toString()); metodos.add(troca.getRequestMethod());
            autorizacoes.add(String.valueOf(troca.getRequestHeaders().getFirst("Authorization")));
            recebeu.complete(null);
            try {
                if (liberar != null) liberar.get(3, TimeUnit.SECONDS);
                byte[] bytes = paciente.getBytes(StandardCharsets.UTF_8);
                troca.getResponseHeaders().set("Content-Type", "application/json");
                troca.getResponseHeaders().set("Location", "/destino-que-nao-deve-ser-seguido");
                troca.sendResponseHeaders(status, bytes.length); troca.getResponseBody().write(bytes);
            } catch (Exception erro) { /* Cancelamento/timeout esperado na fixture. */ }
            finally { troca.close(); }
        });
        servidor.start();
    }
    @AfterEach void fechar() { if (liberar != null) liberar.complete(null); servidor.stop(0); }
    private DarwinProperties config(boolean habilitado, URI url, Duration timeout) {
        return new DarwinProperties(habilitado, url, "token-medico-sintetico", 1,
                Duration.ofSeconds(1), timeout, Duration.ofMinutes(5), 65536, 2, 50, Duration.ofSeconds(30));
    }
    private DarwinProperties config(boolean habilitado, Duration timeout) {
        return config(habilitado, URI.create("http://127.0.0.1:"+servidor.getAddress().getPort()), timeout);
    }
    private DarwinHttpAdapter adapter(DarwinProperties config, Clock clock) {
        var ambiente = new MockEnvironment(); ambiente.setActiveProfiles("test");
        return new DarwinHttpAdapter(config, json, clock, ambiente);
    }
    private DarwinHttpAdapter adapter() { return adapter(config(true, Duration.ofSeconds(2)), relogio); }

    @Test void consultaUnicaPorCpfFormatadoComBearerSemConsultarSessoesOuCriarPaciente() throws Exception {
        var resposta = adapter().consultar("52998224725");
        assertThat(resposta.candidatos()).singleElement().satisfies(c -> {
            assertThat(c.sessaoId()).isNull(); assertThat(c.nome()).isEqualTo("Pessoa Sintética");
            assertThat(c.cpf()).isEqualTo("52998224725"); assertThat(c.cpfInvalido()).isFalse();
        });
        assertThat(caminhos).containsExactly("/api/patients/find/cpf?cpf=529.982.247-25");
        assertThat(metodos).containsExactly("GET");
        assertThat(autorizacoes).containsExactly("Bearer token-medico-sintetico");
        assertThat(json.writeValueAsString(resposta)).doesNotContain("motherName", "phoneNumber", "Dado que não deve sair");
    }
    @Test void desligadaNaoChamaHttpNemAceitaRecibo() {
        var adaptador = adapter(config(false, Duration.ofSeconds(2)), relogio);
        assertThat(adaptador.habilitado()).isFalse();
        assertThatThrownBy(() -> adaptador.consultar("52998224725")).hasMessage("DARWIN_DESABILITADA");
        assertThatThrownBy(() -> adaptador.verificar("qualquer")).hasMessage("DARWIN_DESABILITADA");
        assertThat(caminhos).isEmpty();
    }
    @Test void origemLocalDeFixtureNaoEPermitidaEmProducao() {
        var producao = new DarwinHttpAdapter(config(true,Duration.ofSeconds(2)),json,relogio,new MockEnvironment());
        assertThat(producao.habilitado()).isFalse();
        assertThatThrownBy(() -> producao.consultar("52998224725")).hasMessage("DARWIN_CONFIGURACAO");
        assertThat(caminhos).isEmpty();
    }
    @ParameterizedTest @ValueSource(strings={"https://api.getdarwin.ai", "https://developers.darwin.med.br.evil.test", "http://developers.darwin.med.br", "https://developers.darwin.med.br:444", "https://user@developers.darwin.med.br", "https://developers.darwin.med.br/extra"})
    void recusaOutroProdutoEOuOrigemInsegura(String origem) {
        var adaptador=adapter(config(true,URI.create(origem),Duration.ofSeconds(2)),relogio);
        assertThat(adaptador.habilitado()).isFalse();
        assertThatThrownBy(()->adaptador.consultar("52998224725")).hasMessage("DARWIN_CONFIGURACAO");
        assertThat(caminhos).isEmpty();
    }
    @ParameterizedTest @ValueSource(strings={"", "5561999991234", "11111111111", "52998224724", "529.982.247-25x"})
    void telefoneOuDocumentoInvalidoNaoConsulta(String documento) {
        assertThatThrownBy(()->adapter().consultar(documento)).hasMessage("DARWIN_CPF_INVALIDO");
        assertThat(caminhos).isEmpty();
    }
    @Test void corpoExcessivoEInterrompidoSemExporConteudo() {
        paciente = " ".repeat(65537);
        assertThatThrownBy(() -> adapter().consultar("52998224725")).hasMessage("DARWIN_INDISPONIVEL").hasNoCause();
        assertThat(caminhos).hasSize(1);
    }
    @ParameterizedTest @CsvSource({"400,RESPOSTA_INVALIDA", "401,AUTENTICACAO", "403,AUTENTICACAO", "429,LIMITE", "500,INDISPONIVEL", "503,INDISPONIVEL", "302,RESPOSTA_INVALIDA"})
    void recusaHttpSanitizadaSemRetryOuRedirect(int codigo, String motivo) {
        status = codigo;
        assertThatThrownBy(() -> adapter().consultar("52998224725")).hasMessage("DARWIN_" + motivo)
                .hasNoCause().hasMessageNotContaining("52998224725");
        assertThat(caminhos).hasSize(1);
    }
    @Test void circuitoAbreSemRetryAgressivo() {
        status = 500; var adaptador = adapter();
        for (int i = 0; i < 3; i++) assertThatThrownBy(() -> adaptador.consultar("52998224725")).hasMessage("DARWIN_INDISPONIVEL");
        assertThat(caminhos).hasSize(2);
    }
    @Test void ausenciaNoEscopoDoTokenENomeNuloNaoInventamValores() {
        status=404;
        assertThat(adapter().consultar("52998224725").candidatos()).isEmpty();
        status=200;paciente="{\"cpf\":\"52998224725\",\"name\":null}";
        assertThat(adapter().consultar("52998224725").candidatos()).singleElement().satisfies(c -> assertThat(c.nome()).isNull());
    }
    @ParameterizedTest @ValueSource(strings={"11111111111", "39053344705"})
    void cpfDivergenteOuInvalidoNaoOfereceDados(String cpf) {
        paciente="{\"cpf\":\""+cpf+"\",\"name\":\"Outra identidade\"}";
        assertThatThrownBy(()->adapter().consultar("52998224725")).hasMessage("DARWIN_IDENTIDADE_DIVERGENTE");
    }
    @ParameterizedTest @ValueSource(strings={"{malformado", "{}", "[]", "null", "{\"cpf\":52998224725,\"name\":\"Nome\"}", "{\"cpf\":\"52998224725\",\"name\":55}", "{\"cpf\":\"52998224725\",\"name\":null} {}", "{\"cpf\":\"52998224725\",\"cpf\":\"52998224725\",\"name\":null}"})
    void respostaInvalidaNaoViraAusenciaOuIdentidade(String corpo) {
        paciente=corpo;
        assertThatThrownBy(()->adapter().consultar("52998224725")).hasMessage("DARWIN_RESPOSTA_INVALIDA");
    }
    @Test void timeoutEConcorrenciaNaoAcumulamFila() throws Exception {
        liberar = new CompletableFuture<>();
        var adaptador = adapter(config(true,Duration.ofMillis(250)),relogio);
        try (var executor = java.util.concurrent.Executors.newSingleThreadExecutor()) {
            var primeira = executor.submit(() -> adaptador.consultar("52998224725"));
            recebeu.get(2, TimeUnit.SECONDS);
            assertThatThrownBy(() -> adaptador.consultar("52998224725")).hasMessage("DARWIN_OCUPADO");
            assertThatThrownBy(() -> primeira.get(2,TimeUnit.SECONDS)).hasCauseInstanceOf(FalhaDarwin.class);
            assertThat(caminhos).hasSize(1);
        } finally { liberar.complete(null); }
    }
    @Test void comprovanteAssinadoExpiraERecusaAlteracaoETrocaDaOrigem() {
        var adaptador = adapter(); var candidato = adaptador.consultar("52998224725").candidatos().getFirst();
        var lead = mock(Lead.class);
        when(lead.id()).thenReturn(UUID.randomUUID()); when(lead.telefone()).thenReturn("5561999991234"); when(lead.nome()).thenReturn("Nome atual");
        UUID usuario = UUID.randomUUID(); String recibo = adaptador.comprovar(usuario, lead, candidato);
        assertThat(adaptador.verificar(recibo).usuarioId()).isEqualTo(usuario);
        assertThat(adaptador.verificar(recibo).candidato()).isEqualTo(candidato);
        assertThatThrownBy(() -> adaptador.verificar("x" + recibo)).hasMessage("DARWIN_CONSULTA_INVALIDA");
        var depois = adapter(config(true,Duration.ofSeconds(2)), Clock.offset(relogio,Duration.ofMinutes(6)));
        assertThatThrownBy(() -> depois.verificar(recibo)).hasMessage("DARWIN_CONSULTA_INVALIDA");
        var outra=adapter(config(true,URI.create("http://localhost:"+servidor.getAddress().getPort()),Duration.ofSeconds(2)),relogio);
        assertThatThrownBy(()->outra.verificar(recibo)).hasMessage("DARWIN_CONSULTA_INVALIDA");
    }

    @Test void reciboDoProdutoAnteriorNaoValeMesmoComOMesmoSegredo() throws Exception {
        var adaptador = adapter();
        var lead = mock(Lead.class);
        when(lead.id()).thenReturn(UUID.randomUUID());
        var candidato = adaptador.consultar("52998224725").candidatos().getFirst();
        String corpo = adaptador.comprovar(UUID.randomUUID(), lead, candidato).split("\\.")[0];
        var mac = javax.crypto.Mac.getInstance("HmacSHA256");
        mac.init(new javax.crypto.spec.SecretKeySpec("token-medico-sintetico".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String assinaturaAnterior = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(
                mac.doFinal(("synapse:darwin:previa:v1:" + corpo).getBytes(StandardCharsets.UTF_8)));
        assertThatThrownBy(() -> adaptador.verificar(corpo + "." + assinaturaAnterior))
                .hasMessage("DARWIN_CONSULTA_INVALIDA");
    }
}
