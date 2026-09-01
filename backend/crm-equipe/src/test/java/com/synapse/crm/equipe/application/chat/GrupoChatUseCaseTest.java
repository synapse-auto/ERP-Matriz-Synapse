package com.synapse.crm.equipe.application.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.context.ApplicationEventPublisher;

import com.synapse.crm.equipe.domain.chat.TipoConversaChat;
import com.synapse.crm.sharedkernel.identidade.PapelUsuario;
import com.synapse.crm.sharedkernel.identidade.UsuarioAutenticado;
import com.synapse.crm.sharedkernel.identidade.UsuarioContext;

class GrupoChatUseCaseTest {
    private final ChatInternoRepositorio repositorio = Mockito.mock(ChatInternoRepositorio.class);
    private final UsuarioContext contexto = Mockito.mock(UsuarioContext.class);
    private final ApplicationEventPublisher eventos = Mockito.mock(ApplicationEventPublisher.class);
    private final UUID conversa = UUID.randomUUID();
    private final UUID criador = UUID.randomUUID();
    private final UUID colega = UUID.randomUUID();
    private final UUID terceiro = UUID.randomUUID();

    @BeforeEach
    void autenticar() {
        when(contexto.atual()).thenReturn(new UsuarioAutenticado(criador, PapelUsuario.ATENDENTE, false));
    }

    @Test
    void criar_grupo_inclui_criador_e_publica_sistema() {
        when(repositorio.usuarioExiste(any())).thenReturn(true);
        when(repositorio.criarConversaGrupo(eq("Ops"), any())).thenReturn(conversa);
        var sistema = mensagemSistema("{\"evento\":\"GRUPO_CRIADO\"}");
        when(repositorio.salvarMensagemSistema(eq(conversa), eq(criador), any())).thenReturn(sistema);

        UUID id = new CriarGrupoChatUseCase(repositorio, contexto, eventos).executar(" Ops ", List.of(colega));

        assertEquals(conversa, id);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<UUID>> membros = ArgumentCaptor.forClass(List.class);
        verify(repositorio).criarConversaGrupo(eq("Ops"), membros.capture());
        assertEquals(2, membros.getValue().size());
        org.junit.jupiter.api.Assertions.assertTrue(membros.getValue().contains(criador));
        org.junit.jupiter.api.Assertions.assertTrue(membros.getValue().contains(colega));
        verify(eventos).publishEvent(any(EventoDeChatInterno.MensagemEnviada.class));
    }

    @Test
    void participante_comum_adiciona_renomeia_e_remove() {
        when(repositorio.participante(conversa, criador)).thenReturn(true);
        when(repositorio.tipoDaConversa(conversa)).thenReturn(Optional.of(TipoConversaChat.GRUPO));
        when(repositorio.usuarioExiste(terceiro)).thenReturn(true);
        when(repositorio.participante(conversa, terceiro)).thenReturn(false);
        when(repositorio.nomeDoUsuario(terceiro)).thenReturn(Optional.of("Carla"));
        when(repositorio.participantes(conversa)).thenReturn(List.of(criador, colega, terceiro));
        when(repositorio.salvarMensagemSistema(eq(conversa), eq(criador), any()))
                .thenReturn(mensagemSistema("{}"));
        when(repositorio.nomeDoGrupo(conversa)).thenReturn(Optional.of("Ops"));
        when(repositorio.participante(conversa, colega)).thenReturn(true);
        when(repositorio.nomeDoUsuario(colega)).thenReturn(Optional.of("Bruno"));

        new AdicionarParticipanteGrupoChatUseCase(repositorio, contexto, eventos)
                .executar(conversa, terceiro);
        verify(repositorio).adicionarParticipante(conversa, terceiro);

        new RenomearGrupoChatUseCase(repositorio, contexto, eventos).executar(conversa, "Nova Ops");
        verify(repositorio).renomearGrupo(conversa, "Nova Ops");

        when(repositorio.participantes(conversa)).thenReturn(List.of(criador, terceiro));
        new RemoverParticipanteGrupoChatUseCase(repositorio, contexto, eventos)
                .executar(conversa, colega);
        verify(repositorio).removerParticipante(conversa, colega);
    }

    @Test
    void nao_participante_nao_altera_grupo() {
        when(repositorio.participante(conversa, criador)).thenReturn(false);

        assertThrows(ChatSemAcessoException.class,
                () -> new AdicionarParticipanteGrupoChatUseCase(repositorio, contexto, eventos)
                        .executar(conversa, terceiro));
        assertThrows(ChatSemAcessoException.class,
                () -> new RenomearGrupoChatUseCase(repositorio, contexto, eventos)
                        .executar(conversa, "X"));
        assertThrows(ChatSemAcessoException.class,
                () -> new RemoverParticipanteGrupoChatUseCase(repositorio, contexto, eventos)
                        .executar(conversa, colega));
        verify(repositorio, never()).adicionarParticipante(any(), any());
    }

    @Test
    void conversa_direta_nao_vira_grupo() {
        when(repositorio.participante(conversa, criador)).thenReturn(true);
        when(repositorio.tipoDaConversa(conversa)).thenReturn(Optional.of(TipoConversaChat.DIRETA));

        assertThrows(OperacaoDeGrupoInvalidaException.class,
                () -> new AdicionarParticipanteGrupoChatUseCase(repositorio, contexto, eventos)
                        .executar(conversa, terceiro));
        verify(repositorio, never()).adicionarParticipante(any(), any());
    }

    @Test
    void ultimo_a_sair_apaga_conversa_orfa() {
        when(repositorio.participante(conversa, criador)).thenReturn(true);
        when(repositorio.tipoDaConversa(conversa)).thenReturn(Optional.of(TipoConversaChat.GRUPO));
        when(repositorio.participante(conversa, criador)).thenReturn(true);
        when(repositorio.nomeDoUsuario(criador)).thenReturn(Optional.of("Ana"));
        when(repositorio.salvarMensagemSistema(eq(conversa), eq(criador), any()))
                .thenReturn(mensagemSistema("{}"));
        when(repositorio.participantes(conversa)).thenReturn(List.of());

        new RemoverParticipanteGrupoChatUseCase(repositorio, contexto, eventos)
                .executar(conversa, criador);

        verify(repositorio).removerParticipante(conversa, criador);
        verify(repositorio).apagarSeSemParticipantes(conversa);
    }

    private ChatInternoRepositorio.MensagemResumo mensagemSistema(String conteudo) {
        return new ChatInternoRepositorio.MensagemResumo(
                UUID.randomUUID(), conversa, criador, "Ana", "SISTEMA", conteudo, null, null,
                Instant.parse("2026-09-01T12:00:00Z"));
    }
}
