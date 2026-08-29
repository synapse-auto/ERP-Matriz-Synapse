package com.synapse.crm.equipe.infrastructure.tempo_real;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.synapse.crm.equipe.application.chat.EventoDeChatInterno;
import com.synapse.crm.sharedkernel.emoji.ResumoDeReacao;

class RelayDeChatInternoTest {

    @Test
    void reacao_soDepoisDoCommit() throws Exception {
        var metodo = RelayDeChatInterno.class.getDeclaredMethod(
                "publicarReacao", EventoDeChatInterno.ReacaoAlterada.class);
        assertThat(metodo.getAnnotation(TransactionalEventListener.class).phase())
                .isEqualTo(TransactionPhase.AFTER_COMMIT);
    }

    @Test
    void redis_fora_nao_propaga_falha_da_reacao_ja_persistida() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        doThrow(new RuntimeException("redis down")).when(redis).convertAndSend(anyString(), anyString());
        var relay = new RelayDeChatInterno(redis, new ObjectMapper().findAndRegisterModules());
        relay.publicarReacao(new EventoDeChatInterno.ReacaoAlterada(
                UUID.randomUUID(),
                UUID.randomUUID(),
                List.of(UUID.randomUUID()),
                List.of(new ResumoDeReacao("👍", 1, true))));
        verify(redis).convertAndSend(anyString(), anyString());
    }

    @Test
    void mensagem_continua_depois_do_commit() throws Exception {
        var metodo = RelayDeChatInterno.class.getDeclaredMethod(
                "publicar", EventoDeChatInterno.MensagemEnviada.class);
        assertThat(metodo.getAnnotation(TransactionalEventListener.class).phase())
                .isEqualTo(TransactionPhase.AFTER_COMMIT);
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        var relay = new RelayDeChatInterno(redis, new ObjectMapper().findAndRegisterModules());
        UUID conversa = UUID.randomUUID();
        relay.publicar(new EventoDeChatInterno.MensagemEnviada(
                conversa, UUID.randomUUID(), UUID.randomUUID(), List.of(UUID.randomUUID()),
                "oi", Instant.parse("2026-08-28T15:00:00Z")));
        verify(redis).convertAndSend(org.mockito.ArgumentMatchers.eq("synapse:chat-interno:" + conversa), anyString());
    }
}
