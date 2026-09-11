package com.synapse.crm.equipe.application.chat;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
    void busca_pontual_preserva_participacao_e_reacoes() {
        when(contexto.atual()).thenReturn(new UsuarioAutenticado(usuario, PapelUsuario.ATENDENTE, false));
        when(repositorio.participante(conversa, usuario)).thenReturn(true);
        UUID mensagem = UUID.randomUUID();
        var resumo = new ChatInternoRepositorio.MensagemResumo(
                mensagem, conversa, usuario, "Ana", "TEXTO", "origem", null, null, Instant.now());
        when(repositorio.mensagem(conversa, mensagem)).thenReturn(java.util.Optional.of(resumo));
        when(reacoes.resumir(List.of(mensagem), usuario)).thenReturn(Map.of());

        var resultado = new ListarMensagensChatUseCase(repositorio, reacoes, contexto)
                .executarPorId(conversa, mensagem);

        org.junit.jupiter.api.Assertions.assertEquals(mensagem, resultado.id());
        verify(repositorio).mensagem(conversa, mensagem);
    }

    @Test
    void busca_pontual_bloqueia_nao_participante() {
        when(contexto.atual()).thenReturn(new UsuarioAutenticado(usuario, PapelUsuario.ATENDENTE, false));
        when(repositorio.participante(conversa, usuario)).thenReturn(false);

        assertThrows(ChatSemAcessoException.class,
                () -> new ListarMensagensChatUseCase(repositorio, reacoes, contexto)
                        .executarPorId(conversa, UUID.randomUUID()));
        verifyNoInteractions(reacoes);
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

    @Test
    void responder_preserva_referencia_e_publica_fato_persistido() {
        when(contexto.atual()).thenReturn(new UsuarioAutenticado(usuario, PapelUsuario.ATENDENTE, false));
        when(repositorio.participante(conversa, usuario)).thenReturn(true);
        UUID origem = UUID.randomUUID();
        when(repositorio.mensagem(conversa, origem)).thenReturn(java.util.Optional.of(
                new ChatInternoRepositorio.MensagemResumo(origem, conversa, UUID.randomUUID(), "Bruno",
                        "TEXTO", "origem", null, null, Instant.now())));
        var salva = new ChatInternoRepositorio.MensagemResumo(UUID.randomUUID(), conversa, usuario, "Ana",
                "TEXTO", "resposta", null, null, Instant.now());
        when(repositorio.salvarMensagemComReferencia(conversa, usuario, "resposta", "TEXTO", null, null,
                conversa, origem, "RESPOSTA")).thenReturn(salva);
        when(repositorio.participantes(conversa)).thenReturn(List.of(usuario, UUID.randomUUID()));

        new ResponderMensagemChatUseCase(repositorio, contexto, eventos).executar(origem, conversa, "resposta");

        verify(eventos).publishEvent(any(EventoDeChatInterno.MensagemEnviada.class));
        verify(repositorio).salvarMensagemComReferencia(conversa, usuario, "resposta", "TEXTO", null, null,
                conversa, origem, "RESPOSTA");
    }

    @Test
    void encaminhar_bloqueia_destino_de_que_usuario_nao_participa() {
        when(contexto.atual()).thenReturn(new UsuarioAutenticado(usuario, PapelUsuario.ATENDENTE, false));
        UUID origem = UUID.randomUUID();
        UUID destino = UUID.randomUUID();
        when(repositorio.participante(conversa, usuario)).thenReturn(true);
        when(repositorio.participante(destino, usuario)).thenReturn(false);

        assertThrows(ChatSemAcessoException.class,
                () -> new EncaminharMensagemChatUseCase(repositorio, contexto, eventos)
                        .executar(origem, conversa, destino));
        verifyNoInteractions(eventos);
    }

    @Test
    void excluir_exige_autor_corrente_e_publica_remocao_depois_da_persistencia() {
        when(contexto.atual()).thenReturn(new UsuarioAutenticado(usuario, PapelUsuario.ATENDENTE, false));
        when(repositorio.participante(conversa, usuario)).thenReturn(true);
        UUID mensagem = UUID.randomUUID();
        when(repositorio.mensagem(conversa, mensagem)).thenReturn(java.util.Optional.of(
                new ChatInternoRepositorio.MensagemResumo(mensagem, conversa, usuario, "Ana", "TEXTO",
                        "segredo", null, null, Instant.now())));
        var removida = new ChatInternoRepositorio.MensagemResumo(mensagem, conversa, usuario, "Ana", "TEXTO",
                null, null, null, Instant.now(), List.of(), true, null);
        when(repositorio.removerMensagem(org.mockito.ArgumentMatchers.eq(conversa),
                org.mockito.ArgumentMatchers.eq(mensagem), org.mockito.ArgumentMatchers.eq(usuario), any()))
                .thenReturn(removida);
        when(repositorio.participantes(conversa)).thenReturn(List.of(usuario));

        var resultado = new ExcluirMensagemChatUseCase(repositorio, contexto, eventos, Clock.systemUTC())
                .executar(mensagem, conversa);

        org.junit.jupiter.api.Assertions.assertTrue(resultado.removida());
        verify(eventos).publishEvent(any(EventoDeChatInterno.MensagemRemovida.class));
    }

    @Test
    void autor_edita_texto_preservando_id_e_publica_evento() {
        when(contexto.atual()).thenReturn(new UsuarioAutenticado(usuario, PapelUsuario.ATENDENTE, false));
        when(repositorio.participante(conversa, usuario)).thenReturn(true);
        UUID mensagem = UUID.randomUUID();
        Instant enviado = Instant.parse("2026-08-24T03:00:00Z");
        when(repositorio.mensagem(conversa, mensagem)).thenReturn(java.util.Optional.of(
                new ChatInternoRepositorio.MensagemResumo(mensagem, conversa, usuario, "Ana", "TEXTO",
                        "antes", null, null, enviado)));
        Instant editado = Instant.parse("2026-08-24T04:00:00Z");
        var resultado = new ChatInternoRepositorio.MensagemResumo(mensagem, conversa, usuario, "Ana", "TEXTO",
                "depois", null, null, enviado, List.of(), false, null);
        when(repositorio.editarMensagem(org.mockito.ArgumentMatchers.eq(conversa),
                org.mockito.ArgumentMatchers.eq(mensagem), org.mockito.ArgumentMatchers.eq(usuario),
                org.mockito.ArgumentMatchers.eq("depois"), any())).thenReturn(resultado);
        when(repositorio.participantes(conversa)).thenReturn(List.of(usuario, UUID.randomUUID()));

        var relogio = Clock.fixed(editado, ZoneOffset.UTC);
        var salvo = new EditarMensagemChatUseCase(repositorio, contexto, eventos, relogio)
                .executar(mensagem, conversa, " depois ");

        org.junit.jupiter.api.Assertions.assertEquals(mensagem, salvo.id());
        verify(repositorio).editarMensagem(conversa, mensagem, usuario, "depois", editado);
        verify(eventos).publishEvent(any(EventoDeChatInterno.MensagemEditada.class));
    }

    @Test
    void editar_bloqueia_autor_diferente_e_mensagem_de_midia() {
        when(contexto.atual()).thenReturn(new UsuarioAutenticado(usuario, PapelUsuario.ATENDENTE, false));
        when(repositorio.participante(conversa, usuario)).thenReturn(true);
        UUID mensagem = UUID.randomUUID();
        when(repositorio.mensagem(conversa, mensagem)).thenReturn(java.util.Optional.of(
                new ChatInternoRepositorio.MensagemResumo(mensagem, conversa, UUID.randomUUID(), "Bruno", "TEXTO",
                        "texto", null, null, Instant.now())));
        assertThrows(ChatSemAcessoException.class,
                () -> new EditarMensagemChatUseCase(repositorio, contexto, eventos, Clock.systemUTC())
                        .executar(mensagem, conversa, "novo"));

        when(repositorio.mensagem(conversa, mensagem)).thenReturn(java.util.Optional.of(
                new ChatInternoRepositorio.MensagemResumo(mensagem, conversa, usuario, "Ana", "AUDIO",
                        null, "url", "{}", Instant.now())));
        assertThrows(OperacaoDeGrupoInvalidaException.class,
                () -> new EditarMensagemChatUseCase(repositorio, contexto, eventos, Clock.systemUTC())
                        .executar(mensagem, conversa, "novo"));
        verifyNoInteractions(eventos);
    }
}
