package com.synapse.crm.atendimento.infrastructure.tempo_real;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.event.TransactionalEventListenerFactory;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import com.synapse.crm.atendimento.application.painel.operacional.DestinatariosDoPainelOperacional;
import com.synapse.crm.equipe.application.permissao.ResolvedorDePermissoesEfetivas;
import com.synapse.crm.equipe.domain.permissao.ConfiguracaoDePermissoes;
import com.synapse.crm.equipe.domain.permissao.PoliticaDePermissoes;
import com.synapse.crm.equipe.domain.usuario.PresencaDoUsuarioAlterada;
import com.synapse.crm.sharedkernel.identidade.PapelUsuario;

/** Publicação pela infraestrutura real de eventos transacionais, nunca chamando só o listener. */
class RelayDePresencaOperacionalTest {
    @Test
    void publicaSomenteAposCommitESomenteParaCapacidadeEfetivaSemDadosDeTerceiros() throws Exception {
        UUID autor = UUID.randomUUID(), gestor = UUID.randomUUID();
        var destinos = mock(DestinatariosDoPainelOperacional.class);
        var permissoes = mock(ResolvedorDePermissoesEfetivas.class);
        var redis = mock(StringRedisTemplate.class);
        when(destinos.candidatosDaPresenca(autor)).thenReturn(List.of(gestor, autor));
        when(permissoes.de(gestor)).thenReturn(Optional.of(resolvido(PapelUsuario.GESTOR)));
        when(permissoes.de(autor)).thenReturn(Optional.of(resolvido(PapelUsuario.ATENDENTE)));
        var json = new ObjectMapper();
        try (var contexto = contexto(destinos, permissoes, redis, json)) {
            var transacao = new TransactionTemplate(new TransacaoDeTeste());
            transacao.executeWithoutResult(status -> {
                contexto.publishEvent(new PresencaDoUsuarioAlterada(autor));
                verifyNoInteractions(destinos, redis);
            });
            await().atMost(Duration.ofSeconds(3)).untilAsserted(() ->
                verify(redis).convertAndSend(eq(CanaisRedis.AVISO_USUARIO), anyString()));
            var corpo = org.mockito.ArgumentCaptor.forClass(String.class);
            verify(redis).convertAndSend(eq(CanaisRedis.AVISO_USUARIO), corpo.capture());
            var aviso = json.readTree(corpo.getValue());
            assertThat(aviso.path("usuarioId").asText()).isEqualTo(gestor.toString());
            assertThat(aviso.path("tipo").asText()).isEqualTo("PAINEL_OPERACIONAL_ALTERADO");
            assertThat(aviso.path("dados").size()).isZero();
            assertThat(corpo.getValue()).doesNotContain(autor.toString());
            // Aguarda o último candidato: prova que a negativa realmente foi processada.
            await().atMost(Duration.ofSeconds(3)).untilAsserted(() -> verify(permissoes).de(autor));
            verify(redis, times(1)).convertAndSend(anyString(), anyString());
        }
    }

    @Test
    void rollbackNaoPublicaNemConsultaDestinatarios() {
        var destinos = mock(DestinatariosDoPainelOperacional.class);
        var permissoes = mock(ResolvedorDePermissoesEfetivas.class);
        var redis = mock(StringRedisTemplate.class);
        try (var contexto = contexto(destinos, permissoes, redis, new ObjectMapper())) {
            new TransactionTemplate(new TransacaoDeTeste()).executeWithoutResult(status -> {
                contexto.publishEvent(new PresencaDoUsuarioAlterada(UUID.randomUUID()));
                status.setRollbackOnly();
            });
            verifyNoInteractions(destinos, permissoes, redis);
        }
    }

    private ResolvedorDePermissoesEfetivas.Resolvido resolvido(PapelUsuario papel) {
        return new ResolvedorDePermissoesEfetivas.Resolvido(papel, true,
            PoliticaDePermissoes.calcular(papel, ConfiguracaoDePermissoes.vazia(),
                ConfiguracaoDePermissoes.vazia(), Set.of()));
    }

    private AnnotationConfigApplicationContext contexto(DestinatariosDoPainelOperacional destinos,
            ResolvedorDePermissoesEfetivas permissoes, StringRedisTemplate redis, ObjectMapper json) {
        var contexto = new AnnotationConfigApplicationContext();
        contexto.registerBean(TransactionalEventListenerFactory.class);
        contexto.registerBean(RelayDePresencaOperacional.class, () -> new RelayDePresencaOperacional(
            destinos, permissoes, redis, json,
            new TempoRealProperties(0,0,0,0,null,0,0,0,null,0,0,0)));
        contexto.refresh();
        return contexto;
    }

    private static class TransacaoDeTeste extends AbstractPlatformTransactionManager {
        @Override protected Object doGetTransaction() { return new Object(); }
        @Override protected void doBegin(Object transaction, TransactionDefinition definition) {}
        @Override protected void doCommit(DefaultTransactionStatus status) {}
        @Override protected void doRollback(DefaultTransactionStatus status) {}
    }
}
