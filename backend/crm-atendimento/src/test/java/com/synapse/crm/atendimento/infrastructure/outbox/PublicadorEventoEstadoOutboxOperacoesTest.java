package com.synapse.crm.atendimento.infrastructure.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.synapse.crm.atendimento.domain.evento.EventoCanonicoDeAtendimento;
import com.synapse.crm.atendimento.infrastructure.tempo_real.CanaisRedis;

class PublicadorEventoEstadoOutboxOperacoesTest {

    private static final Instant AGORA = Instant.parse("2026-09-12T12:00:00Z");

    @Test
    void evento_e_persistido_na_outbox_antes_do_commit() throws Exception {
        var metodo = PersistidorEventoEstadoOutbox.class.getDeclaredMethod(
                "persistir", EventoCanonicoDeAtendimento.class);
        assertThat(metodo.getAnnotation(TransactionalEventListener.class).phase())
                .isEqualTo(TransactionPhase.BEFORE_COMMIT);
        OutboxEventoEstadoRepositorioJdbc outbox = mock(OutboxEventoEstadoRepositorioJdbc.class);
        EventoCanonicoDeAtendimento evento = evento();

        new PersistidorEventoEstadoOutbox(outbox).persistir(evento);

        verify(outbox).enfileirar(evento);
    }

    @Test
    void worker_publica_payload_minimo_versionado_e_marca_sucesso() throws Exception {
        PublicadorEventoEstadoOutboxTransacoes transacoes = mock(PublicadorEventoEstadoOutboxTransacoes.class);
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ObjectMapper json = new ObjectMapper();
        EventoCanonicoDeAtendimento evento = evento();
        var pendente = new OutboxEventoEstadoRepositorioJdbc.Pendente(evento.eventoId(), evento, 0);
        when(transacoes.reservar(AGORA)).thenReturn(List.of(pendente));

        int processados = new PublicadorEventoEstadoOutboxOperacoes(
                transacoes, redis, json, Clock.fixed(AGORA, ZoneOffset.UTC)).rodada();

        assertThat(processados).isOne();
        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(redis).convertAndSend(eq(CanaisRedis.doAtendimento(evento.atendimentoId())), payload.capture());
        verify(transacoes).registrarSucesso(pendente, AGORA);
        JsonNode envelope = json.readTree(payload.getValue());
        assertThat(envelope.path("tipo").asText()).isEqualTo("ATENDIMENTO_ESTADO");
        assertThat(envelope.path("contrato").asText()).isEqualTo("atendimento.estado.v1");
        assertThat(envelope.path("eventoId").asText()).isEqualTo(evento.eventoId().toString());
        assertThat(envelope.path("versaoContrato").asInt()).isOne();
        assertThat(envelope.path("dados").fieldNames()).toIterable()
                .containsExactlyInAnyOrder("atendimentoId", "leadId", "eventoTipo", "versao", "ocorridoEm");
        assertThat(envelope.toString())
                .doesNotContain("telefone", "conteudo", "token", "midia", "destinatarios");
    }

    @Test
    void redis_indisponivel_reagenda_sem_marcar_publicado() {
        PublicadorEventoEstadoOutboxTransacoes transacoes = mock(PublicadorEventoEstadoOutboxTransacoes.class);
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        EventoCanonicoDeAtendimento evento = evento();
        var pendente = new OutboxEventoEstadoRepositorioJdbc.Pendente(evento.eventoId(), evento, 0);
        when(transacoes.reservar(AGORA)).thenReturn(List.of(pendente));
        doThrow(new RuntimeException("redis indisponivel"))
                .when(redis).convertAndSend(any(String.class), any(String.class));

        new PublicadorEventoEstadoOutboxOperacoes(
                transacoes, redis, new ObjectMapper(), Clock.fixed(AGORA, ZoneOffset.UTC)).rodada();

        verify(transacoes).registrarFalha(eq(pendente), eq(AGORA), any(String.class));
        verify(transacoes, never()).registrarSucesso(any(), any());
    }

    private static EventoCanonicoDeAtendimento evento() {
        return new EventoCanonicoDeAtendimento(
                UUID.randomUUID(),
                1,
                EventoCanonicoDeAtendimento.Tipo.ATENDIMENTO_TRANSFERIDO,
                UUID.randomUUID(),
                UUID.randomUUID(),
                7,
                AGORA);
    }
}
