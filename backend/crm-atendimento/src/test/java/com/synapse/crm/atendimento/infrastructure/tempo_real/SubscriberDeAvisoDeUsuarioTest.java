package com.synapse.crm.atendimento.infrastructure.tempo_real;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.Message;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import com.synapse.crm.sharedkernel.permissao.ConsultaDeAcessoAClientes;

class SubscriberDeAvisoDeUsuarioTest {
    @Test
    void bloqueiaAvisoOperacionalMasNaoPresencaPessoal() {
        var template = mock(SimpMessagingTemplate.class);
        var acesso = mock(ConsultaDeAcessoAClientes.class);
        var listener = new SubscriberDeAvisoDeUsuario(template, new ObjectMapper(), acesso);
        UUID usuario = UUID.randomUUID();
        var externo = mock(Message.class);
        when(externo.getBody()).thenReturn(("{\"usuarioId\":\"" + usuario + "\",\"tipo\":\"FINALIZACAO_EM_MASSA\"}").getBytes(StandardCharsets.UTF_8));
        listener.onMessage(externo, null);
        verifyNoInteractions(template);
        var presenca = mock(Message.class);
        when(presenca.getBody()).thenReturn(("{\"usuarioId\":\"" + usuario + "\",\"tipo\":\"PRESENCA_ALTERADA\"}").getBytes(StandardCharsets.UTF_8));
        listener.onMessage(presenca, null);
        verify(template).convertAndSendToUser(eq(usuario.toString()), eq("/queue/notificacoes"), contains("PRESENCA_ALTERADA"));
    }
}
