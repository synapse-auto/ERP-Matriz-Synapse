package com.synapse.crm.atendimento.infrastructure.tempo_real;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.synapse.crm.atendimento.domain.evento.EventoDeAtendimento;
import com.synapse.crm.atendimento.domain.midia.ArmazenamentoDeMidia;
import com.synapse.crm.atendimento.infrastructure.midia.MidiaProperties;

class RelayDeTempoRealListenerTest {
    @Test
    void transferencia_por_mensagem_revoga_o_dono_antigo_pelo_mesmo_canal() throws Exception {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        var listener = new RelayDeTempoRealListener(redis, new ObjectMapper(), mock(ArmazenamentoDeMidia.class), new MidiaProperties(null, null, null, null, null, null));
        UUID atendimento = UUID.randomUUID(); UUID lead = UUID.randomUUID(); UUID antigo = UUID.randomUUID(); UUID novo = UUID.randomUUID();
        listener.aoEnviarComTransferencia(new EventoDeAtendimento.MensagemEnviada(
                lead, "Lead de teste", atendimento, UUID.randomUUID(), novo, Optional.of(antigo), true,
                Instant.parse("2026-08-24T12:00:00Z")));

        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(redis).convertAndSend(eq(CanaisRedis.doAtendimento(atendimento)), payload.capture());
        JsonNode envelope = new ObjectMapper().readTree(payload.getValue());
        assertThat(envelope.path("tipo").asText()).isEqualTo("TRANSFERENCIA");
        assertThat(envelope.path("dados").path("deAtendenteId").asText()).isEqualTo(antigo.toString());
        assertThat(envelope.path("dados").path("paraAtendenteId").asText()).isEqualTo(novo.toString());
    }
}
