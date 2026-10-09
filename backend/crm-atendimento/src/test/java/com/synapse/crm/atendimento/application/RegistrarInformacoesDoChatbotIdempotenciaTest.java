package com.synapse.crm.atendimento.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.synapse.crm.atendimento.application.informacoeschatbot.InformacoesDoChatbotDesabilitadasException;
import com.synapse.crm.atendimento.application.informacoeschatbot.RegistrarInformacoesDoChatbotUseCase;
import com.synapse.crm.atendimento.domain.informacoeschatbot.ConteudoDasInformacoes;
import com.synapse.crm.atendimento.domain.informacoeschatbot.ConteudoDasInformacoesInvalidoException;

/**
 * Ordem das decisoes do contrato do n8n: chave → replay → so para operacao NOVA a configuracao
 * atual (flag, limite, destino). Uma operacao concluida nunca e invalidada por configuracao posterior.
 */
class RegistrarInformacoesDoChatbotIdempotenciaTest {

    private static final String OPERACAO = "REGISTRAR_INFORMACOES_CHATBOT";

    private final RegistrarInformacoesDoChatbotUseCase informacoes = mock(RegistrarInformacoesDoChatbotUseCase.class);
    private final IdempotenciaDeComandoAutomacao idempotencia = mock(IdempotenciaDeComandoAutomacao.class);
    private final ObjectMapper json = new ObjectMapper().registerModule(new JavaTimeModule());
    private final UUID atendimentoId = UUID.randomUUID();

    private ComandosAutomacaoUseCase comandos;

    @BeforeEach
    void preparar() {
        comandos = new ComandosAutomacaoUseCase(
                null, null, null, null, null, null, informacoes, idempotencia, json);
        when(informacoes.normalizarParaIdempotencia(any()))
                .thenAnswer(invocacao -> ConteudoDasInformacoes.normalizar(invocacao.getArgument(0)));
    }

    @Test
    @DisplayName("replay de operacao concluida devolve a resposta original mesmo com a flag desligada e o limite reduzido")
    void replayNaoDependeDaConfiguracaoDeHoje() {
        // 1) Operacao aceita quando tudo estava habilitado: captura hash e resposta gravados.
        UUID cardId = UUID.randomUUID();
        var resultado = new RegistrarInformacoesDoChatbotUseCase.Resultado(
                cardId, atendimentoId, Instant.parse("2026-10-08T14:00:00Z"));
        when(idempotencia.buscar("chave-1")).thenReturn(Optional.empty());
        when(idempotencia.reservar(eq("chave-1"), eq(OPERACAO), eq(atendimentoId), anyString()))
                .thenReturn(new IdempotenciaDeComandoAutomacao.Reserva(true, "chave-1", OPERACAO, atendimentoId, "h", null));
        when(informacoes.executar(eq(atendimentoId), eq("chave-1"), anyString())).thenReturn(resultado);
        var original = comandos.registrarInformacoesDoChatbot(atendimentoId, "chave-1", "Nome: Maria\r\nInteresse: avaliacao");

        var hash = ArgumentCaptor.forClass(String.class);
        verify(idempotencia).reservar(eq("chave-1"), eq(OPERACAO), eq(atendimentoId), hash.capture());
        var resposta = ArgumentCaptor.forClass(String.class);
        verify(idempotencia).concluir(eq("chave-1"), resposta.capture());

        // 2) Hoje: flag desligada, limite menor que o texto e atendimento ja finalizado. O replay passa.
        clearInvocations(informacoes);
        doThrow(new InformacoesDoChatbotDesabilitadasException()).when(informacoes).exigirHabilitada();
        doThrow(new ConteudoDasInformacoesInvalidoException("limite")).when(informacoes).validarConteudo(any());
        when(informacoes.validarDestino(any())).thenThrow(new IllegalStateException("nao deveria consultar o destino"));
        when(idempotencia.buscar("chave-1")).thenReturn(Optional.of(new IdempotenciaDeComandoAutomacao.Reserva(
                false, "chave-1", OPERACAO, atendimentoId, hash.getValue(), resposta.getValue())));

        var replay = comandos.registrarInformacoesDoChatbot(
                atendimentoId, "chave-1", "Nome: Maria\nInteresse: avaliacao");

        assertThat(replay).isEqualTo(original);
        assertThat(replay.id()).isEqualTo(cardId);
        verify(informacoes, never()).exigirHabilitada();
        verify(informacoes, never()).validarConteudo(any());
        verify(informacoes, never()).validarDestino(any());
        verify(informacoes, never()).executar(any(), anyString(), anyString());
    }

    @Test
    @DisplayName("replay com conteudo diferente continua sendo chave reutilizada, mesmo com a flag desligada")
    void replayComConteudoDiferenteConflita() {
        when(idempotencia.buscar("chave-1")).thenReturn(Optional.of(new IdempotenciaDeComandoAutomacao.Reserva(
                false, "chave-1", OPERACAO, atendimentoId, "hash-de-outro-conteudo", "{}")));
        doThrow(new InformacoesDoChatbotDesabilitadasException()).when(informacoes).exigirHabilitada();

        assertThatThrownBy(() -> comandos.registrarInformacoesDoChatbot(atendimentoId, "chave-1", "texto novo"))
                .isInstanceOf(ChaveIdempotenciaReutilizadaException.class);
    }

    @Test
    @DisplayName("operacao NOVA com a flag desligada: 409 de funcionalidade, sem reservar a chave")
    void operacaoNovaExigeFlag() {
        when(idempotencia.buscar("chave-nova")).thenReturn(Optional.empty());
        doThrow(new InformacoesDoChatbotDesabilitadasException()).when(informacoes).exigirHabilitada();

        assertThatThrownBy(() -> comandos.registrarInformacoesDoChatbot(atendimentoId, "chave-nova", "texto"))
                .isInstanceOf(InformacoesDoChatbotDesabilitadasException.class);
        verify(idempotencia, never()).reservar(anyString(), anyString(), any(), anyString());
    }

    @Test
    @DisplayName("operacao NOVA acima do limite atual: 422, sem reservar a chave")
    void operacaoNovaValidaOLimite() {
        when(idempotencia.buscar("chave-nova")).thenReturn(Optional.empty());
        doThrow(new ConteudoDasInformacoesInvalidoException("limite")).when(informacoes).validarConteudo(any());

        assertThatThrownBy(() -> comandos.registrarInformacoesDoChatbot(atendimentoId, "chave-nova", "texto"))
                .isInstanceOf(ConteudoDasInformacoesInvalidoException.class);
        verify(idempotencia, never()).reservar(anyString(), anyString(), any(), anyString());
    }

    @Test
    @DisplayName("chave em branco e 400 antes de qualquer validacao de conteudo")
    void chaveEmBrancoVemPrimeiro() {
        assertThatThrownBy(() -> comandos.registrarInformacoesDoChatbot(atendimentoId, " ", ""))
                .isInstanceOf(IdempotencyKeyInvalidaException.class);
        verify(informacoes, never()).validarConteudo(any());
        verifyNoInteractions(idempotencia);
    }
}
