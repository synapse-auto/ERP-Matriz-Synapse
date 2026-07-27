package com.synapse.crm.sharedkernel.identidade;

import java.util.Optional;

/**
 * Acesso ao usuario da requisicao corrente.
 *
 * <p>Porta pura: a implementacao que le o Spring Security mora em crm-equipe/infrastructure. Manter
 * a interface aqui e o que permite crm-core consultar quem esta pedindo sem depender de crm-equipe
 * nem de framework.
 */
public interface UsuarioContext {

    /**
     * @return o usuario da requisicao corrente
     * @throws IllegalStateException se nao houver requisicao autenticada — nunca retorna null, para
     *     que "sem usuario" falhe alto em vez de virar consulta sem filtro
     */
    UsuarioAutenticado atual();

    /**
     * Variante que nao lanca, para quem precisa decidir o que fazer quando nao ha usuario.
     *
     * <p>Existe para o aplicador de contexto RLS: ele roda no inicio de TODA transacao, inclusive as
     * do proprio login, quando ainda nao existe usuario autenticado. Ali "nao tem ninguem" e
     * informacao, nao erro — e vira transacao sem contexto, que o banco nega por padrao.
     *
     * <p>Casos de uso continuam usando {@link #atual()}: para eles, seguir sem usuario e sempre bug.
     */
    Optional<UsuarioAutenticado> atualSeHouver();
}
