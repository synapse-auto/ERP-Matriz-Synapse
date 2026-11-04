package com.synapse.crm.atendimento.infrastructure.canal;

import java.net.URI;
import java.time.Duration;
import java.util.UUID;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Segredos separados para saida e callback; nenhum valor e retornado ao navegador. */
@ConfigurationProperties("synapse.canal.evolution")
public record EvolutionProperties(boolean habilitado, String urlBase, String instancia, String apiKey,
        UUID credencialId, String segredoWebhook, Duration timeout, int concorrencia,
        int limiteCorpoBytes, int limiteRespostaBytes, boolean permitirDestinoPrivado,
        boolean repasseAutomacaoHabilitado) {
    public void validar() {
        try {
            URI u = URI.create(urlBase);
            if (!"https".equals(u.getScheme()) || u.getHost() == null || u.getUserInfo()!=null
                    || u.getRawQuery()!=null || u.getFragment()!=null || !u.getPath().matches("/?")) throw new IllegalArgumentException();
            if (instancia==null || !instancia.matches("[A-Za-z0-9_-]{1,120}") || apiKey==null || apiKey.isBlank()
                    || apiKey.chars().anyMatch(Character::isISOControl) || credencialId==null
                    || segredoWebhook==null || !segredoWebhook.matches("[A-Za-z0-9_-]{43,}")
                    || segredoWebhook.equals(apiKey) || timeout==null || !timeout.isPositive()
                    || concorrencia<1 || limiteCorpoBytes<1 || limiteCorpoBytes==Integer.MAX_VALUE
                    || limiteRespostaBytes<1 || limiteRespostaBytes==Integer.MAX_VALUE) throw new IllegalArgumentException();
        } catch (RuntimeException e) { throw new IllegalArgumentException("EVOLUTION_CONFIGURACAO_INVALIDA"); }
    }
    @Override public String toString() { return "EvolutionProperties[redigido]"; }
}
