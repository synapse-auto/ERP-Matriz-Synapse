package com.synapse.crm.atendimento.application.informacoeschatbot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import com.synapse.crm.atendimento.application.AtendimentoRepositorio;
import com.synapse.crm.atendimento.application.RecursoDeAtendimentoIndisponivelException;
import com.synapse.crm.atendimento.application.informacoeschatbot.InformacoesDoChatbotRecusadasException.Motivo;
import com.synapse.crm.atendimento.domain.atendimento.Atendimento;
import com.synapse.crm.atendimento.domain.atendimento.StatusAtendimento;
import com.synapse.crm.atendimento.domain.evento.InformacoesDoChatbotParaTempoReal;
import com.synapse.crm.atendimento.domain.informacoeschatbot.ConteudoDasInformacoesInvalidoException;

class RegistrarInformacoesDoChatbotUseCaseTest {

    private static final Instant AGORA = Instant.parse("2026-10-08T14:00:00Z");
    private static final int LIMITE = 100;

    private final AtendimentoRepositorio atendimentos = mock(AtendimentoRepositorio.class);
    private final InformacoesDoChatbotRepositorio informacoes = mock(InformacoesDoChatbotRepositorio.class);
    private final HabilitacaoDasInformacoesDoChatbot habilitacao = mock(HabilitacaoDasInformacoesDoChatbot.class);
    private final ApplicationEventPublisher eventos = mock(ApplicationEventPublisher.class);
    private final Clock relogio = Clock.fixed(AGORA, ZoneOffset.UTC);

    private RegistrarInformacoesDoChatbotUseCase casoDeUso;
    private final UUID atendimentoId = UUID.randomUUID();
    private final UUID leadId = UUID.randomUUID();

    @BeforeEach
    void preparar() {
        casoDeUso = novo(LIMITE);
        when(habilitacao.habilitada()).thenReturn(true);
    }

    @Test
    @DisplayName("grava o snapshot e avisa o tempo real com ids, sem o texto")
    void gravaEPublicaAviso() {
        com(StatusAtendimento.EM_ATENDIMENTO);

        var resultado = casoDeUso.executar(atendimentoId, "chave-1", "Nome: Maria");

        verify(informacoes).inserir(resultado.id(), atendimentoId, "chave-1", "Nome: Maria", AGORA);
        var aviso = ArgumentCaptor.forClass(InformacoesDoChatbotParaTempoReal.class);
        verify(eventos).publishEvent(aviso.capture());
        assertThat(aviso.getValue())
                .isEqualTo(new InformacoesDoChatbotParaTempoReal(atendimentoId, leadId, resultado.id(), AGORA));
        assertThat(aviso.getValue().toString()).doesNotContain("Maria");
    }

    @Test
    @DisplayName("atendimento ainda com a IA: a transferencia nao aconteceu, nada e gravado")
    void recusaAtendimentoEmIa() {
        com(StatusAtendimento.EM_IA);

        assertThatThrownBy(() -> casoDeUso.executar(atendimentoId, "chave-1", "texto"))
                .isInstanceOfSatisfying(InformacoesDoChatbotRecusadasException.class,
                        erro -> assertThat(erro.motivo()).isEqualTo(Motivo.ATENDIMENTO_NAO_TRANSFERIDO));
        verify(informacoes, never()).inserir(any(), any(), any(), any(), any());
        verifyNoInteractions(eventos);
    }

    @Test
    @DisplayName("callback atrasado para atendimento finalizado e recusado, nunca redirecionado")
    void recusaAtendimentoFinalizado() {
        com(StatusAtendimento.FINALIZADO);

        assertThatThrownBy(() -> casoDeUso.executar(atendimentoId, "chave-1", "texto"))
                .isInstanceOfSatisfying(InformacoesDoChatbotRecusadasException.class,
                        erro -> assertThat(erro.motivo()).isEqualTo(Motivo.ATENDIMENTO_FINALIZADO));
        verify(informacoes, never()).inserir(any(), any(), any(), any(), any());
        // Nunca procura "o atendimento aberto do lead": o unico acesso e pelo id informado.
        verify(atendimentos, never()).abertoDoLead(any());
        verifyNoInteractions(eventos);
    }

    @Test
    @DisplayName("atendimento inexistente ou fora do alcance vira 'indisponivel'")
    void recusaAtendimentoInexistente() {
        when(atendimentos.porId(atendimentoId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> casoDeUso.validarDestino(atendimentoId))
                .isInstanceOf(RecursoDeAtendimentoIndisponivelException.class);
    }

    @Test
    @DisplayName("recurso desabilitado na instancia nao consulta atendimento nem grava")
    void recusaQuandoDesabilitado() {
        when(habilitacao.habilitada()).thenReturn(false);

        assertThatThrownBy(() -> casoDeUso.exigirHabilitada())
                .isInstanceOf(InformacoesDoChatbotDesabilitadasException.class);
        verifyNoInteractions(atendimentos, informacoes, eventos);
    }

    @Test
    @DisplayName("normaliza com o limite da instancia")
    void normalizaComOLimite() {
        assertThat(casoDeUso.normalizar("a\r\nb")).isEqualTo("a\nb");
        assertThatThrownBy(() -> casoDeUso.normalizar("x".repeat(LIMITE + 1)))
                .isInstanceOf(ConteudoDasInformacoesInvalidoException.class);
    }

    @Test
    @DisplayName("limite configurado acima do teto da coluna derruba a subida, nao o runtime")
    void recusaLimiteAcimaDoTetoDoBanco() {
        assertThatThrownBy(() -> novo(RegistrarInformacoesDoChatbotUseCase.TETO_ABSOLUTO_DE_CARACTERES + 1))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> novo(0)).isInstanceOf(IllegalStateException.class);
    }

    private RegistrarInformacoesDoChatbotUseCase novo(int limite) {
        return new RegistrarInformacoesDoChatbotUseCase(
                atendimentos, informacoes, habilitacao, eventos, relogio, limite);
    }

    private void com(StatusAtendimento status) {
        UUID atendente = status == StatusAtendimento.EM_IA ? null : UUID.randomUUID();
        when(atendimentos.porId(atendimentoId)).thenReturn(Optional.of(new Atendimento(
                atendimentoId, leadId, UUID.randomUUID(), UUID.randomUUID(), atendente, status,
                AGORA.minusSeconds(60), status == StatusAtendimento.FINALIZADO ? AGORA : null)));
    }
}
