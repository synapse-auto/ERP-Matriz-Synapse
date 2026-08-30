package com.synapse.crm.equipe.application.chat;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mockito;
import org.springframework.context.ApplicationEventPublisher;

import com.synapse.crm.equipe.domain.usuario.StatusPresenca;
import com.synapse.crm.sharedkernel.identidade.PapelUsuario;
import com.synapse.crm.sharedkernel.identidade.UsuarioAutenticado;
import com.synapse.crm.sharedkernel.identidade.UsuarioContext;

class ChatInternoUseCaseTest {
    private final ChatInternoRepositorio repositorio = Mockito.mock(ChatInternoRepositorio.class);
    private final ReacaoDeChatInternoRepositorio reacoes = Mockito.mock(ReacaoDeChatInternoRepositorio.class);
    private final UsuarioContext contexto = Mockito.mock(UsuarioContext.class);
    private final ApplicationEventPublisher eventos = Mockito.mock(ApplicationEventPublisher.class);
    private final UUID conversa = UUID.randomUUID();
    private final UUID usuario = UUID.randomUUID();

    @ParameterizedTest
    @EnumSource(value = PapelUsuario.class, names = {"ATENDENTE", "GESTOR", "ADMINISTRADOR"})
    void nao_participante_nao_le_nem_escreve_mesmo_com_papel_amplo(PapelUsuario papel) {
        when(contexto.atual()).thenReturn(new UsuarioAutenticado(usuario, papel, false));
        when(repositorio.participante(conversa, usuario)).thenReturn(false);

        assertThrows(ChatSemAcessoException.class,
                () -> new ListarMensagensChatUseCase(repositorio, reacoes, contexto).executar(conversa, null, 50));
        assertThrows(ChatSemAcessoException.class,
                () -> new EnviarMensagemChatUseCase(repositorio, contexto, eventos).executar(conversa, "texto"));
        assertThrows(ChatSemAcessoException.class,
                () -> new DefinirReacaoChatUseCase(repositorio, reacoes, contexto, eventos)
                        .executar(conversa, UUID.randomUUID(), "👍"));
        assertThrows(ChatSemAcessoException.class,
                () -> new RemoverReacaoChatUseCase(repositorio, reacoes, contexto, eventos)
                        .executar(conversa, UUID.randomUUID()));
    }

    @Test
    void mensagem_publica_evento_como_fato_persistido() {
        when(contexto.atual()).thenReturn(new UsuarioAutenticado(usuario, PapelUsuario.ATENDENTE, false));
        when(repositorio.participante(conversa, usuario)).thenReturn(true);
        Instant quando = Instant.parse("2026-08-24T03:00:00Z");
        var salva = new ChatInternoRepositorio.MensagemResumo(UUID.randomUUID(), conversa, usuario, "Ana", "TEXTO", "texto", null, null, quando);
        when(repositorio.salvarMensagem(conversa, usuario, "texto")).thenReturn(salva);

        new EnviarMensagemChatUseCase(repositorio, contexto, eventos).executar(conversa, " texto ");

        verify(eventos).publishEvent(any(EventoDeChatInterno.MensagemEnviada.class));
    }

    @Test
    void leitura_e_individual_e_usa_relogio_injetado() {
        when(contexto.atual()).thenReturn(new UsuarioAutenticado(usuario, PapelUsuario.GESTOR, false));
        when(repositorio.participante(conversa, usuario)).thenReturn(true);
        var relogio = Clock.fixed(Instant.parse("2026-08-24T03:00:00Z"), ZoneOffset.UTC);

        new MarcarConversaChatComoLidaUseCase(repositorio, contexto, relogio).executar(conversa);

        verify(repositorio).marcarComoLida(conversa, usuario, Instant.parse("2026-08-24T03:00:00Z"));
    }

    @Test
    void lista_de_mensagens_preserva_cursor_e_limite_para_paginacao() {
        when(contexto.atual()).thenReturn(new UsuarioAutenticado(usuario, PapelUsuario.ADMINISTRADOR, false));
        when(repositorio.participante(conversa, usuario)).thenReturn(true);
        Instant cursor = Instant.parse("2026-08-24T02:00:00Z");
        when(repositorio.listarMensagens(conversa, usuario, cursor, 50))
                .thenReturn(new ChatInternoRepositorio.PaginaMensagens(List.of(), null));
        when(reacoes.resumir(List.of(), usuario)).thenReturn(Map.of());

        new ListarMensagensChatUseCase(repositorio, reacoes, contexto).executar(conversa, cursor, 50);

        verify(repositorio).listarMensagens(conversa, usuario, cursor, 50);
    }

    @Test
    void lista_de_contatos_preserva_presenca_da_fonte_de_verdade() {
        when(contexto.atual()).thenReturn(new UsuarioAutenticado(usuario, PapelUsuario.ATENDENTE, false));
        UUID outro = UUID.randomUUID();
        when(repositorio.listarContatos(usuario)).thenReturn(
                java.util.List.of(new ChatInternoRepositorio.ContatoResumo(outro, "Bruno", null, StatusPresenca.ONLINE)));

        var contatos = new ListarContatosChatUseCase(repositorio, contexto).executar();

        org.junit.jupiter.api.Assertions.assertEquals(StatusPresenca.ONLINE, contatos.getFirst().presenca());
        verify(repositorio).listarContatos(usuario);
    }
}
