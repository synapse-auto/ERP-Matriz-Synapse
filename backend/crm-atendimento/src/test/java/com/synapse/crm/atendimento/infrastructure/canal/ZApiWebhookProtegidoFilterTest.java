package com.synapse.crm.atendimento.infrastructure.canal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class ZApiWebhookProtegidoFilterTest {
    private static final String SEGREDO = "segredo-sintetico-exclusivo-webhook-com-mais-de-43-caracteres";
    private static final String HEADER_SINTETICO = "X-Header-Somente-Do-Teste";
    private final ZApiProperties api = new ZApiProperties(true, "instancia-local", "token-api", "client-api",
            UUID.randomUUID(), Duration.ofSeconds(1), 4096, 1);
    private final CanalProperties canal = new CanalProperties("z-api", null, "instancia-local", null, null, null, null, null, null, null, null);

    private ZApiWebhookProtegidoFilter filtro(ZApiWebhookProperties.Modo modo) {
        return new ZApiWebhookProtegidoFilter(new ZApiWebhookProperties(true, modo, SEGREDO,
                modo == ZApiWebhookProperties.Modo.HEADER_NATIVO ? HEADER_SINTETICO : "", 128,
                ZApiWebhookProperties.Namespace.NAO_CONFIRMADO, false), api, canal, "token-interno-outro");
    }
    private MockHttpServletRequest pedido() {
        var req = new MockHttpServletRequest("POST", "/webhook/z-api");
        req.setSecure(true); req.setContentType("application/json"); req.setContent("{}".getBytes(StandardCharsets.UTF_8));
        return req;
    }

    @ParameterizedTest @ValueSource(strings = {"", "secret=errado", "secret=", "secret=errado&secret=outro"})
    void tokenInvalidoNaoLeCorpoNemExecutaCadeia(String query) throws Exception {
        var req = new MockHttpServletRequest("POST", "/webhook/z-api") {
            @Override public jakarta.servlet.ServletInputStream getInputStream() { throw new AssertionError("nao deve ler corpo"); }
        };
        req.setSecure(true); req.setQueryString(query);
        var resposta = new MockHttpServletResponse();
        filtro(ZApiWebhookProperties.Modo.SEGREDO_URL).doFilter(req, resposta, (a, b) -> { throw new AssertionError("nao deve processar"); });
        assertThat(resposta.getStatus()).isEqualTo(403);
        assertThat(resposta.getContentAsString()).doesNotContain(SEGREDO);
    }

    @Test void aceitaQueryExclusivaERemoveSegredosAntesDaObservabilidade() throws Exception {
        var req = pedido(); req.setQueryString("secret=" + SEGREDO); req.addHeader("Authorization", SEGREDO);
        var chamadas = new AtomicInteger();
        filtro(ZApiWebhookProperties.Modo.SEGREDO_URL).doFilter(req, new MockHttpServletResponse(), (a, b) -> {
            var seguro = (jakarta.servlet.http.HttpServletRequest) a;
            assertThat(seguro.getQueryString()).isNull(); assertThat(seguro.getParameterMap()).isEmpty();
            assertThat(seguro.getHeader("Authorization")).isNull();
            assertThat(seguro.getAttribute(ZApiWebhookProtegidoFilter.AUTENTICADO)).isEqualTo(true);
            assertThat(seguro.getInputStream().readAllBytes()).isEqualTo("{}".getBytes(StandardCharsets.UTF_8)); chamadas.incrementAndGet();
        });
        assertThat(chamadas).hasValue(1);
    }

    @Test void segredoExclusivoDoWebhookContinuaObrigatorioSemClientTokenDeConta() throws Exception {
        var semClientToken = new ZApiProperties(true, "instancia-local", "token-api", "", false,
                UUID.randomUUID(), Duration.ofSeconds(1), 4096, 1);
        var propriedadesWebhook = new ZApiWebhookProperties(true, ZApiWebhookProperties.Modo.SEGREDO_URL,
                SEGREDO, "", 128, ZApiWebhookProperties.Namespace.NAO_CONFIRMADO, false);
        var filtroSemClientToken = new ZApiWebhookProtegidoFilter(propriedadesWebhook, semClientToken,
                canal, "token-interno-outro");

        var errado = pedido(); errado.setQueryString("secret=incorreto");
        var negada = new MockHttpServletResponse();
        filtroSemClientToken.doFilter(errado, negada, (a, b) -> { throw new AssertionError(); });
        assertThat(negada.getStatus()).isEqualTo(403);

        var correto = pedido(); correto.setQueryString("secret=" + SEGREDO);
        var aceito = new AtomicInteger();
        filtroSemClientToken.doFilter(correto, new MockHttpServletResponse(), (a, b) -> aceito.incrementAndGet());
        assertThat(aceito).hasValue(1);
    }

    @Test void modoHeaderSoAceitaNomeConfiguradoNaoClientTokenNemFallbackQuery() throws Exception {
        var req = pedido(); req.setQueryString("secret=" + SEGREDO); req.addHeader("Client-Token", SEGREDO);
        var negada = new MockHttpServletResponse();
        filtro(ZApiWebhookProperties.Modo.HEADER_NATIVO).doFilter(req, negada, (a, b) -> { throw new AssertionError(); });
        assertThat(negada.getStatus()).isEqualTo(403);
        req = pedido(); req.addHeader(HEADER_SINTETICO, SEGREDO);
        filtro(ZApiWebhookProperties.Modo.HEADER_NATIVO).doFilter(req, new MockHttpServletResponse(), (a, b) ->
                assertThat(((jakarta.servlet.http.HttpServletRequest) a).getHeader(HEADER_SINTETICO)).isNull());
    }

    @Test void recusaHttpMesmoComForwardedForjadoECorpoMaiorQueOLimite() throws Exception {
        var req = pedido(); req.setSecure(false); req.setQueryString("secret=" + SEGREDO); req.addHeader("X-Forwarded-Proto", "https");
        var resposta = new MockHttpServletResponse();
        filtro(ZApiWebhookProperties.Modo.SEGREDO_URL).doFilter(req, resposta, (a, b) -> { throw new AssertionError(); });
        assertThat(resposta.getStatus()).isEqualTo(403);
        req = pedido(); req.setQueryString("secret=" + SEGREDO); req.setContent(new byte[129]); resposta = new MockHttpServletResponse();
        filtro(ZApiWebhookProperties.Modo.SEGREDO_URL).doFilter(req, resposta, (a, b) -> { throw new AssertionError(); });
        assertThat(resposta.getStatus()).isEqualTo(413);
    }

    @Test void impedeReutilizarCredencialDaApi() {
        var props = new ZApiWebhookProperties(true, ZApiWebhookProperties.Modo.SEGREDO_URL, SEGREDO, "", 128,
                ZApiWebhookProperties.Namespace.NAO_CONFIRMADO, false);
        assertThatThrownBy(() -> new ZApiWebhookProtegidoFilter(props, api, canal, SEGREDO))
                .hasMessage("Z_API_WEBHOOK_SEGREDO_REUTILIZADO");
    }
    @Test void corpoSemContentLengthTambemTemTeto() throws Exception {
        var req = new MockHttpServletRequest("POST", "/webhook/z-api") {
            @Override public long getContentLengthLong() { return -1; }
        };
        req.setSecure(true); req.setQueryString("secret=" + SEGREDO); req.setContentType("application/json");req.setContent(new byte[129]);
        var resposta=new MockHttpServletResponse();
        filtro(ZApiWebhookProperties.Modo.SEGREDO_URL).doFilter(req,resposta,(a,b)->{throw new AssertionError("nao pode persistir");});
        assertThat(resposta.getStatus()).isEqualTo(413);
    }
}
