package com.synapse.crm.campanhas.application;

import java.util.UUID;

import com.synapse.crm.campanhas.domain.PoliticaDePausa;

/**
 * Parametros de campanhas guardados em {@code configuracao_automacao} (V88). Lidos a cada ciclo: o que o
 * administrador muda vale no ciclo seguinte, sem deploy e sem reiniciar.
 */
public interface ConfiguracaoDeCampanhas {

    Parametros atuais();

    /** Campos nulos ficam como estao. Valor fora da faixa semeada na migration e recusado. */
    void atualizar(Atualizacao atualizacao, UUID usuarioId);

    /**
     * @param envioHabilitado interruptor global; desligado, nenhum ciclo envia
     * @param tetoDiarioDaInstancia maximo por dia somando todas as campanhas
     * @param limiteMetaInformado contatos unicos por 24h que a Meta concede, informado a mao; 0 = nao informado
     * @param cooldownProativoHoras cooldown da politica proativa (E219), usado para excluir do publico
     */
    record Parametros(
            boolean envioHabilitado,
            int tetoDiarioDaInstancia,
            int limiteDiarioPadrao,
            int limiteMetaInformado,
            PoliticaDePausa politicaDePausa,
            int conferenciaAposMinutos,
            int respondeuJanelaDias,
            int cooldownProativoHoras) {}

    record Atualizacao(
            Boolean envioHabilitado,
            Integer tetoDiarioDaInstancia,
            Integer limiteDiarioPadrao,
            Integer limiteMetaInformado,
            Integer limiarDeFalhaPorCento,
            Integer janelaDeEnvios,
            Integer minimoDeAmostra) {}
}
