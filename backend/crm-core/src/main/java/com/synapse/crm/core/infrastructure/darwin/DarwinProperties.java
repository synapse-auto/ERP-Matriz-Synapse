package com.synapse.crm.core.infrastructure.darwin;

import java.net.URI;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** O recurso ausente/incompleto não impede o boot do CRM. Segredo só no ambiente do backend. */
@ConfigurationProperties("synapse.integracoes.darwin")
public record DarwinProperties(
        @DefaultValue("false") boolean habilitado,
        @DefaultValue("https://developers.darwin.med.br") URI url,
        @DefaultValue("") String token,
        @DefaultValue("2") int concorrencia,
        @DefaultValue("2s") Duration timeoutConexao,
        @DefaultValue("10s") Duration timeoutConsulta,
        @DefaultValue("5m") Duration validadePrevia,
        @DefaultValue("524288") int maxRespostaBytes,
        @DefaultValue("5") int circuitoMinimo,
        @DefaultValue("50") float circuitoLimiar,
        @DefaultValue("30s") Duration circuitoEspera) {
    boolean pronta() {
        return token != null && !token.isBlank() && token.chars().noneMatch(Character::isISOControl) && concorrencia > 0
                && timeoutConexao != null && timeoutConexao.isPositive() && timeoutConsulta != null && timeoutConsulta.isPositive()
                && validadePrevia != null && validadePrevia.isPositive() && maxRespostaBytes > 0 && circuitoMinimo > 0
                && circuitoLimiar > 0 && circuitoLimiar <= 100 && circuitoEspera != null && circuitoEspera.isPositive()
                && url != null && url.getRawUserInfo() == null && url.getRawQuery() == null && url.getRawFragment() == null
                && url.getPath() != null && (url.getPath().isEmpty() || url.getPath().equals("/"))
                && (("https".equals(url.getScheme()) && "developers.darwin.med.br".equals(url.getHost()) && url.getPort() == -1)
                    || ("http".equals(url.getScheme()) && ("127.0.0.1".equals(url.getHost()) || "localhost".equals(url.getHost()))));
    }
    @Override public String toString() { return "DarwinProperties[redigido]"; }
}
