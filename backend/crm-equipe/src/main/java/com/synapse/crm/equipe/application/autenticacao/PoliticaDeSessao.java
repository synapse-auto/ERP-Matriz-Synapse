package com.synapse.crm.equipe.application.autenticacao;

import java.time.Duration;

/**
 * Porta de configuracao da sessao.
 *
 * <p>Existe para que os casos de uso leiam a validade do refresh token sem importar
 * {@code @ConfigurationProperties} de infrastructure — o que quebraria a regra de dependencia.
 */
public interface PoliticaDeSessao {

    Duration validadeDoRefreshToken();
}
