package com.synapse.crm.atendimento.infrastructure.tempo_real;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.synapse.crm.atendimento.domain.evento.InformacoesDoChatbotParaTempoReal;
import com.synapse.crm.atendimento.infrastructure.midia.MidiaProperties;
import com.synapse.crm.sharedkernel.midia.ArmazenamentoDeMidia;

class RelayInformacoesDoChatbotTest {

    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    private final RelayDeTempoRealListener relay = new RelayDeTempoRealListener(
            redis, new ObjectMapper(), mock(ArmazenamentoDeMidia.class),
            new MidiaProperties(null, null, null, null, null, null));

    @Test
    @DisplayName("publica INFORMACOES_CHATBOT no canal do atendimento, so com ids e instante")
    void publicaAvisoLeve() throws Exception {
        UUID atendimento = UUID.randomUUID();
        UUID lead = UUID.randomUUID();
        UUID informacao = UUID.randomUUID();

        relay.aoRegistrarInformacoesDoChatbot(new InformacoesDoChatbotParaTempoReal(
                atendimento, lead, informacao, Instant.parse("2026-10-08T14:00:00Z")));

        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(redis).convertAndSend(eq(CanaisRedis.doAtendimento(atendimento)), payload.capture());
        JsonNode envelope = new ObjectMapper().readTree(payload.getValue());
        assertThat(envelope.path("tipo").asText()).isEqualTo("INFORMACOES_CHATBOT");
        JsonNode dados = envelope.path("dados");
        assertThat(dados.path("atendimentoId").asText()).isEqualTo(atendimento.toString());
        assertThat(dados.path("leadId").asText()).isEqualTo(lead.toString());
        assertThat(dados.path("informacaoId").asText()).isEqualTo(informacao.toString());
        assertThat(dados.path("eventoId").asText()).isEqualTo(informacao.toString());
        assertThat(dados.has("conteudo")).isFalse();
        assertThat(dados.has("destinatarios")).isFalse();
    }

    @Test
    @DisplayName("so sai depois do commit: transacao revertida nao deixa aviso para um card que nao existe")
    void publicaSomenteAposOCommit() throws Exception {
        var metodo = RelayDeTempoRealListener.class.getDeclaredMethod(
                "aoRegistrarInformacoesDoChatbot", InformacoesDoChatbotParaTempoReal.class);

        assertThat(metodo.getAnnotation(TransactionalEventListener.class).phase())
                .isEqualTo(TransactionPhase.AFTER_COMMIT);
    }
}
