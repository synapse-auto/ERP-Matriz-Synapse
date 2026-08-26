package com.synapse.crm.atendimento.infrastructure.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import com.synapse.crm.atendimento.application.MensagemRepositorio;
import com.synapse.crm.atendimento.application.Outbox;
import com.synapse.crm.atendimento.domain.canal.CanalGateway;
import com.synapse.crm.atendimento.domain.canal.ConteudoDeEnvio;
import com.synapse.crm.atendimento.domain.canal.ResultadoDeEnvio;
import com.synapse.crm.sharedkernel.identidade.ContextoDeServico;

class PublicadorDaOutboxOperacoesTest {

    private static final Instant AGORA = Instant.parse("2026-08-25T12:00:00Z");
    private static final Clock RELOGIO = Clock.fixed(AGORA, ZoneOffset.UTC);

    @BeforeEach
    void instalarAutoridadeDeTeste() {
        ContextoDeServico.instalarPonteDeAutoridade(nome -> () -> {});
    }

    @Test
    void linhaLentaNaoBloqueiaOutraDoMesmoLote() throws Exception {
        PublicadorDaOutboxTransacoes transacoes = mock(PublicadorDaOutboxTransacoes.class);
        CanalGateway canal = mock(CanalGateway.class);
        Outbox.EnvioPendente lenta = pendente();
        Outbox.EnvioPendente rapida = pendente();
        CountDownLatch iniciouRapida = new CountDownLatch(1);
        CountDownLatch liberaLenta = new CountDownLatch(1);

        when(transacoes.reservar(AGORA)).thenReturn(List.of(lenta, rapida));
        when(canal.provedor()).thenReturn("teste");
        when(canal.enviar(any())).thenAnswer(invocacao -> {
            CanalGateway.Envio envio = invocacao.getArgument(0);
            if (envio.mensagemId().equals(lenta.mensagemId())) {
                assertThat(liberaLenta.await(2, TimeUnit.SECONDS)).isTrue();
            } else {
                iniciouRapida.countDown();
            }
            return new ResultadoDeEnvio.Aceito("externo-" + envio.mensagemId());
        });

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            PublicadorDaOutboxOperacoes operacoes = new PublicadorDaOutboxOperacoes(
                    transacoes,
                    canal,
                    RELOGIO,
                    executor);
            var rodada = CompletableFuture.runAsync(operacoes::rodada);

            assertThat(iniciouRapida.await(1, TimeUnit.SECONDS))
                    .as("a segunda chamada deve começar enquanto a primeira esta lenta")
                    .isTrue();
            liberaLenta.countDown();
            rodada.get(2, TimeUnit.SECONDS);
            verify(transacoes, org.mockito.Mockito.times(2))
                    .registrarResultado(any(), any(), any());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void reservaPersistidaRecebeExpiracaoConfigurada() {
        Outbox outbox = mock(Outbox.class);
        OutboxProperties propriedades = propriedades();
        when(outbox.reservarPendentes(2, AGORA, AGORA.plus(Duration.ofSeconds(30))))
                .thenReturn(List.of());
        PublicadorDaOutboxTransacoes transacoes = new PublicadorDaOutboxTransacoes(
                outbox,
                mock(MensagemRepositorio.class),
                mock(CanalGateway.class),
                propriedades,
                mock(ApplicationEventPublisher.class));

        assertThat(transacoes.reservar(AGORA)).isEmpty();
        verify(outbox).reservarPendentes(2, AGORA, AGORA.plus(Duration.ofSeconds(30)));
    }

    @Test
    void recusaTemporariaReagendaERecusaPermanenteEsgota() {
        Outbox outbox = mock(Outbox.class);
        MensagemRepositorio mensagens = mock(MensagemRepositorio.class);
        CanalGateway canal = mock(CanalGateway.class);
        ApplicationEventPublisher eventos = mock(ApplicationEventPublisher.class);
        OutboxProperties propriedades = propriedades();
        PublicadorDaOutboxTransacoes transacoes = new PublicadorDaOutboxTransacoes(
                outbox, mensagens, canal, propriedades, eventos);
        Outbox.EnvioPendente pendente = pendente();

        transacoes.registrarResultado(pendente, ResultadoDeEnvio.Recusado.temporario("429"), AGORA);
        verify(outbox).reagendar(
                pendente.outboxId(), AGORA.plus(Duration.ofSeconds(5)), "429");

        transacoes.registrarResultado(pendente, ResultadoDeEnvio.Recusado.permanente("numero invalido"), AGORA);
        verify(outbox).esgotar(pendente.outboxId(), AGORA, "numero invalido");
        verify(mensagens).atualizarStatusEntrega(
                pendente.mensagemId(), pendente.enviadoEm(), com.synapse.crm.atendimento.domain.mensagem.StatusEntrega.FALHOU);
    }

    private static OutboxProperties propriedades() {
        return new OutboxProperties(2, 3, Duration.ofSeconds(5), Duration.ofMinutes(30), 2, Duration.ofSeconds(30));
    }

    private static Outbox.EnvioPendente pendente() {
        return new Outbox.EnvioPendente(
                UUID.randomUUID(),
                UUID.randomUUID(),
                AGORA,
                UUID.randomUUID(),
                UUID.randomUUID(),
                "5561999999999",
                UUID.randomUUID(),
                new ConteudoDeEnvio.MensagemLivre("ola"),
                0);
    }
}
