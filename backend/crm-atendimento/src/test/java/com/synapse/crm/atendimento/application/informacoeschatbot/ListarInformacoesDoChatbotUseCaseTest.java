package com.synapse.crm.atendimento.application.informacoeschatbot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.synapse.crm.atendimento.application.AtendimentoRepositorio;
import com.synapse.crm.atendimento.application.RecursoDeAtendimentoIndisponivelException;
import com.synapse.crm.atendimento.domain.atendimento.Atendimento;
import com.synapse.crm.atendimento.domain.atendimento.StatusAtendimento;
import com.synapse.crm.atendimento.domain.informacoeschatbot.InformacoesDoChatbot;

class ListarInformacoesDoChatbotUseCaseTest {

    private static final int LIMITE = 7;

    private final AtendimentoRepositorio atendimentos = mock(AtendimentoRepositorio.class);
    private final InformacoesDoChatbotRepositorio informacoes = mock(InformacoesDoChatbotRepositorio.class);
    private final HabilitacaoDasInformacoesDoChatbot habilitacao = mock(HabilitacaoDasInformacoesDoChatbot.class);
    private final UUID atendimentoId = UUID.randomUUID();
    private ListarInformacoesDoChatbotUseCase casoDeUso;

    @BeforeEach
    void preparar() {
        casoDeUso = new ListarInformacoesDoChatbotUseCase(atendimentos, informacoes, habilitacao, LIMITE);
    }

    @Test
    @DisplayName("flag desligada: lista vazia sem consultar atendimento nem cards")
    void desabilitadaDevolveVazioSemConsultar() {
        when(habilitacao.habilitada()).thenReturn(false);

        assertThat(casoDeUso.executar(atendimentoId)).isEmpty();
        verifyNoInteractions(atendimentos, informacoes);
    }

    @Test
    @DisplayName("atendimento fora do alcance: indisponivel, e os cards nem sao lidos")
    void foraDoAlcanceNaoLeCards() {
        when(habilitacao.habilitada()).thenReturn(true);
        when(atendimentos.porId(atendimentoId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> casoDeUso.executar(atendimentoId))
                .isInstanceOf(RecursoDeAtendimentoIndisponivelException.class);
        verifyNoInteractions(informacoes);
    }

    @Test
    @DisplayName("devolve os cards do atendimento com o limite configurado")
    void devolveOsCardsComLimite() {
        when(habilitacao.habilitada()).thenReturn(true);
        when(atendimentos.porId(atendimentoId)).thenReturn(Optional.of(new Atendimento(
                atendimentoId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                StatusAtendimento.EM_ATENDIMENTO, Instant.parse("2026-10-08T13:00:00Z"), null)));
        var card = new InformacoesDoChatbot(
                UUID.randomUUID(), atendimentoId, "Nome: Maria", Instant.parse("2026-10-08T14:00:00Z"));
        when(informacoes.recentesDoAtendimento(atendimentoId, LIMITE)).thenReturn(List.of(card));

        assertThat(casoDeUso.executar(atendimentoId)).containsExactly(card);
        verify(informacoes).recentesDoAtendimento(atendimentoId, LIMITE);
    }

    @Test
    @DisplayName("limite invalido derruba a subida")
    void recusaLimiteInvalido() {
        assertThatThrownBy(() -> new ListarInformacoesDoChatbotUseCase(atendimentos, informacoes, habilitacao, 0))
                .isInstanceOf(IllegalStateException.class);
    }
}
