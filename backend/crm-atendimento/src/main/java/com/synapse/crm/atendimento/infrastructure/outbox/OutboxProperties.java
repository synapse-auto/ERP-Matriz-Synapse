package com.synapse.crm.atendimento.infrastructure.outbox;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Parametros do publisher. Nenhum numero fica no codigo (CLAUDE.md).
 *
 * @param lote quantas linhas por rodada; segura o consumo de conexao do job
 * @param maximoDeTentativas depois disso a linha esgota e vai para inspecao, nunca some
 * @param backoffInicial primeira espera; dobra a cada tentativa
 * @param backoffMaximo teto do backoff, para uma indisponibilidade longa nao empurrar a proxima
 *     tentativa para daqui a dias
 */
@ConfigurationProperties("synapse.canal.outbox")
public record OutboxProperties(
        int lote, int maximoDeTentativas, Duration backoffInicial, Duration backoffMaximo) {

    public OutboxProperties {
        lote = lote <= 0 ? 50 : lote;
        maximoDeTentativas = maximoDeTentativas <= 0 ? 8 : maximoDeTentativas;
        backoffInicial = backoffInicial == null ? Duration.ofSeconds(5) : backoffInicial;
        backoffMaximo = backoffMaximo == null ? Duration.ofMinutes(30) : backoffMaximo;
    }

    /**
     * Backoff exponencial a partir do numero de tentativas ja feitas, limitado pelo teto.
     *
     * <p>Sem backoff, uma linha que falha em sequencia seria retentada em loop apertado — o CRM
     * viraria um ataque ao proprio provedor, e a quota da conta acabaria por causa de uma mensagem
     * so.
     */
    public Duration esperaApos(int tentativas) {
        Duration espera = backoffInicial.multipliedBy(1L << Math.min(tentativas, 20));
        return espera.compareTo(backoffMaximo) > 0 ? backoffMaximo : espera;
    }
}
