package com.synapse.crm.atendimento.infrastructure.canal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.web.client.RestClient;

import com.synapse.crm.atendimento.application.AtendimentoRepositorio;
import com.synapse.crm.atendimento.application.ConfiguracaoDoComandoResetGeralRepositorio;
import com.synapse.crm.atendimento.application.ConfiguracaoDoComandoResetRepositorio;
import com.synapse.crm.atendimento.application.IdempotenciaDeMensagemRecebidaRepositorio;
import com.synapse.crm.atendimento.application.RegistrarMensagemRecebidaUseCase;
import com.synapse.crm.atendimento.application.TransferirAtendimentoUseCase;
import com.synapse.crm.atendimento.application.WebhookEntrada;
import com.synapse.crm.atendimento.application.canal.CanalCredencialAtivaRepositorio;
import com.synapse.crm.atendimento.application.canal.CanalEntradaAtiva;
import com.synapse.crm.atendimento.application.referencia.MensagemIdExternoRepositorio;
import com.synapse.crm.atendimento.application.referencia.OrigemDeMensagemRepositorio;
import com.synapse.crm.atendimento.domain.atendimento.Atendimento;
import com.synapse.crm.atendimento.domain.atendimento.StatusAtendimento;
import com.synapse.crm.atendimento.domain.canal.TradutorDeCanal;
import com.synapse.crm.atendimento.domain.mensagem.Mensagem;
import com.synapse.crm.atendimento.domain.mensagem.Remetente;
import com.synapse.crm.atendimento.domain.mensagem.TipoMensagem;
import com.synapse.crm.atendimento.infrastructure.webhook.ProcessadorDeWebhookEntradaOperacoes;
import com.synapse.crm.core.application.lead.LeadNoCaminhoDeMensagem;
import com.synapse.crm.core.application.lead.ResetarFichaDoLeadUseCase;
import com.synapse.crm.sharedkernel.midia.ArmazenamentoDeMidia;
import com.synapse.crm.sharedkernel.midia.ConversorDeAudio;

/**
 * Recebimento de midia da Uzapi/Autotic pelo ponto de entrada do runtime
 * ({@code ProcessadorDeWebhookEntradaOperacoes.rodada()}), com o tradutor e o adaptador reais e so
 * o provedor (HTTP), o storage e a persistencia simulados. Payloads sanitizados: telefones, ids,
 * hosts e nomes sao ficticios.
 *
 * <p>Fica neste pacote porque tradutor e adaptador sao pacote-privados. Cada caso exercita o
 * caminho completo: webhook cru -> traducao -> resolvedor -> download -> storage -> registro.
 */
@ExtendWith(OutputCaptureExtension.class)
class RecebimentoDeMidiaUzapiPontaAPontaTest {

    private static final Instant AGORA = Instant.parse("2026-10-01T15:00:00Z");
    private static final Duration PRAZO_MIDIA = Duration.ofMinutes(10);
    private static final String ID_ENTRADA = "entrada-midia-1";
    private static final String URL_BASE = "https://uzapi.example.test";
    private static final String MEDIA_ID = "media-fake-1";
    private static final String URL_DA_MIDIA = "https://cdn.example.test/arquivos/abc.bin";
    private static final String URL_ASSINADA = URL_DA_MIDIA + "?assinatura=segredo-da-url-temporaria";
    private static final String TOKEN = "token-de-teste-uzapi";
    private static final byte[] BYTES = "conteudo-sigiloso-do-arquivo".getBytes(StandardCharsets.UTF_8);

    private final WebhookEntrada entrada = mock(WebhookEntrada.class);
    private final IdempotenciaDeMensagemRecebidaRepositorio idempotencia =
            mock(IdempotenciaDeMensagemRecebidaRepositorio.class);
    private final RegistrarMensagemRecebidaUseCase registrar = mock(RegistrarMensagemRecebidaUseCase.class);
    private final ArmazenamentoDeMidia armazenamento = mock(ArmazenamentoDeMidia.class);
    private final LeadNoCaminhoDeMensagem leads = mock(LeadNoCaminhoDeMensagem.class);
    private final CanalCredencialAtivaRepositorio canaisAtivos = mock(CanalCredencialAtivaRepositorio.class);
    private final ConfiguracaoDoComandoResetRepositorio configuracaoDoReset =
            mock(ConfiguracaoDoComandoResetRepositorio.class);
    private final ConfiguracaoDoComandoResetGeralRepositorio configuracaoDoResetGeral =
            mock(ConfiguracaoDoComandoResetGeralRepositorio.class);

    private MockRestServiceServer provedor;
    private ProcessadorDeWebhookEntradaOperacoes processador;

    @BeforeEach
    void configurar() {
        when(configuracaoDoReset.valor()).thenReturn(Optional.of("#reset"));
        when(configuracaoDoResetGeral.valor()).thenReturn(Optional.of("#resetgeral"));
        when(idempotencia.reservarSeNova(anyString())).thenReturn(true);
        when(leads.resolverPorTelefone(anyString(), any())).thenReturn(UUID.randomUUID());
        when(canaisAtivos.porIdentificadorExterno("phone-id-1"))
                .thenReturn(Optional.of(new CanalEntradaAtiva(UUID.randomUUID(), UUID.randomUUID())));
        when(armazenamento.salvar(any(), any(), any())).thenReturn("midia/ref-gerada");

        CanalProperties propriedades = new CanalProperties(
                UzapiAutoticAdapter.PROVEDOR,
                URL_BASE,
                "5511999999999",
                TOKEN,
                null,
                "segredo-de-webhook",
                Duration.ofHours(24),
                Duration.ofSeconds(10),
                "",
                "usuario",
                "v1");
        ObjectMapper json = new ObjectMapper();
        RestClient.Builder builder = RestClient.builder();
        provedor = MockRestServiceServer.bindTo(builder).build();
        UzapiAutoticAdapter adaptador = new UzapiAutoticAdapter(
                builder,
                propriedades,
                json,
                CircuitBreakerRegistry.ofDefaults(),
                mock(ArmazenamentoDeMidia.class),
                mock(ConversorDeAudio.class));
        processador = new ProcessadorDeWebhookEntradaOperacoes(
                entrada,
                new UzapiAutoticWebhookTradutor(propriedades, json),
                idempotencia,
                registrar,
                mock(com.synapse.crm.atendimento.application.reacao.RegistrarReacaoDoClienteUseCase.class),
                mock(MensagemIdExternoRepositorio.class),
                mock(OrigemDeMensagemRepositorio.class),
                mock(AtendimentoRepositorio.class),
                configuracaoDoReset,
                configuracaoDoResetGeral,
                mock(TransferirAtendimentoUseCase.class),
                mock(ResetarFichaDoLeadUseCase.class),
                leads,
                adaptador,
                armazenamento,
                canaisAtivos,
                json,
                Clock.fixed(AGORA, ZoneOffset.UTC),
                transacaoPassThrough(),
                50,
                5,
                Duration.ofHours(2),
                Duration.ofSeconds(5),
                Duration.ofMinutes(30),
                PRAZO_MIDIA);
    }

    // --- o que funciona: cada tipo/extensao chega ao storage e vira mensagem com arquivo ----------

    static Stream<Arguments> anexosQueOProvedorEntrega() {
        return Stream.of(
                //          tipo       , nome no webhook , mime no webhook   , content-type da CDN , tipo no CRM
                Arguments.of("image", null, "image/jpeg", "image/jpeg", TipoMensagem.IMAGEM),
                Arguments.of("image", "FOTO.JPG", "image/jpeg", "image/jpeg", TipoMensagem.IMAGEM),
                Arguments.of("image", "foto.jpg", "image/jpeg", "image/jpeg", TipoMensagem.IMAGEM),
                Arguments.of("image", "foto.jpeg", "image/jpeg", "image/jpeg", TipoMensagem.IMAGEM),
                Arguments.of("image", "foto.png", "image/png", "image/png", TipoMensagem.IMAGEM),
                Arguments.of("document", "contrato.pdf", "application/pdf", "application/pdf", TipoMensagem.DOCUMENTO),
                // Foto enviada "como documento": o tipo do CRM segue o tipo do webhook, nao a extensao.
                Arguments.of("document", "FOTO.JPG", "image/jpeg", "image/jpeg", TipoMensagem.DOCUMENTO),
                Arguments.of("audio", null, "audio/ogg; codecs=opus", "audio/ogg", TipoMensagem.AUDIO),
                Arguments.of("video", "video.mp4", "video/mp4", "video/mp4", TipoMensagem.VIDEO),
                Arguments.of("sticker", null, "image/webp", "image/webp", TipoMensagem.IMAGEM));
    }

    @ParameterizedTest(name = "{0} nome={1} cdn={3}")
    @MethodSource("anexosQueOProvedorEntrega")
    void anexoEntregueChegaAoStorageEViraMensagemComArquivo(
            String tipoDoWebhook,
            String nome,
            String mimeDoWebhook,
            String contentTypeDaCdn,
            TipoMensagem tipoNoCrm) {
        pendenteComPayload(payloadDeMidia(tipoDoWebhook, MEDIA_ID, mimeDoWebhook, nome));
        provedorEntrega(contentTypeDaCdn, BYTES);
        when(registrar.executar(any())).thenReturn(resultado());

        int processadas = processador.rodada();

        provedor.verify();
        assertThat(processadas).isEqualTo(1);
        verify(armazenamento).salvar(eq(BYTES), eq(nome), eq(contentTypeDaCdn));
        var requisicao = registro();
        assertThat(requisicao.tipo()).isEqualTo(tipoNoCrm);
        assertThat(requisicao.midiaUrl()).isEqualTo("midia/ref-gerada");
        assertThat(requisicao.midiaMetadados())
                .contains("\"mimetype\":\"" + contentTypeDaCdn + "\"")
                .contains("\"tamanho\":" + BYTES.length)
                .doesNotContain("indisponivel");
        verify(entrada).marcarProcessado(ID_ENTRADA, AGORA, List.of());
        verify(entrada, never()).reagendar(anyString(), any(), anyString());
        verify(entrada, never()).esgotar(anyString(), any(), anyString());
    }

    // --- negativos ----------------------------------------------------------------------------------

    @Test
    void midiaSemMediaIdNaoChamaOProvedorNemGravaArquivoNemViraMensagem() {
        pendenteComPayload(payloadDeMidiaSemId("image"));

        processador.rodada();

        provedor.verify(); // nenhuma chamada esperada: qualquer GET quebraria aqui
        verify(armazenamento, never()).salvar(any(), any(), any());
        verify(registrar, never()).executar(any());
        verify(entrada).marcarProcessado(ID_ENTRADA, AGORA, List.of(new TradutorDeCanal.ItemDescartado(
                "image", TradutorDeCanal.MotivoDeDescarte.SEM_IDENTIFICADOR)));
    }

    @Test
    void resolvedorSemUrlNaoGravaArquivoERetentaDentroDoPrazoMesmoNaUltimaTentativaDoTeto() {
        // tentativas=4 de um teto de 5: se a falha caisse no caminho generico, a linha esgotaria
        // aqui e o anexo nunca chegaria ao atendente (nem como aviso de arquivo indisponivel).
        pendenteComPayload(
                payloadDeMidia("image", MEDIA_ID, "image/jpeg", "foto.jpg"), 4, AGORA.minusSeconds(75));
        provedor.expect(once(), requestTo(URL_BASE + "/v1/" + MEDIA_ID))
                .andRespond(withSuccess("{\"id\":\"" + MEDIA_ID + "\"}", MediaType.APPLICATION_JSON));

        processador.rodada();

        provedor.verify();
        verify(armazenamento, never()).salvar(any(), any(), any());
        verify(registrar, never()).executar(any());
        verify(entrada).reagendar(eq(ID_ENTRADA), any(), anyString());
        verify(entrada, never()).esgotar(anyString(), any(), anyString());
    }

    @Test
    void respostaSemBytesNaoGeraArquivoVazioNoStorageERetentaDentroDoPrazoMesmoNaUltimaTentativaDoTeto() {
        pendenteComPayload(
                payloadDeMidia("image", MEDIA_ID, "image/jpeg", "foto.jpg"), 4, AGORA.minusSeconds(75));
        provedorEntrega("image/jpeg", new byte[0]);

        processador.rodada();

        provedor.verify();
        verify(armazenamento, never()).salvar(any(), any(), any());
        verify(registrar, never()).executar(any());
        verify(entrada).reagendar(eq(ID_ENTRADA), any(), anyString());
        verify(entrada, never()).esgotar(anyString(), any(), anyString());
    }

    @Test
    void respostaSemBytesDepoisDoPrazoMantemOAvisoDeArquivoIndisponivel() {
        pendenteComPayload(
                payloadDeMidia("image", MEDIA_ID, "image/jpeg", "foto.jpg"), 9, AGORA.minus(PRAZO_MIDIA));
        provedorEntrega("image/jpeg", new byte[0]);
        when(registrar.executar(any())).thenReturn(resultado());

        processador.rodada();

        verify(armazenamento, never()).salvar(any(), any(), any());
        var requisicao = registro();
        assertThat(requisicao.midiaUrl()).isNull();
        assertThat(requisicao.midiaMetadados()).contains("\"indisponivel\":true");
        verify(entrada).marcarProcessado(ID_ENTRADA, AGORA, List.of());
    }

    @Test
    void erroTemporarioReagendaSemDuplicarEAProximaRodadaEntregaUmaSoMensagem() {
        String payload = payloadDeMidia("image", MEDIA_ID, "image/jpeg", "foto.jpg");
        pendenteComPayload(payload);
        provedorEntrega503NoDownload();

        processador.rodada();

        provedor.verify();
        verify(entrada).reagendar(eq(ID_ENTRADA), eq(AGORA.plusSeconds(5)), anyString());
        verify(registrar, never()).executar(any());
        verify(armazenamento, never()).salvar(any(), any(), any());

        // Segunda rodada: o provedor entregou. O processador a chama como o agendador chama.
        provedor.reset();
        pendenteComPayload(payload, 1, AGORA.minusSeconds(6));
        provedorEntrega("image/jpeg", BYTES);
        when(registrar.executar(any())).thenReturn(resultado());

        processador.rodada();

        provedor.verify();
        verify(registrar, times(1)).executar(any());
        verify(armazenamento, times(1)).salvar(any(), any(), any());
        verify(entrada, times(1)).marcarProcessado(ID_ENTRADA, AGORA, List.of());
    }

    @Test
    void erroDefinitivoMantemOAvisoDeArquivoIndisponivelNaMesmaTentativa(CapturedOutput log) {
        pendenteComPayload(payloadDeMidia("image", MEDIA_ID, "image/jpeg", "FOTO.JPG"));
        provedor.expect(once(), requestTo(URL_BASE + "/v1/" + MEDIA_ID))
                .andRespond(withStatus(HttpStatus.GONE));
        when(registrar.executar(any())).thenReturn(resultado());

        processador.rodada();

        provedor.verify();
        verify(armazenamento, never()).salvar(any(), any(), any());
        var requisicao = registro();
        assertThat(requisicao.midiaUrl()).isNull();
        assertThat(requisicao.midiaMetadados())
                .contains("\"indisponivel\":true")
                .contains("FOTO.JPG")
                .doesNotContain(MEDIA_ID);
        verify(entrada).marcarProcessado(ID_ENTRADA, AGORA, List.of());
        verify(entrada, never()).reagendar(anyString(), any(), anyString());
        assertThat(log.getOut()).contains(ProcessadorDeWebhookEntradaOperacoes.MARCADOR_MIDIA_NAO_RECEBIDA);
    }

    @Test
    void urlTemporariaTokenEBytesNaoAparecemNosLogsNemNoUltimoErro(CapturedOutput log) {
        pendenteComPayload(payloadDeMidia("image", MEDIA_ID, "image/jpeg", "foto.jpg"));
        provedor.expect(once(), requestTo(URL_BASE + "/v1/" + MEDIA_ID))
                .andRespond(withSuccess(
                        "{\"id\":\"" + MEDIA_ID + "\",\"url\":\"" + URL_ASSINADA + "\"}",
                        MediaType.APPLICATION_JSON));
        provedor.expect(once(), requestTo(URL_ASSINADA))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE)
                        .body(BYTES)
                        .contentType(MediaType.APPLICATION_OCTET_STREAM));

        processador.rodada();

        ArgumentCaptor<String> ultimoErro = ArgumentCaptor.forClass(String.class);
        verify(entrada).reagendar(eq(ID_ENTRADA), any(), ultimoErro.capture());
        assertThat(ultimoErro.getValue())
                .contains("etapa=download respondeu HTTP 503")
                .doesNotContain("segredo-da-url-temporaria")
                .doesNotContain(TOKEN)
                .doesNotContain("conteudo-sigiloso");
        assertThat(log.getAll())
                .doesNotContain("segredo-da-url-temporaria")
                .doesNotContain("/arquivos/abc.bin")
                .doesNotContain(TOKEN)
                .doesNotContain("conteudo-sigiloso");
    }

    // --- montagem -----------------------------------------------------------------------------------

    private void provedorEntrega(String contentType, byte[] bytes) {
        provedor.expect(once(), requestTo(URL_BASE + "/v1/" + MEDIA_ID))
                .andRespond(withSuccess(
                        "{\"id\":\"" + MEDIA_ID + "\",\"url\":\"" + URL_DA_MIDIA + "\"}",
                        MediaType.APPLICATION_JSON));
        provedor.expect(once(), requestTo(URL_DA_MIDIA))
                .andRespond(withSuccess(bytes, MediaType.parseMediaType(contentType)));
    }

    private void provedorEntrega503NoDownload() {
        provedor.expect(once(), requestTo(URL_BASE + "/v1/" + MEDIA_ID))
                .andRespond(withSuccess(
                        "{\"id\":\"" + MEDIA_ID + "\",\"url\":\"" + URL_DA_MIDIA + "\"}",
                        MediaType.APPLICATION_JSON));
        provedor.expect(once(), requestTo(URL_DA_MIDIA))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
    }

    private void pendenteComPayload(String payload) {
        pendenteComPayload(payload, 0, AGORA.minusSeconds(1));
    }

    private void pendenteComPayload(String payload, int tentativas, Instant recebidoEm) {
        when(entrada.reservarPendentes(anyInt()))
                .thenReturn(List.of(new WebhookEntrada.Pendente(ID_ENTRADA, payload, tentativas, recebidoEm)));
    }

    private RegistrarMensagemRecebidaUseCase.MensagemRecebida registro() {
        ArgumentCaptor<RegistrarMensagemRecebidaUseCase.MensagemRecebida> captor =
                ArgumentCaptor.forClass(RegistrarMensagemRecebidaUseCase.MensagemRecebida.class);
        verify(registrar).executar(captor.capture());
        return captor.getValue();
    }

    private static String payloadDeMidia(String tipo, String mediaId, String mime, String nome) {
        String arquivo = nome == null ? "" : ",\"filename\":\"" + nome + "\"";
        return envelope("{\"from\":\"5561988887777\",\"id\":\"wamid.fake-1\",\"timestamp\":\"1790866800\","
                + "\"type\":\"" + tipo + "\",\"" + tipo + "\":{\"id\":\"" + mediaId + "\","
                + "\"mime_type\":\"" + mime + "\"" + arquivo + "}}");
    }

    private static String payloadDeMidiaSemId(String tipo) {
        return envelope("{\"from\":\"5561988887777\",\"id\":\"wamid.fake-2\",\"timestamp\":\"1790866800\","
                + "\"type\":\"" + tipo + "\",\"" + tipo + "\":{\"mime_type\":\"image/jpeg\"}}");
    }

    private static String envelope(String mensagem) {
        return "{\"entry\":[{\"changes\":[{\"value\":{"
                + "\"metadata\":{\"phone_number_id\":\"phone-id-1\"},"
                + "\"contacts\":[{\"wa_id\":\"5561988887777\",\"profile\":{\"name\":\"Cliente Fake\"}}],"
                + "\"messages\":[" + mensagem + "]}}]}]}";
    }

    private static RegistrarMensagemRecebidaUseCase.Resultado resultado() {
        UUID atendimentoId = UUID.randomUUID();
        Atendimento atendimento = new Atendimento(
                atendimentoId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null,
                StatusAtendimento.EM_IA, AGORA, null);
        Mensagem mensagem = Mensagem.midia(
                UUID.randomUUID(), atendimentoId, Remetente.lead(), TipoMensagem.IMAGEM,
                null, "{}", AGORA);
        return new RegistrarMensagemRecebidaUseCase.Resultado(atendimento, mensagem, false);
    }

    private static PlatformTransactionManager transacaoPassThrough() {
        return new PlatformTransactionManager() {
            @Override
            public org.springframework.transaction.TransactionStatus getTransaction(
                    TransactionDefinition definition) {
                return new SimpleTransactionStatus();
            }

            @Override
            public void commit(org.springframework.transaction.TransactionStatus status) {}

            @Override
            public void rollback(org.springframework.transaction.TransactionStatus status) {}
        };
    }
}
