package com.synapse.crm.sharedkernel.identidade;

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
}
