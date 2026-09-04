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
import com.synapse.crm.atendimento.infrastructure.midia.MidiaProperties;
import com.synapse.crm.sharedkernel.midia.ArmazenamentoDeMidia;

class RelayDeTempoRealListenerTest {
    @Test
    void transferencia_por_mensagem_revoga_o_dono_antigo_pelo_mesmo_canal() throws Exception {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        var listener = new RelayDeTempoRealListener(redis, new ObjectMapper(), mock(ArmazenamentoDeMidia.class), new MidiaProperties(null, null, null, null, null, null));
        UUID atendimento = UUID.randomUUID(); UUID lead = UUID.randomUUID(); UUID antigo = UUID.randomUUID(); UUID novo = UUID.randomUUID();
        listener.aoEnviarComTransferencia(new EventoDeAtendimento.MensagemEnviada(
                lead, "Lead de teste", atendimento, UUID.randomUUID(), novo, Optional.of(antigo), true,
                false, Instant.parse("2026-08-24T12:00:00Z")));

        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(redis).convertAndSend(eq(CanaisRedis.doAtendimento(atendimento)), payload.capture());
        JsonNode envelope = new ObjectMapper().readTree(payload.getValue());
        assertThat(envelope.path("tipo").asText()).isEqualTo("TRANSFERENCIA");
        assertThat(envelope.path("dados").path("deAtendenteId").asText()).isEqualTo(antigo.toString());
        assertThat(envelope.path("dados").path("paraAtendenteId").asText()).isEqualTo(novo.toString());
    }

    @Test
    void devolucao_para_ia_depois_do_commit_leva_atendimento_e_lead_sem_destino() throws Exception {
        var metodo = RelayDeTempoRealListener.class.getDeclaredMethod(
                "aoTransferir", EventoDeAtendimento.AtendimentoTransferido.class);
        assertThat(metodo.getAnnotation(org.springframework.transaction.event.TransactionalEventListener.class)
                .phase()).isEqualTo(org.springframework.transaction.event.TransactionPhase.AFTER_COMMIT);

        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        var listener = new RelayDeTempoRealListener(
                redis, new ObjectMapper(), mock(ArmazenamentoDeMidia.class),
                new MidiaProperties(null, null, null, null, null, null));
        UUID atendimento = UUID.randomUUID();
        UUID lead = UUID.randomUUID();
        UUID antigo = UUID.randomUUID();
        listener.aoTransferir(new EventoDeAtendimento.AtendimentoTransferido(
                lead,
                "Lead",
                atendimento,
                antigo,
                null,
                null,
                com.synapse.crm.core.domain.timeline.OrigemEvento.AUTOMACAO,
                Instant.parse("2026-09-04T15:00:00Z")));

        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(redis).convertAndSend(eq(CanaisRedis.doAtendimento(atendimento)), payload.capture());
        JsonNode envelope = new ObjectMapper().readTree(payload.getValue());
        assertThat(envelope.path("tipo").asText()).isEqualTo("TRANSFERENCIA");
        assertThat(envelope.path("dados").path("atendimentoId").asText()).isEqualTo(atendimento.toString());
        assertThat(envelope.path("dados").path("leadId").asText()).isEqualTo(lead.toString());
        assertThat(envelope.path("dados").path("deAtendenteId").asText()).isEqualTo(antigo.toString());
        assertThat(envelope.path("dados").path("paraAtendenteId").isNull()).isTrue();
    }

    @Test
    void reacao_depois_do_commit_leva_ator_sem_nomes_nem_lista_de_reatores() throws Exception {
        var metodo = RelayDeTempoRealListener.class.getDeclaredMethod(
                "aoReagir", com.synapse.crm.atendimento.domain.evento.ReacaoDaMensagemParaTempoReal.class);
        assertThat(metodo.getAnnotation(org.springframework.transaction.event.TransactionalEventListener.class)
                .phase()).isEqualTo(org.springframework.transaction.event.TransactionPhase.AFTER_COMMIT);

        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        var listener = new RelayDeTempoRealListener(
                redis, new ObjectMapper(), mock(ArmazenamentoDeMidia.class),
                new MidiaProperties(null, null, null, null, null, null));
        UUID atendimento = UUID.randomUUID();
        UUID mensagem = UUID.randomUUID();
        listener.aoReagir(new com.synapse.crm.atendimento.domain.evento.ReacaoDaMensagemParaTempoReal(
                atendimento,
                mensagem,
                Instant.parse("2026-08-28T15:00:00Z"),
                UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
                "👍",
                java.util.List.of(new com.synapse.crm.sharedkernel.emoji.ResumoDeReacao("👍", 2, true))));

        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(redis).convertAndSend(eq(CanaisRedis.doAtendimento(atendimento)), payload.capture());
        JsonNode envelope = new ObjectMapper().readTree(payload.getValue());
        assertThat(envelope.path("tipo").asText()).isEqualTo("REACAO");
        assertThat(envelope.path("dados").path("mensagemId").asText()).isEqualTo(mensagem.toString());
        assertThat(envelope.path("dados").path("atorId").asText()).isEqualTo("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        assertThat(envelope.path("dados").path("emojiDoAtor").asText()).isEqualTo("👍");
        assertThat(envelope.path("dados").path("reacoes").get(0).path("emoji").asText()).isEqualTo("👍");
        assertThat(envelope.path("dados").path("reacoes").get(0).path("quantidade").asInt()).isEqualTo(2);
        assertThat(envelope.path("dados").path("reacoes").get(0).has("reagi")).isFalse();
        assertThat(envelope.toString()).doesNotContain("reagi");
        assertThat(envelope.toString()).doesNotContain("remetenteNome");
        assertThat(envelope.path("dados").has("reatores")).isFalse();
    }

    @Test
    void redis_fora_nao_propaga_falha_da_reacao_ja_persistida() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        doThrow(new RuntimeException("redis down")).when(redis).convertAndSend(anyString(), anyString());
        var listener = new RelayDeTempoRealListener(
                redis, new ObjectMapper(), mock(ArmazenamentoDeMidia.class),
                new MidiaProperties(null, null, null, null, null, null));
        listener.aoReagir(new com.synapse.crm.atendimento.domain.evento.ReacaoDaMensagemParaTempoReal(
                UUID.randomUUID(),
                UUID.randomUUID(),
                Instant.parse("2026-08-28T15:00:00Z"),
                UUID.randomUUID(),
                null,
                java.util.List.of()));
    }
}
