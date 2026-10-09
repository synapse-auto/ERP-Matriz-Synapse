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
import com.synapse.crm.atendimento.application.informacoeschatbot.ListarInformacoesDoChatbotUseCase.Cursor;
import com.synapse.crm.atendimento.domain.atendimento.Atendimento;
import com.synapse.crm.atendimento.domain.atendimento.StatusAtendimento;
import com.synapse.crm.atendimento.domain.informacoeschatbot.InformacoesDoChatbot;

class ListarInformacoesDoChatbotUseCaseTest {

    private static final int PAGINA = 3;
    private static final Instant BASE = Instant.parse("2026-10-08T14:00:00Z");

    private final AtendimentoRepositorio atendimentos = mock(AtendimentoRepositorio.class);
    private final InformacoesDoChatbotRepositorio informacoes = mock(InformacoesDoChatbotRepositorio.class);
    private final HabilitacaoDasInformacoesDoChatbot habilitacao = mock(HabilitacaoDasInformacoesDoChatbot.class);
    private final UUID atendimentoId = UUID.randomUUID();
    private ListarInformacoesDoChatbotUseCase casoDeUso;

    @BeforeEach
    void preparar() {
        casoDeUso = new ListarInformacoesDoChatbotUseCase(atendimentos, informacoes, habilitacao, PAGINA);
    }

    @Test
    @DisplayName("flag desligada: pagina vazia sem consultar atendimento nem cards")
    void desabilitadaDevolveVazioSemConsultar() {
        when(habilitacao.habilitada()).thenReturn(false);

        var pagina = casoDeUso.executar(atendimentoId, null, null);

        assertThat(pagina.itens()).isEmpty();
        assertThat(pagina.proximoCursor()).isNull();
        verifyNoInteractions(atendimentos, informacoes);
    }

    @Test
    @DisplayName("atendimento fora do alcance: indisponivel, e os cards nem sao lidos")
    void foraDoAlcanceNaoLeCards() {
        when(habilitacao.habilitada()).thenReturn(true);
        when(atendimentos.porId(atendimentoId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> casoDeUso.executar(atendimentoId, null, null))
                .isInstanceOf(RecursoDeAtendimentoIndisponivelException.class);
        verifyNoInteractions(informacoes);
    }

    @Test
    @DisplayName("pede uma linha a mais, devolve a pagina em ordem cronologica e o cursor do mais antigo")
    void paginaComCursor() {
        liberar();
        // O repositorio devolve do mais recente para o mais antigo; sobram 4 = ha mais.
        var c5 = card(5);
        var c4 = card(4);
        var c3 = card(3);
        var c2 = card(2);
        when(informacoes.anteriores(atendimentoId, null, null, null, PAGINA + 1)).thenReturn(List.of(c5, c4, c3, c2));

        var pagina = casoDeUso.executar(atendimentoId, null, null);

        assertThat(pagina.itens()).containsExactly(c3, c4, c5);
        assertThat(pagina.proximoCursor()).isEqualTo(new Cursor(c3.registradoEm(), c3.id()));
    }

    @Test
    @DisplayName("ultima pagina: sem cursor, sem perder nenhum card")
    void ultimaPagina() {
        liberar();
        var c2 = card(2);
        var c1 = card(1);
        var cursor = new Cursor(BASE.plusSeconds(3), UUID.randomUUID());
        when(informacoes.anteriores(atendimentoId, null, cursor.registradoEm(), cursor.id(), PAGINA + 1))
                .thenReturn(List.of(c2, c1));

        var pagina = casoDeUso.executar(atendimentoId, null, cursor);

        assertThat(pagina.itens()).containsExactly(c1, c2);
        assertThat(pagina.proximoCursor()).isNull();
    }

    @Test
    @DisplayName("repassa a janela 'desde' ao repositorio")
    void repassaDesde() {
        liberar();
        var desde = BASE.minusSeconds(60);
        when(informacoes.anteriores(atendimentoId, desde, null, null, PAGINA + 1)).thenReturn(List.of());

        assertThat(casoDeUso.executar(atendimentoId, desde, null).itens()).isEmpty();
        verify(informacoes).anteriores(atendimentoId, desde, null, null, PAGINA + 1);
    }

    @Test
    @DisplayName("tamanho de pagina invalido derruba a subida")
    void recusaTamanhoInvalido() {
        assertThatThrownBy(() -> new ListarInformacoesDoChatbotUseCase(atendimentos, informacoes, habilitacao, 0))
                .isInstanceOf(IllegalStateException.class);
    }

    private void liberar() {
        when(habilitacao.habilitada()).thenReturn(true);
        when(atendimentos.porId(atendimentoId)).thenReturn(Optional.of(new Atendimento(
                atendimentoId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                StatusAtendimento.EM_ATENDIMENTO, BASE.minusSeconds(3600), null)));
    }

    private InformacoesDoChatbot card(int ordem) {
        return new InformacoesDoChatbot(UUID.randomUUID(), atendimentoId, "card " + ordem, BASE.plusSeconds(ordem));
    }
}
