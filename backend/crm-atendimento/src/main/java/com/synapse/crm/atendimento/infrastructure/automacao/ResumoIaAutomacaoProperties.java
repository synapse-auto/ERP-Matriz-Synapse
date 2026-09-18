package com.synapse.crm.atendimento.infrastructure.automacao;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Configuração por instância do webhook de resumo; vazio mantém o recurso desligado. */
@ConfigurationProperties("synapse.automacao.resumo-ia")
public record ResumoIaAutomacaoProperties(
        String url,
        String token,
        Duration timeout,
        int lote,
        int maximoDeTentativas,
        Duration backoffInicial,
        Duration backoffMaximo,
        Duration reservaExpiracao,
        long intervaloMs) {

    public ResumoIaAutomacaoProperties {
        url = url == null ? "" : url.trim();
        token = token == null ? "" : token.trim();
        timeout = timeout == null ? Duration.ofSeconds(5) : timeout;
        lote = lote <= 0 ? 10 : lote;
        maximoDeTentativas = maximoDeTentativas <= 0 ? 5 : maximoDeTentativas;
        backoffInicial = backoffInicial == null ? Duration.ofSeconds(5) : backoffInicial;
        backoffMaximo = backoffMaximo == null ? Duration.ofMinutes(10) : backoffMaximo;
        reservaExpiracao = reservaExpiracao == null ? Duration.ofSeconds(30) : reservaExpiracao;
        intervaloMs = intervaloMs <= 0 ? 1000 : intervaloMs;
    }

    public boolean configurado() {
        return !url.isBlank() && !token.isBlank();
    }

    public Duration esperaApos(int tentativas) {
        Duration espera = backoffInicial.multipliedBy(1L << Math.min(tentativas, 20));
        return espera.compareTo(backoffMaximo) > 0 ? backoffMaximo : espera;
    }
}
