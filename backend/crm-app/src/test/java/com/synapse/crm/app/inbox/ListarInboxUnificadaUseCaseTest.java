package com.synapse.crm.app.inbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.synapse.crm.atendimento.application.painel.CartaoAtendimento;
import com.synapse.crm.atendimento.application.painel.ListarAtendimentosVisiveisUseCase;
import com.synapse.crm.atendimento.application.painel.VisaoAtendimento;
import com.synapse.crm.atendimento.domain.atendimento.StatusAtendimento;
import com.synapse.crm.automacaoconfig.application.featureflag.FeatureService;
import com.synapse.crm.equipe.application.chat.ChatInternoRepositorio;
import com.synapse.crm.equipe.application.chat.ListarConversasChatUseCase;
import com.synapse.crm.equipe.domain.chat.TipoConversaChat;

class ListarInboxUnificadaUseCaseTest {
    private final ListarAtendimentosVisiveisUseCase clientes = Mockito.mock(ListarAtendimentosVisiveisUseCase.class);
    private final ListarConversasChatUseCase equipe = Mockito.mock(ListarConversasChatUseCase.class);
    private final FeatureService features = Mockito.mock(FeatureService.class);
    private final ListarInboxUnificadaUseCase caso = new ListarInboxUnificadaUseCase(clientes, equipe, features);

    @Test
    void ordenaGlobalmentePorUltimaMensagemEPaginaComCursor() {
        UUID lead = UUID.randomUUID();
        UUID conversa = UUID.randomUUID();
        when(clientes.executarPaginado(Mockito.eq(VisaoAtendimento.TODOS), Mockito.anyInt(), Mockito.any(), Mockito.any()))
                .thenReturn(List.of(cartao(lead, Instant.parse("2026-01-01T10:00:00Z"))));
        when(features.habilitadas()).thenReturn(List.of("chat_interno"));
        when(equipe.executarPaginado(Mockito.anyInt(), Mockito.any(), Mockito.any())).thenReturn(List.of(new ChatInternoRepositorio.ConversaResumo(
                conversa, TipoConversaChat.DIRETA, "Ana", "Olá", Instant.parse("2026-01-01T11:00:00Z"), 1,
                "/api/v1/me/foto/" + conversa)));

        InboxUnificada primeira = caso.executar(VisaoAtendimento.TODOS, 1, null);
        InboxUnificada segunda = caso.executar(VisaoAtendimento.TODOS, 1, primeira.proximoCursor());

        assertThat(primeira.itens()).extracting(InboxUnificada.Item::tipo)
                .containsExactly(InboxUnificada.Tipo.EQUIPE_INTERNA);
        assertThat(primeira.itens().getFirst().avatarUrl()).isEqualTo("/api/v1/me/foto/" + conversa);
        assertThat(segunda.itens()).extracting(InboxUnificada.Item::tipo)
                .containsExactly(InboxUnificada.Tipo.CLIENTE);
        verify(clientes, never()).executar(VisaoAtendimento.TODOS);
        verify(equipe, never()).executar();
    }

    @Test
    void falhaDoChatInternoNaoEscondeClientes() {
        UUID lead = UUID.randomUUID();
        when(clientes.executarPaginado(Mockito.eq(VisaoAtendimento.TODOS), Mockito.anyInt(), Mockito.isNull(), Mockito.isNull()))
                .thenReturn(List.of(cartao(lead, null)));
        when(features.habilitadas()).thenReturn(List.of("chat_interno"));
        when(equipe.executarPaginado(Mockito.anyInt(), Mockito.isNull(), Mockito.isNull())).thenThrow(new IllegalStateException("indisponível"));

        assertThat(caso.executar(VisaoAtendimento.TODOS, 50, null).itens())
                .singleElement().extracting(InboxUnificada.Item::tipo)
                .isEqualTo(InboxUnificada.Tipo.CLIENTE);
    }

    @Test
    void flagDesligadaNaoIncluiConversasInternas() {
        UUID lead = UUID.randomUUID();
        when(clientes.executarPaginado(Mockito.eq(VisaoAtendimento.TODOS), Mockito.anyInt(), Mockito.isNull(), Mockito.isNull()))
                .thenReturn(List.of(cartao(lead, null)));
        when(features.habilitadas()).thenReturn(List.of());

        assertThat(caso.executar(VisaoAtendimento.TODOS, 50, null).itens())
                .extracting(InboxUnificada.Item::tipo)
                .containsOnly(InboxUnificada.Tipo.CLIENTE);
    }

    @Test
    void desempatePorIdentificadorNaoDuplicaPagina() {
        Instant instante = Instant.parse("2026-01-01T10:00:00Z");
        UUID primeiro = UUID.randomUUID();
        UUID segundo = UUID.randomUUID();
        when(clientes.executarPaginado(Mockito.eq(VisaoAtendimento.TODOS), Mockito.anyInt(), Mockito.any(), Mockito.any()))
                .thenReturn(List.of(cartao(primeiro, instante), cartao(segundo, instante)));
        when(features.habilitadas()).thenReturn(List.of());

        InboxUnificada primeira = caso.executar(VisaoAtendimento.TODOS, 1, null);
        InboxUnificada segunda = caso.executar(VisaoAtendimento.TODOS, 1, primeira.proximoCursor());

        assertThat(primeira.itens()).hasSize(1);
        assertThat(segunda.itens()).hasSize(1);
        assertThat(segunda.itens().getFirst().identificadorVisual())
                .isNotEqualTo(primeira.itens().getFirst().identificadorVisual());
    }

    @Test
    void mensagensNulasFicamNoFimEUsamIdComoDesempate() {
        UUID primeiro = UUID.randomUUID();
        UUID segundo = UUID.randomUUID();
        when(clientes.executarPaginado(Mockito.eq(VisaoAtendimento.TODOS), Mockito.anyInt(), Mockito.any(), Mockito.any()))
                .thenReturn(List.of(cartao(primeiro, null), cartao(segundo, null)));
        when(features.habilitadas()).thenReturn(List.of());

        InboxUnificada primeira = caso.executar(VisaoAtendimento.TODOS, 1, null);
        InboxUnificada segunda = caso.executar(VisaoAtendimento.TODOS, 1, primeira.proximoCursor());

        assertThat(primeira.itens().getFirst().ultimaMensagemEm()).isNull();
        assertThat(segunda.itens()).hasSize(1);
        assertThat(segunda.itens().getFirst().identificadorVisual())
                .isNotEqualTo(primeira.itens().getFirst().identificadorVisual());
    }

    private static CartaoAtendimento cartao(UUID lead, Instant ultimaMensagem) {
        return new CartaoAtendimento(UUID.randomUUID(), lead, "Lead", null, null, "WHATSAPP", null,
                null, null, StatusAtendimento.EM_ATENDIMENTO, null, null, null, "mensagem", "LEAD",
                ultimaMensagem, null, 0);
    }
}
