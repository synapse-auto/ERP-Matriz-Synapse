package com.synapse.crm.equipe.infrastructure.permissao;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

import com.synapse.crm.equipe.application.permissao.PoliticaDeRevalidacao;

/**
 * {@code synapse.permissoes.revalidacao}: atraso maximo para uma alteracao salva em outro no valer
 * aqui. Opcional ({@code SYNAPSE_PERMISSOES_REVALIDACAO}); default no application.yml (2s). Valor
 * ausente ou nao positivo cai em 2s — nunca em "cache eterno".
 */
@ConfigurationProperties(prefix = "synapse.permissoes")
public record PermissoesProperties(Duration revalidacao) implements PoliticaDeRevalidacao {

    private static final Duration PADRAO = Duration.ofSeconds(2);

    public PermissoesProperties {
        if (revalidacao == null || revalidacao.isNegative() || revalidacao.isZero()) {
            revalidacao = PADRAO;
        }
    }

    @Override
    public Duration intervaloDeRevalidacao() {
        return revalidacao;
    }
}
