package com.synapse.crm.atendimento.infrastructure.webhook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;

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
import com.synapse.crm.atendimento.domain.canal.CanalGateway;
import com.synapse.crm.atendimento.domain.canal.MidiaRecebidaTemporariamenteIndisponivelException;
import com.synapse.crm.atendimento.domain.canal.ProvedorTemporariamenteIndisponivelException;
import com.synapse.crm.atendimento.domain.canal.TradutorDeCanal;
import com.synapse.crm.atendimento.domain.mensagem.Mensagem;
import com.synapse.crm.atendimento.domain.mensagem.Remetente;
import com.synapse.crm.atendimento.domain.mensagem.TipoMensagem;
import com.synapse.crm.core.application.lead.LeadNoCaminhoDeMensagem;
import com.synapse.crm.core.application.lead.ResetarFichaDoLeadUseCase;
import com.synapse.crm.sharedkernel.midia.ArmazenamentoDeMidia;

class ProcessadorDeWebhookEntradaOperacoesTest {

    private static final Instant AGORA = Instant.parse("2026-09-02T15:00:00Z");
    private static final String ID_EXTERNO = "wamid.midia-1";
    private static final Duration PRAZO_MIDIA = Duration.ofMinutes(10);

    private final WebhookEntrada entrada = mock(WebhookEntrada.class);
    private final TradutorDeCanal tradutor = mock(TradutorDeCanal.class);
    private final IdempotenciaDeMensagemRecebidaRepositorio idempotencia =
            mock(IdempotenciaDeMensagemRecebidaRepositorio.class);
    private final CanalGateway canal = mock(CanalGateway.class);
    private final CanalCredencialAtivaRepositorio canaisAtivos =
            mock(CanalCredencialAtivaRepositorio.class);
    private final LeadNoCaminhoDeMensagem leads = mock(LeadNoCaminhoDeMensagem.class);
    private final RegistrarMensagemRecebidaUseCase registrar = mock(RegistrarMensagemRecebidaUseCase.class);
    private final ConfiguracaoDoComandoResetRepositorio configuracaoDoReset =
            mock(ConfiguracaoDoComandoResetRepositorio.class);
    private final ConfiguracaoDoComandoResetGeralRepositorio configuracaoDoResetGeral =
            mock(ConfiguracaoDoComandoResetGeralRepositorio.class);

    @BeforeEach
    void stubsComuns() {
        when(configuracaoDoReset.valor()).thenReturn(Optional.of("#reset"));
        when(configuracaoDoResetGeral.valor()).thenReturn(Optional.of("#resetgeral"));
        when(idempotencia.reservarSeNova(anyString())).thenReturn(true);
        when(leads.resolverPorTelefone(anyString(), any())).thenReturn(UUID.randomUUID());
        when(canaisAtivos.porIdentificadorExterno(anyString()))
                .thenReturn(Optional.of(new CanalEntradaAtiva(UUID.randomUUID(), UUID.randomUUID())));
        when(tradutor.traduzirComDescartes(anyString()))
                .thenReturn(TradutorDeCanal.Traducao.semDescartes(List.of(mensagemDeMidia())));
        when(tradutor.provedor()).thenReturn("meta-cloud");
    }

    @ParameterizedTest
    @MethodSource("comandosReset")
    void reconhece_reset_sem_diferenciar_caixa_ou_espacos(String texto) {
        assertThat(ProcessadorDeWebhookEntradaOperacoes.ehComandoReset(texto, "#reset")).isTrue();
    }

    @ParameterizedTest
    @MethodSource("naoComandosReset")
    void nao_confunde_texto_parecido_com_reset(String texto) {
        assertThat(ProcessadorDeWebhookEntradaOperacoes.ehComandoReset(texto, "#reset")).isFalse();
    }

    @ParameterizedTest
    @MethodSource("comandosConfigurados")
    void compara_com_o_literal_configurado_sem_aceitar_texto_parecido(String texto, boolean esperado) {
        assertThat(ProcessadorDeWebhookEntradaOperacoes.ehComandoReset(texto, "#voltar"))
                .isEqualTo(esperado);
    }

    @Test
    void disjuntorAbertoNaoIncrementaTentativaEContinuaElegivel() {
        when(entrada.reservarPendentes(anyInt())).thenReturn(List.of(pendente(0, AGORA.minusSeconds(30))));
        when(canal.baixarMidiaRecebida(anyString()))
                .thenThrow(new ProvedorTemporariamenteIndisponivelException("circuit breaker aberto"));

        processador(Duration.ofHours(2)).rodada();

        verify(entrada).adiar(eq(ID_EXTERNO), eq(AGORA.plusSeconds(5)), anyString());
        verify(entrada, never()).reagendar(anyString(), any(), anyString());
        verify(entrada, never()).esgotar(anyString(), any(), anyString());
    }

    @Test
    void disjuntorAbertoAposPrazoAbsolutoEsgotaALinha() {
        Instant recebidoEm = AGORA.minus(Duration.ofHours(2));
        when(entrada.reservarPendentes(anyInt())).thenReturn(List.of(pendente(0, recebidoEm)));
        when(canal.baixarMidiaRecebida(anyString()))
                .thenThrow(new ProvedorTemporariamenteIndisponivelException("circuit breaker aberto"));

        processador(Duration.ofHours(2)).rodada();

        verify(entrada).esgotar(eq(ID_EXTERNO), eq(AGORA), anyString());
        verify(entrada, never()).adiar(anyString(), any(), anyString());
        verify(entrada, never()).reagendar(anyString(), any(), anyString());
    }

    @Test
    void falhaRealDaMetaContinuaConsumindoTentativa() {
        when(entrada.reservarPendentes(anyInt())).thenReturn(List.of(pendente(0, AGORA.minusSeconds(10))));
        when(canal.baixarMidiaRecebida(anyString()))
                .thenThrow(new IllegalStateException("502 Bad Gateway do provedor"));

        processador(Duration.ofHours(2)).rodada();

        verify(entrada).reagendar(eq(ID_EXTERNO), eq(AGORA.plusSeconds(5)), anyString());
        verify(entrada, never()).adiar(anyString(), any(), anyString());
        verify(entrada, never()).esgotar(anyString(), any(), anyString());
    }

    @Test
    void falhaRealNaUltimaTentativaEsgota() {
        when(entrada.reservarPendentes(anyInt())).thenReturn(List.of(pendente(4, AGORA.minusSeconds(10))));
        when(canal.baixarMidiaRecebida(anyString()))
                .thenThrow(new IllegalStateException("400 da Meta"));

        processador(Duration.ofHours(2)).rodada();

        verify(entrada).esgotar(eq(ID_EXTERNO), eq(AGORA), anyString());
        verify(entrada, never()).adiar(anyString(), any(), anyString());
        verify(entrada, never()).reagendar(anyString(), any(), anyString());
    }

    @Test
    void midiaSemIdNaoChamaDownloadENaoBloqueiaOsDemaisItensDoPost() {
        when(entrada.reservarPendentes(anyInt())).thenReturn(List.of(pendente(0, AGORA)));
        when(tradutor.traduzirComDescartes(anyString()))
                .thenReturn(TradutorDeCanal.Traducao.semDescartes(List.of(mensagemDeMidiaSemId())));

        processador(Duration.ofHours(2)).rodada();

        verify(canal, never()).baixarMidiaRecebida(anyString());
        // A midia sem referencia continua descartada, mas agora a linha diz isso.
        verify(entrada).marcarProcessado(ID_EXTERNO, AGORA, List.of(
                new TradutorDeCanal.ItemDescartado(
                        "image", TradutorDeCanal.MotivoDeDescarte.SEM_IDENTIFICADOR)));
        verify(entrada, never()).reagendar(anyString(), any(), anyString());
        verify(entrada, never()).esgotar(anyString(), any(), anyString());
    }

    @Test
    void midiaIndisponivelAntesDoPrazoRetentaMesmoAlemDoTetoDeTentativas() {
        // Com o teto de 5 tentativas a linha desistia em ~77s (E207). Antes do prazo de midia a
        // falha do provedor so reagenda, qualquer que seja o numero de tentativas ja feitas.
        when(entrada.reservarPendentes(anyInt()))
                .thenReturn(List.of(pendente(7, AGORA.minus(PRAZO_MIDIA).plusSeconds(1))));
        when(canal.baixarMidiaRecebida(anyString()))
                .thenThrow(new MidiaRecebidaTemporariamenteIndisponivelException(
                        "resolvedor de midia uzapi-autotic respondeu HTTP 410; midiaId=1"));

        processador(Duration.ofHours(2)).rodada();

        verify(entrada).reagendar(eq(ID_EXTERNO), any(), anyString());
        verify(entrada, never()).esgotar(anyString(), any(), anyString());
        verify(registrar, never()).executar(any());
    }

    @Test
    void midiaIndisponivelAposOPrazoEntraNaConversaSemArquivoEmVezDeSumir() {
        when(entrada.reservarPendentes(anyInt()))
                .thenReturn(List.of(pendente(9, AGORA.minus(PRAZO_MIDIA))));
        when(canal.baixarMidiaRecebida(anyString()))
                .thenThrow(new MidiaRecebidaTemporariamenteIndisponivelException(
                        "resolvedor de midia uzapi-autotic respondeu HTTP 410; midiaId=1"));
        when(registrar.executar(any())).thenReturn(resultadoDeMidiaSemArquivo());

        processador(Duration.ofHours(2)).rodada();

        ArgumentCaptor<RegistrarMensagemRecebidaUseCase.MensagemRecebida> requisicao =
                ArgumentCaptor.forClass(RegistrarMensagemRecebidaUseCase.MensagemRecebida.class);
        verify(registrar).executar(requisicao.capture());
        assertThat(requisicao.getValue().tipo()).isEqualTo(TipoMensagem.IMAGEM);
        assertThat(requisicao.getValue().midiaUrl()).isNull();
        assertThat(requisicao.getValue().midiaMetadados())
                .contains("\"indisponivel\":true")
                .contains("foto.jpg")
                .doesNotContain("media-id-meta");
        // Mensagem sem arquivo nao e descarte: o anexo entrou na conversa.
        verify(entrada).marcarProcessado(ID_EXTERNO, AGORA, List.of());
        verify(entrada, never()).esgotar(anyString(), any(), anyString());
        verify(entrada, never()).reagendar(anyString(), any(), anyString());
    }

    @Test
    void descarteDoTradutorEGravadoNaLinhaMesmoSemNenhumaMensagem() {
        var descarte = new TradutorDeCanal.ItemDescartado(
                "reaction", TradutorDeCanal.MotivoDeDescarte.TIPO_NAO_SUPORTADO);
        when(entrada.reservarPendentes(anyInt())).thenReturn(List.of(pendente(0, AGORA)));
        when(tradutor.traduzirComDescartes(anyString()))
                .thenReturn(new TradutorDeCanal.Traducao(List.of(), List.of(descarte)));

        processador(Duration.ofHours(2)).rodada();

        verify(entrada).marcarProcessado(ID_EXTERNO, AGORA, List.of(descarte));
        verify(entrada, never()).reagendar(anyString(), any(), anyString());
    }

    @Test
    void falhaQueNaoEDeMidiaIndisponivelContinuaEsgotandoNoTeto() {
        // Negativo: o prazo de midia nao vira passe livre para qualquer erro do download.
        when(entrada.reservarPendentes(anyInt()))
                .thenReturn(List.of(pendente(4, AGORA.minus(PRAZO_MIDIA))));
        when(canal.baixarMidiaRecebida(anyString()))
                .thenThrow(new IllegalStateException("erro do storage"));

        processador(Duration.ofHours(2)).rodada();

        verify(entrada).esgotar(eq(ID_EXTERNO), eq(AGORA), anyString());
        verify(registrar, never()).executar(any());
    }

    private static RegistrarMensagemRecebidaUseCase.Resultado resultadoDeMidiaSemArquivo() {
        UUID atendimentoId = UUID.randomUUID();
        Atendimento atendimento = new Atendimento(
                atendimentoId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null,
                StatusAtendimento.EM_IA, AGORA, null);
        Mensagem mensagem = Mensagem.midia(
                UUID.randomUUID(), atendimentoId, Remetente.lead(), TipoMensagem.IMAGEM,
                null, "{\"indisponivel\":true}", AGORA);
        return new RegistrarMensagemRecebidaUseCase.Resultado(atendimento, mensagem, false);
    }

    @Test
    void reentregaDeMensagemJaRegistradaNaoEDescarte() {
        when(entrada.reservarPendentes(anyInt())).thenReturn(List.of(pendente(0, AGORA)));
        when(idempotencia.reservarSeNova(anyString())).thenReturn(false);

        processador(Duration.ofHours(2)).rodada();

        verify(canal, never()).baixarMidiaRecebida(anyString());
        verify(entrada).marcarProcessado(ID_EXTERNO, AGORA, List.of());
    }

    @Test
    void postSemItemDeClienteNaoGeraDescarte() {
        // POST so de status (ou Status/Story filtrado): o tradutor nao devolve mensagem nem descarte.
        when(entrada.reservarPendentes(anyInt())).thenReturn(List.of(pendente(0, AGORA)));
        when(tradutor.traduzirComDescartes(anyString()))
                .thenReturn(TradutorDeCanal.Traducao.semDescartes(List.of()));

        processador(Duration.ofHours(2)).rodada();

        verify(entrada).marcarProcessado(ID_EXTERNO, AGORA, List.of());
    }

    private ProcessadorDeWebhookEntradaOperacoes processador(Duration prazoAbsoluto) {
        PlatformTransactionManager transacoes = transacaoPassThrough();
        return new ProcessadorDeWebhookEntradaOperacoes(
                entrada,
                tradutor,
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
                canal,
                mock(ArmazenamentoDeMidia.class),
                canaisAtivos,
                new ObjectMapper(),
                Clock.fixed(AGORA, ZoneOffset.UTC),
                transacoes,
                50,
                5,
                prazoAbsoluto,
                Duration.ofSeconds(5),
                Duration.ofMinutes(30),
                PRAZO_MIDIA);
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

    private static WebhookEntrada.Pendente pendente(int tentativas, Instant recebidoEm) {
        return new WebhookEntrada.Pendente(ID_EXTERNO, "{}", tentativas, recebidoEm);
    }

    private static TradutorDeCanal.MensagemRecebidaDoCanal mensagemDeMidia() {
        return new TradutorDeCanal.MensagemRecebidaDoCanal(
                "wamid.msg-1",
                "5561999999999",
                "Cliente",
                null,
                "IMAGEM",
                "media-id-meta",
                "image/jpeg",
                "foto.jpg",
                null,
                AGORA,
                "phone-id");
    }

    private static TradutorDeCanal.MensagemRecebidaDoCanal mensagemDeMidiaSemId() {
        return new TradutorDeCanal.MensagemRecebidaDoCanal(
                "wamid.msg-sem-id",
                "5561999999999",
                "Cliente",
                null,
                "IMAGEM",
                null,
                "image/jpeg",
                "foto.jpg",
                null,
                AGORA,
                "phone-id");
    }

    private static Stream<Arguments> comandosReset() {
        return Stream.of(Arguments.of("#reset"), Arguments.of(" #RESET "), Arguments.of("\t#ReSeT\n"));
    }

    private static Stream<Arguments> naoComandosReset() {
        return Stream.of(
                Arguments.of((String) null),
                Arguments.of("reset"),
                Arguments.of("#resetar"),
                Arguments.of("texto #reset"));
    }

    private static Stream<Arguments> comandosConfigurados() {
        return Stream.of(
                Arguments.of(" #VOLTAR ", true),
                Arguments.of("#reset", false),
                Arguments.of("quero #voltar", false));
    }
}
