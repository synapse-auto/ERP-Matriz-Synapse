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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;

import com.synapse.crm.atendimento.application.AtendimentoRepositorio;
import com.synapse.crm.atendimento.application.ConfiguracaoDoComandoResetRepositorio;
import com.synapse.crm.atendimento.application.IdempotenciaDeMensagemRecebidaRepositorio;
import com.synapse.crm.atendimento.application.RegistrarMensagemRecebidaUseCase;
import com.synapse.crm.atendimento.application.TransferirAtendimentoUseCase;
import com.synapse.crm.atendimento.application.WebhookEntrada;
import com.synapse.crm.atendimento.application.canal.CanalCredencialAtivaRepositorio;
import com.synapse.crm.atendimento.application.canal.CanalEntradaAtiva;
import com.synapse.crm.atendimento.application.referencia.MensagemIdExternoRepositorio;
import com.synapse.crm.atendimento.application.referencia.OrigemDeMensagemRepositorio;
import com.synapse.crm.atendimento.domain.canal.CanalGateway;
import com.synapse.crm.atendimento.domain.canal.ProvedorTemporariamenteIndisponivelException;
import com.synapse.crm.atendimento.domain.canal.TradutorDeCanal;
import com.synapse.crm.core.application.lead.LeadNoCaminhoDeMensagem;
import com.synapse.crm.sharedkernel.midia.ArmazenamentoDeMidia;

class ProcessadorDeWebhookEntradaOperacoesTest {

    private static final Instant AGORA = Instant.parse("2026-09-02T15:00:00Z");
    private static final String ID_EXTERNO = "wamid.midia-1";

    private final WebhookEntrada entrada = mock(WebhookEntrada.class);
    private final TradutorDeCanal tradutor = mock(TradutorDeCanal.class);
    private final IdempotenciaDeMensagemRecebidaRepositorio idempotencia =
            mock(IdempotenciaDeMensagemRecebidaRepositorio.class);
    private final CanalGateway canal = mock(CanalGateway.class);
    private final CanalCredencialAtivaRepositorio canaisAtivos =
            mock(CanalCredencialAtivaRepositorio.class);
    private final LeadNoCaminhoDeMensagem leads = mock(LeadNoCaminhoDeMensagem.class);
    private final ConfiguracaoDoComandoResetRepositorio configuracaoDoReset =
            mock(ConfiguracaoDoComandoResetRepositorio.class);

    @BeforeEach
    void stubsComuns() {
        when(configuracaoDoReset.valor()).thenReturn(Optional.of("#reset"));
        when(idempotencia.reservarSeNova(anyString())).thenReturn(true);
        when(leads.resolverPorTelefone(anyString(), any())).thenReturn(UUID.randomUUID());
        when(canaisAtivos.porIdentificadorExterno(anyString()))
                .thenReturn(Optional.of(new CanalEntradaAtiva(UUID.randomUUID(), UUID.randomUUID())));
        when(tradutor.traduzir(anyString())).thenReturn(List.of(mensagemDeMidia()));
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

        verify(entrada).adiar(eq(ID_EXTERNO), anyString());
        verify(entrada, never()).reagendar(anyString(), anyString());
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
        verify(entrada, never()).adiar(anyString(), anyString());
        verify(entrada, never()).reagendar(anyString(), anyString());
    }

    @Test
    void falhaRealDaMetaContinuaConsumindoTentativa() {
        when(entrada.reservarPendentes(anyInt())).thenReturn(List.of(pendente(0, AGORA.minusSeconds(10))));
        when(canal.baixarMidiaRecebida(anyString()))
                .thenThrow(new IllegalStateException("502 Bad Gateway do provedor"));

        processador(Duration.ofHours(2)).rodada();

        verify(entrada).reagendar(eq(ID_EXTERNO), anyString());
        verify(entrada, never()).adiar(anyString(), anyString());
        verify(entrada, never()).esgotar(anyString(), any(), anyString());
    }

    @Test
    void falhaRealNaUltimaTentativaEsgota() {
        when(entrada.reservarPendentes(anyInt())).thenReturn(List.of(pendente(4, AGORA.minusSeconds(10))));
        when(canal.baixarMidiaRecebida(anyString()))
                .thenThrow(new IllegalStateException("400 da Meta"));

        processador(Duration.ofHours(2)).rodada();

        verify(entrada).esgotar(eq(ID_EXTERNO), eq(AGORA), anyString());
        verify(entrada, never()).adiar(anyString(), anyString());
        verify(entrada, never()).reagendar(anyString(), anyString());
    }

    private ProcessadorDeWebhookEntradaOperacoes processador(Duration prazoAbsoluto) {
        PlatformTransactionManager transacoes = transacaoPassThrough();
        return new ProcessadorDeWebhookEntradaOperacoes(
                entrada,
                tradutor,
                idempotencia,
                mock(RegistrarMensagemRecebidaUseCase.class),
                mock(MensagemIdExternoRepositorio.class),
                mock(OrigemDeMensagemRepositorio.class),
                mock(AtendimentoRepositorio.class),
                configuracaoDoReset,
                mock(TransferirAtendimentoUseCase.class),
                leads,
                canal,
                mock(ArmazenamentoDeMidia.class),
                canaisAtivos,
                new ObjectMapper(),
                Clock.fixed(AGORA, ZoneOffset.UTC),
                transacoes,
                50,
                5,
                prazoAbsoluto);
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
