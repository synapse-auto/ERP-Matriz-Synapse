package com.synapse.crm.app.atendimento;

import org.springframework.stereotype.Component;

import com.synapse.crm.atendimento.application.finalizacaomassa.ParametrosDeFinalizacaoEmMassa;
import com.synapse.crm.automacaoconfig.application.ConfiguracaoAutomacaoRepositorio;

/**
 * Limites da finalizacao em massa vindos de {@code configuracao_automacao} (V94), lidos a cada uso: o que o
 * responsavel muda vale no pedido seguinte, sem deploy. Parametro ausente ou invalido falha alto: um limite
 * inventado aqui esconderia uma instancia mal configurada.
 */
@Component
class ParametrosDeFinalizacaoEmMassaConfiguraveis implements ParametrosDeFinalizacaoEmMassa {

    static final String CHAVE_LIMITE = "atendimento.finalizacao_em_massa.limite_por_operacao";
    static final String CHAVE_PERIODO = "atendimento.finalizacao_em_massa.periodo_maximo_dias";
    static final String CHAVE_LOTE = "atendimento.finalizacao_em_massa.lote";

    private final ConfiguracaoAutomacaoRepositorio configuracoes;

    ParametrosDeFinalizacaoEmMassaConfiguraveis(ConfiguracaoAutomacaoRepositorio configuracoes) {
        this.configuracoes = configuracoes;
    }

    @Override
    public int limitePorOperacao() {
        return inteiro(CHAVE_LIMITE);
    }

    @Override
    public int periodoMaximoEmDias() {
        return inteiro(CHAVE_PERIODO);
    }

    @Override
    public int tamanhoDoLote() {
        return inteiro(CHAVE_LOTE);
    }

    private int inteiro(String chave) {
        var configuracao = configuracoes
                .porChave(chave)
                .orElseThrow(() -> new IllegalStateException("parametro " + chave + " nao encontrado"));
        try {
            int valor = Integer.parseInt(configuracao.valor().trim());
            if (valor < 1) {
                throw new IllegalStateException("parametro " + chave + " precisa ser positivo");
            }
            return valor;
        } catch (NumberFormatException invalido) {
            throw new IllegalStateException("parametro " + chave + " nao e um inteiro", invalido);
        }
    }
}
