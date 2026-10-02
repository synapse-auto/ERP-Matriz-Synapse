package com.synapse.crm.atendimento.infrastructure.canal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Duration;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.synapse.crm.atendimento.domain.canal.CanalGateway;
import com.synapse.crm.atendimento.domain.canal.MidiaRecebidaTemporariamenteIndisponivelException;
import com.synapse.crm.sharedkernel.midia.ArmazenamentoDeMidia;
import com.synapse.crm.sharedkernel.midia.ConversorDeAudio;

/**
 * Respostas 2xx do provedor que nao entregam o arquivo: o resolvedor sem {@code url}, ilegivel, ou
 * o download sem bytes. Nenhuma delas pode virar arquivo no storage, e todas precisam seguir o
 * mesmo ciclo das demais indisponibilidades (retentar ate o prazo e, passado ele, entrar na
 * conversa como anexo indisponivel) em vez de cair na falha generica, que esgota a linha em ~75 s
 * sem que o atendente saiba que o cliente mandou um anexo.
 */
class UzapiAutoticAdapterMidiaRecebidaRespostaInvalidaTest {

    private static final String URL_BASE = "https://uzapi.example.test";
    private static final String RESOLVEDOR = URL_BASE + "/v1/media-inbound";
    private static final String URL_DA_MIDIA = "https://cdn.example.test/arquivos/abc.bin";
    private static final String TOKEN = "token-de-teste";

    private MockRestServiceServer servidor;
    private UzapiAutoticAdapter adapter;

    @BeforeEach
    void configurar() {
        RestClient.Builder builder = RestClient.builder();
        servidor = MockRestServiceServer.bindTo(builder).build();
        adapter = new UzapiAutoticAdapter(
                builder,
                new CanalProperties(
                        UzapiAutoticAdapter.PROVEDOR,
                        URL_BASE,
                        "numero-de-teste",
                        TOKEN,
                        "verify",
                        "secret",
                        Duration.ofHours(24),
                        Duration.ofSeconds(10),
                        "",
                        "usuario",
                        "v1"),
                new ObjectMapper(),
                CircuitBreakerRegistry.ofDefaults(),
                mock(ArmazenamentoDeMidia.class),
                mock(ConversorDeAudio.class));
    }

    @ParameterizedTest(name = "corpo do resolvedor: {0}")
    @ValueSource(strings = {
        "{\"id\":\"media-inbound\"}",
        "{\"id\":\"media-inbound\",\"url\":\"\"}",
        "{\"id\":\"media-inbound\",\"url\":\"   \"}",
        "{\"id\":\"media-inbound\",\"url\":null}",
        "{}",
        "nao-e-json",
        ""
    })
    void resolvedorQueRespondeSemUrlUtilizavelEIndisponibilidadeRetentavelSemVazarNada(String corpo) {
        servidor.expect(once(), requestTo(RESOLVEDOR))
                .andRespond(withSuccess(corpo, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> adapter.baixarMidiaRecebida("media-inbound"))
                .isInstanceOf(MidiaRecebidaTemporariamenteIndisponivelException.class)
                .hasMessageContaining("etapa=resolvedor")
                .hasMessageContaining("midiaId=media-inbound")
                .hasMessageNotContaining(TOKEN)
                .hasMessageNotContaining(URL_BASE);

        servidor.verify();
    }

    @Test
    void downloadSemBytesEIndisponibilidadeRetentavelComEtapaEHostSemCaminho() {
        servidorResolveParaAUrlDaMidia();
        servidor.expect(once(), requestTo(URL_DA_MIDIA))
                .andRespond(withSuccess(new byte[0], MediaType.IMAGE_JPEG));

        assertThatThrownBy(() -> adapter.baixarMidiaRecebida("media-inbound"))
                .isInstanceOf(MidiaRecebidaTemporariamenteIndisponivelException.class)
                .hasMessageContaining("etapa=download")
                .hasMessageContaining("host=cdn.example.test")
                .hasMessageContaining("midiaId=media-inbound")
                .hasMessageNotContaining("/arquivos/")
                .hasMessageNotContaining(TOKEN);

        servidor.verify();
    }

    @Test
    void downloadSemContentTypeDevolveOctetStreamEOsBytesIntactos() {
        // Documenta o comportamento atual: o mimetype gravado e o da CDN; o do webhook nao e usado.
        servidorResolveParaAUrlDaMidia();
        servidor.expect(once(), requestTo(URL_DA_MIDIA))
                .andRespond(withSuccess(new byte[] {1, 2, 3}, null));

        CanalGateway.MidiaRecebida recebida = adapter.baixarMidiaRecebida("media-inbound");

        assertThat(recebida.conteudo()).containsExactly(1, 2, 3);
        assertThat(recebida.mimetype()).isEqualTo("application/octet-stream");
    }

    private void servidorResolveParaAUrlDaMidia() {
        servidor.expect(once(), requestTo(RESOLVEDOR))
                .andRespond(withSuccess(
                        "{\"id\":\"media-inbound\",\"url\":\"" + URL_DA_MIDIA + "\"}",
                        MediaType.APPLICATION_JSON));
    }
}
