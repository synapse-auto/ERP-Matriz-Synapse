package com.synapse.crm.equipe.application.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.context.ApplicationEventPublisher;

import com.synapse.crm.equipe.application.chat.ChatInternoRepositorio.MensagemResumo;
import com.synapse.crm.sharedkernel.identidade.PapelUsuario;
import com.synapse.crm.sharedkernel.identidade.UsuarioAutenticado;
import com.synapse.crm.sharedkernel.identidade.UsuarioContext;
import com.synapse.crm.sharedkernel.midia.ArmazenamentoDeMidia;
import com.synapse.crm.sharedkernel.midia.CategoriaDeMidia;
import com.synapse.crm.sharedkernel.midia.DetectorDeTipoReal;
import com.synapse.crm.sharedkernel.midia.LimiteDeAnexoRepositorio;

class EnviarMidiaChatUseCaseTest {
    private final ChatInternoRepositorio repositorio = Mockito.mock(ChatInternoRepositorio.class);
    private final UsuarioContext contexto = Mockito.mock(UsuarioContext.class);
    private final ApplicationEventPublisher eventos = Mockito.mock(ApplicationEventPublisher.class);
    private final ArmazenamentoDeMidia armazenamento = Mockito.mock(ArmazenamentoDeMidia.class);
    private final DetectorDeTipoReal detector = Mockito.mock(DetectorDeTipoReal.class);
    private final LimiteDeAnexoRepositorio limites = Mockito.mock(LimiteDeAnexoRepositorio.class);
    private final IdempotenciaDeMidiaChatRepositorio idempotencia = Mockito.mock(IdempotenciaDeMidiaChatRepositorio.class);
    private final UUID conversa = UUID.randomUUID();
    private final UUID usuario = UUID.randomUUID();

    @Test
    void grava_imagem_e_legenda_na_mesma_mensagem_e_retorna_a_mesma_no_replay() {
        byte[] bytes = {1, 2, 3};
        UUID mensagemId = UUID.randomUUID();
        var salva = new MensagemResumo(mensagemId, conversa, usuario, "Ana", "IMAGEM", "Legenda\ncom acento",
                "chat-interno/capa.png", "{\"legenda\":\"Legenda\\ncom acento\"}", Instant.now());
        when(contexto.atual()).thenReturn(new UsuarioAutenticado(usuario, PapelUsuario.ATENDENTE, false));
        when(repositorio.participante(conversa, usuario)).thenReturn(true);
        when(detector.detectar(bytes)).thenReturn("image/png");
        when(limites.limiteEmBytes(CategoriaDeMidia.IMAGEM)).thenReturn(Optional.empty());
        when(idempotencia.reservar(eq("chave-1"), eq(usuario), eq(conversa), any()))
                .thenReturn(new IdempotenciaDeMidiaChatRepositorio.Reserva(
                        "chave-1", usuario, conversa, "impressao", null, true));
        when(armazenamento.salvar(eq(bytes), any(String.class), eq("image/png")))
                .thenReturn("chat-interno/capa.png");
        when(repositorio.salvarMensagemDeMidia(eq(conversa), eq(usuario), eq("IMAGEM"),
                eq("Legenda\ncom acento"), eq("chat-interno/capa.png"), any()))
                .thenReturn(salva);
        when(repositorio.participantes(conversa)).thenReturn(List.of(usuario));

        var useCase = new EnviarMidiaChatUseCase(repositorio, contexto, eventos, armazenamento, detector,
                limites, new ObjectMapper(), idempotencia);
        var resultado = useCase.executar(conversa, "capa.png", "Legenda\ncom acento", bytes, "chave-1");

        assertThat(resultado.id()).isEqualTo(mensagemId);
        verify(idempotencia).concluir("chave-1", mensagemId);
        verify(eventos).publishEvent(any(EventoDeChatInterno.MensagemEnviada.class));

        when(idempotencia.reservar(eq("chave-1"), eq(usuario), eq(conversa), any()))
                .thenReturn(new IdempotenciaDeMidiaChatRepositorio.Reserva(
                        "chave-1", usuario, conversa, "impressao", mensagemId, false));
        when(repositorio.mensagem(conversa, mensagemId)).thenReturn(Optional.of(salva));

        var repeticao = useCase.executar(conversa, "capa.png", "Legenda\ncom acento", bytes, "chave-1");

        assertThat(repeticao.id()).isEqualTo(mensagemId);
        verify(repositorio, Mockito.times(1)).salvarMensagemDeMidia(
                any(), any(), any(), any(), any(), any());
    }
}
