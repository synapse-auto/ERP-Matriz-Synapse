package com.synapse.crm.atendimento.application.proativo;

import java.util.Set;

import com.synapse.crm.atendimento.application.origem.TipoDeOrigem;

/**
 * Parametros de frequencia das mensagens proativas, por instancia. A implementacao le
 * {@code configuracao_automacao} (modulo de automacao), editavel em tempo de execucao.
 */
public interface PoliticaDeEnvioProativo {

    Politica vigente();

    /**
     * @param cooldownHoras intervalo minimo entre duas proativas do mesmo tipo ao mesmo lead; 0 desliga
     * @param tetoDiarioPorLead maximo de proativas por lead por dia (fuso da instancia); 0 desliga
     */
    record Politica(boolean habilitada, Set<TipoDeOrigem> tiposDesligados, int cooldownHoras, int tetoDiarioPorLead) {

        public Politica {
            tiposDesligados = Set.copyOf(tiposDesligados);
        }

        /** O comportamento de antes da E219: nada bloqueia. */
        public static Politica semRestricao() {
            return new Politica(true, Set.of(), 0, 0);
        }
    }
}
