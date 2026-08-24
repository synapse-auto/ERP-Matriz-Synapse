package com.synapse.crm.atendimento.infrastructure.tempo_real;

import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.Message;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import com.synapse.crm.atendimento.application.tempo_real.RevalidarAssinaturaTempoRealUseCase;
import com.synapse.crm.sharedkernel.identidade.PapelUsuario;

class RedisSubscriberDeAtendimentoTest {

    private final SimpMessagingTemplate template = mock(SimpMessagingTemplate.class);
    private final RevalidarAssinaturaTempoRealUseCase revalidar = mock(RevalidarAssinaturaTempoRealUseCase.class);
    private final RegistroDeAssinaturas registro = new RegistroDeAssinaturas(
            Clock.fixed(Instant.parse("2026-08-23T12:00:00Z"), ZoneOffset.UTC),
            new TempoRealProperties(1, 1, 1, 1, "*", 60));
    private final RedisSubscriberDeAtendimento subscriber = new RedisSubscriberDeAtendimento(
            registro, template, new ObjectMapper(), revalidar);

    private final UUID atendimentoId = UUID.randomUUID();
    private final UUID transferidorId = UUID.randomUUID();
    private final UUID destinatarioId = UUID.randomUUID();
    private final UUID donoAnteriorId = UUID.randomUUID();

    @BeforeEach
    void limparAssinaturas() {
        registro.doAtendimento(atendimentoId).forEach(registro::remover);
        reset(template);
    }

    @Test
    void entrega_aviso_ao_destinatario_e_nao_ao_transferidor() {
        subscriber.onMessage(mensagem(transferencia(destinatarioId, transferidorId, "USUARIO")), null);

        verify(template).convertAndSendToUser(
                eq(destinatarioId.toString()), eq(RedisSubscriberDeAtendimento.DESTINO_NOTIFICACOES), contains("TRANSFERENCIA_RECEBIDA"));
        verify(template, never()).convertAndSendToUser(
                eq(transferidorId.toString()), eq(RedisSubscriberDeAtendimento.DESTINO_NOTIFICACOES), contains("TRANSFERENCIA_RECEBIDA"));
    }

    @Test
    void transferencia_para_ia_nao_entrega_aviso() {
        subscriber.onMessage(mensagem(transferencia(null, transferidorId, "USUARIO")), null);

        verify(template, never()).convertAndSendToUser(
                org.mockito.ArgumentMatchers.anyString(), eq(RedisSubscriberDeAtendimento.DESTINO_NOTIFICACOES), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void transferencia_para_ia_avisa_o_dono_anterior_no_canal_pessoal() {
        subscriber.onMessage(mensagem(transferenciaParaIa(donoAnteriorId)), null);

        verify(template).convertAndSendToUser(
                eq(donoAnteriorId.toString()), eq(RedisSubscriberDeAtendimento.DESTINO_NOTIFICACOES),
                contains("ATENDIMENTO_DEVOLVIDO_PARA_IA"));
    }

    @Test
    void automacao_entrega_aviso_com_a_mesma_rota() {
        subscriber.onMessage(mensagem(transferencia(destinatarioId, null, "AUTOMACAO")), null);

        verify(template).convertAndSendToUser(
                eq(destinatarioId.toString()), eq(RedisSubscriberDeAtendimento.DESTINO_NOTIFICACOES), contains("TRANSFERENCIA_RECEBIDA"));
    }

    @Test
    void chat_interno_entrega_apenas_aos_participantes_destinatarios() {
        String corpo = "{\"tipo\":\"CHAT_INTERNO_MENSAGEM\",\"destinatarios\":[\"" + destinatarioId
                + "\"],\"conversaId\":\"" + UUID.randomUUID() + "\",\"mensagemId\":\""
                + UUID.randomUUID() + "\",\"remetenteId\":\"" + transferidorId
                + "\",\"conteudo\":\"texto\",\"enviadoEm\":\"2026-08-23T12:00:00Z\"}";
        Message mensagem = mock(Message.class);
        org.mockito.Mockito.when(mensagem.getChannel()).thenReturn(
                ("synapse:chat-interno:" + UUID.randomUUID()).getBytes(StandardCharsets.UTF_8));
        org.mockito.Mockito.when(mensagem.getBody()).thenReturn(corpo.getBytes(StandardCharsets.UTF_8));

        subscriber.onMessage(mensagem, null);

        verify(template).convertAndSendToUser(eq(destinatarioId.toString()),
                eq(RedisSubscriberDeAtendimento.DESTINO_NOTIFICACOES), contains("CHAT_INTERNO_MENSAGEM"));
        verify(template, never()).convertAndSendToUser(eq(transferidorId.toString()),
                eq(RedisSubscriberDeAtendimento.DESTINO_NOTIFICACOES), contains("CHAT_INTERNO_MENSAGEM"));
    }

    @Test
    void revogacao_do_dono_anterior_continua_sendo_entregue() {
        registro.registrar(new AssinaturaAutorizada(
                "sessao", "sub", atendimentoId, donoAnteriorId, PapelUsuario.ATENDENTE));

        subscriber.onMessage(mensagem(transferencia(destinatarioId, transferidorId, "USUARIO")), null);

        verify(template).convertAndSendToUser(
                eq(donoAnteriorId.toString()), eq("/queue/revogacoes"), contains(atendimentoId.toString()));
    }

    private Message mensagem(String corpo) {
        Message mensagem = mock(Message.class);
        org.mockito.Mockito.when(mensagem.getChannel())
                .thenReturn(CanaisRedis.doAtendimento(atendimentoId).getBytes(StandardCharsets.UTF_8));
        org.mockito.Mockito.when(mensagem.getBody()).thenReturn(corpo.getBytes(StandardCharsets.UTF_8));
        return mensagem;
    }

    private String transferencia(UUID para, UUID ator, String atorTipo) {
        String paraJson = para == null ? "null" : "\"" + para + "\"";
        String atorJson = ator == null ? "null" : "\"" + ator + "\"";
        return "{\"tipo\":\"TRANSFERENCIA\",\"dados\":{"
                + "\"atendimentoId\":\"" + atendimentoId + "\","
                + "\"leadId\":\"" + UUID.randomUUID() + "\","
                + "\"leadNome\":\"Lead de teste\","
                + "\"paraAtendenteId\":" + paraJson + ","
                + "\"quemTransferiu\":" + atorJson + ","
                + "\"atorTipo\":\"" + atorTipo + "\","
                + "\"ocorridoEm\":\"2026-08-23T12:00:00Z\"}}";
    }

    private String transferenciaParaIa(UUID de) {
        return "{\"tipo\":\"TRANSFERENCIA\",\"dados\":{"
                + "\"atendimentoId\":\"" + atendimentoId + "\","
                + "\"leadId\":\"" + UUID.randomUUID() + "\","
                + "\"leadNome\":\"Lead de teste\","
                + "\"deAtendenteId\":\"" + de + "\","
                + "\"paraAtendenteId\":null,"
                + "\"quemTransferiu\":null,"
                + "\"atorTipo\":\"SISTEMA\","
                + "\"ocorridoEm\":\"2026-08-23T12:00:00Z\"}}";
    }
}
