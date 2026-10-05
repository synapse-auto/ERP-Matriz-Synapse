package com.synapse.crm.atendimento.application.finalizacaomassa;

/**
 * Limites da operacao, lidos de {@code configuracao_automacao} a cada pedido (sem deploy). Porta: quem a
 * implementa e a aplicacao executavel, que enxerga a configuracao; este modulo nao.
 */
public interface ParametrosDeFinalizacaoEmMassa {

    /** Maximo de atendimentos que uma operacao pode congelar. */
    int limitePorOperacao();

    /** Maior janela (datas inclusivas) aceita. */
    int periodoMaximoEmDias();

    /** Quantos itens o worker processa por rodada. */
    int tamanhoDoLote();
}
