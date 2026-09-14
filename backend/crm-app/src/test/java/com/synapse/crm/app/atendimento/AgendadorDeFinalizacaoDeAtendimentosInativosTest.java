package com.synapse.crm.app.atendimento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import com.synapse.crm.atendimento.application.FinalizarAtendimentosInativosUseCase;
import com.synapse.crm.automacaoconfig.application.ConfiguracaoAutomacaoRepositorio;
import com.synapse.crm.automacaoconfig.domain.ConfiguracaoAutomacao;
import com.synapse.crm.automacaoconfig.domain.TipoConfiguracaoAutomacao;
import com.synapse.crm.sharedkernel.identidade.ContextoDeServico;

class AgendadorDeFinalizacaoDeAtendimentosInativosTest {

    private static final Instant AGORA = Instant.parse("2026-09-14T12:00:00Z");

    private final ConfiguracaoAutomacaoRepositorio configuracoes = mock(ConfiguracaoAutomacaoRepositorio.class);
    private final FinalizarAtendimentosInativosUseCase finalizar = mock(FinalizarAtendimentosInativosUseCase.class);
    private final Logger logger = (Logger) LoggerFactory.getLogger(AgendadorDeFinalizacaoDeAtendimentosInativos.class);
    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();

    @BeforeEach
    void preparar() {
        ContextoDeServico.instalarPonteDeAutoridade(nome -> () -> {});
        logs.start();
        logger.addAppender(logs);
    }

    @AfterEach
    void limpar() {
        logger.detachAppender(logs);
        ContextoDeServico.instalarPonteDeAutoridade(ContextoDeServico.PonteDeAutoridade.NAO_INSTALADA);
    }

    @Test
    void toggleAusente_naoSelecionaNemFinalizaEnaoAlerta() {
        when(configuracoes.porChave(AgendadorDeFinalizacaoDeAtendimentosInativos.CHAVE_HABILITADO))
                .thenReturn(Optional.empty());

        agendador().finalizarInativos();

        verifyNoInteractions(finalizar);
        assertThat(logs.list).noneMatch(event -> event.getLevel() == Level.ERROR);
    }

    @Test
    void toggleFalse_naoSelecionaNemFinalizaEnaoAlerta() {
        when(configuracoes.porChave(AgendadorDeFinalizacaoDeAtendimentosInativos.CHAVE_HABILITADO))
                .thenReturn(Optional.of(configuracao(
                        AgendadorDeFinalizacaoDeAtendimentosInativos.CHAVE_HABILITADO,
                        "false",
                        TipoConfiguracaoAutomacao.BOOLEAN)));

        agendador().finalizarInativos();

        verifyNoInteractions(finalizar);
        assertThat(logs.list).noneMatch(event -> event.getLevel() == Level.ERROR);
    }

    @Test
    void toggleComValorInvalido_falhaFechadoSemAlertaDeLimiar() {
        when(configuracoes.porChave(AgendadorDeFinalizacaoDeAtendimentosInativos.CHAVE_HABILITADO))
                .thenReturn(Optional.of(configuracao(
                        AgendadorDeFinalizacaoDeAtendimentosInativos.CHAVE_HABILITADO,
                        "talvez",
                        TipoConfiguracaoAutomacao.BOOLEAN)));

        agendador().finalizarInativos();

        verifyNoInteractions(finalizar);
        assertThat(logs.list).noneMatch(event -> event.getLevel() == Level.ERROR);
    }

    @Test
    void toggleTrue_mantemFluxoDoE177() {
        when(configuracoes.porChave(AgendadorDeFinalizacaoDeAtendimentosInativos.CHAVE_HABILITADO))
                .thenReturn(Optional.of(configuracao(
                        AgendadorDeFinalizacaoDeAtendimentosInativos.CHAVE_HABILITADO,
                        "true",
                        TipoConfiguracaoAutomacao.BOOLEAN)));
        when(configuracoes.porChave(AgendadorDeFinalizacaoDeAtendimentosInativos.CHAVE_HORAS))
                .thenReturn(Optional.of(configuracao(
                        AgendadorDeFinalizacaoDeAtendimentosInativos.CHAVE_HORAS,
                        "24",
                        TipoConfiguracaoAutomacao.INT)));

        agendador().finalizarInativos();

        verify(finalizar).executar(AGORA, java.time.Duration.ofHours(24), 10);
    }

    @Test
    void toggleTrueComLimiarAusente_alertaConfigQuebrada() {
        when(configuracoes.porChave(AgendadorDeFinalizacaoDeAtendimentosInativos.CHAVE_HABILITADO))
                .thenReturn(Optional.of(configuracao(
                        AgendadorDeFinalizacaoDeAtendimentosInativos.CHAVE_HABILITADO,
                        "true",
                        TipoConfiguracaoAutomacao.BOOLEAN)));
        when(configuracoes.porChave(AgendadorDeFinalizacaoDeAtendimentosInativos.CHAVE_HORAS))
                .thenReturn(Optional.empty());

        agendador().finalizarInativos();

        verifyNoInteractions(finalizar);
        assertThat(logs.list)
                .anyMatch(event -> event.getLevel() == Level.ERROR
                        && event.getFormattedMessage().contains("ALERTA_CONFIG_AUTOMACAO"));
    }

    @Test
    void toggleTrueComLimiarInvalido_alertaConfigQuebrada() {
        when(configuracoes.porChave(AgendadorDeFinalizacaoDeAtendimentosInativos.CHAVE_HABILITADO))
                .thenReturn(Optional.of(configuracao(
                        AgendadorDeFinalizacaoDeAtendimentosInativos.CHAVE_HABILITADO,
                        "true",
                        TipoConfiguracaoAutomacao.BOOLEAN)));
        when(configuracoes.porChave(AgendadorDeFinalizacaoDeAtendimentosInativos.CHAVE_HORAS))
                .thenReturn(Optional.of(configuracao(
                        AgendadorDeFinalizacaoDeAtendimentosInativos.CHAVE_HORAS,
                        "0",
                        TipoConfiguracaoAutomacao.INT)));

        agendador().finalizarInativos();

        verifyNoInteractions(finalizar);
        assertThat(logs.list)
                .anyMatch(event -> event.getLevel() == Level.ERROR
                        && event.getFormattedMessage().contains("ALERTA_CONFIG_AUTOMACAO"));
    }

    @Test
    void trocarToggleEntreRodadas_mudaComportamentoSemRedeploy() {
        when(configuracoes.porChave(AgendadorDeFinalizacaoDeAtendimentosInativos.CHAVE_HABILITADO))
                .thenReturn(
                        Optional.of(configuracao(
                                AgendadorDeFinalizacaoDeAtendimentosInativos.CHAVE_HABILITADO,
                                "false",
                                TipoConfiguracaoAutomacao.BOOLEAN)),
                        Optional.of(configuracao(
                                AgendadorDeFinalizacaoDeAtendimentosInativos.CHAVE_HABILITADO,
                                "true",
                                TipoConfiguracaoAutomacao.BOOLEAN)));
        when(configuracoes.porChave(AgendadorDeFinalizacaoDeAtendimentosInativos.CHAVE_HORAS))
                .thenReturn(Optional.of(configuracao(
                        AgendadorDeFinalizacaoDeAtendimentosInativos.CHAVE_HORAS,
                        "24",
                        TipoConfiguracaoAutomacao.INT)));

        var agendador = agendador();
        agendador.finalizarInativos();
        agendador.finalizarInativos();

        verify(finalizar).executar(AGORA, java.time.Duration.ofHours(24), 10);
    }

    private AgendadorDeFinalizacaoDeAtendimentosInativos agendador() {
        when(finalizar.executar(any(Instant.class), any(java.time.Duration.class), anyInt()))
                .thenReturn(new FinalizarAtendimentosInativosUseCase.Resultado(0, 0, 0, 0, AGORA));
        return new AgendadorDeFinalizacaoDeAtendimentosInativos(
                configuracoes,
                finalizar,
                new FinalizacaoAtendimentoInativoProperties(10),
                Clock.fixed(AGORA, ZoneOffset.UTC));
    }

    private static ConfiguracaoAutomacao configuracao(
            String chave, String valor, TipoConfiguracaoAutomacao tipo) {
        return new ConfiguracaoAutomacao(chave, valor, null, tipo, null, null, chave, null, AGORA);
    }
}
