package com.synapse.crm.automacaoconfig.infrastructure.proativo;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.synapse.crm.atendimento.application.origem.TipoDeOrigem;
import com.synapse.crm.atendimento.application.proativo.PoliticaDeEnvioProativo;
import com.synapse.crm.automacaoconfig.application.ConfiguracaoAutomacaoRepositorio;
import com.synapse.crm.automacaoconfig.domain.ConfiguracaoAutomacao;
import com.synapse.crm.automacaoconfig.domain.TipoConfiguracaoAutomacao;

/**
 * Le a politica de frequencia de {@code configuracao_automacao} (V85) a cada decisao; o cache do
 * repositorio absorve a leitura. Parametro ausente ou invalido volta ao padrao semeado (ligado, 0 =
 * sem limite) com alerta: um erro de digitacao nao pode, sozinho, calar nem liberar a Automacao alem
 * do que ja acontecia antes da E219.
 */
@Component
class PoliticaDeEnvioProativoPorConfiguracao implements PoliticaDeEnvioProativo {

    static final String CHAVE_HABILITADA = "automacao_proativa.habilitada";
    static final String CHAVE_COOLDOWN = "automacao_proativa.cooldown_horas";
    static final String CHAVE_TETO = "automacao_proativa.teto_diario_por_lead";
    private static final String ALERTA = "[ALERTA_CONFIG_AUTOMACAO]";
    private static final int COOLDOWN_MAXIMO = 720;
    private static final int TETO_MAXIMO = 50;
    private static final Logger log = LoggerFactory.getLogger(PoliticaDeEnvioProativoPorConfiguracao.class);

    private final ConfiguracaoAutomacaoRepositorio configuracoes;

    PoliticaDeEnvioProativoPorConfiguracao(ConfiguracaoAutomacaoRepositorio configuracoes) {
        this.configuracoes = configuracoes;
    }

    @Override
    public Politica vigente() {
        Set<TipoDeOrigem> desligados = EnumSet.noneOf(TipoDeOrigem.class);
        for (TipoDeOrigem tipo : TipoDeOrigem.values()) {
            if (tipo.proativa() && !booleano(chaveDoTipo(tipo))) {
                desligados.add(tipo);
            }
        }
        return new Politica(
                booleano(CHAVE_HABILITADA),
                desligados,
                inteiro(CHAVE_COOLDOWN, COOLDOWN_MAXIMO),
                inteiro(CHAVE_TETO, TETO_MAXIMO));
    }

    static String chaveDoTipo(TipoDeOrigem tipo) {
        return "automacao_proativa." + tipo.name().toLowerCase(Locale.ROOT) + ".habilitada";
    }

    private boolean booleano(String chave) {
        ConfiguracaoAutomacao parametro = configuracoes.porChave(chave).orElse(null);
        if (parametro == null) {
            log.warn("{} parametro {} ausente; vale o padrao (ligado)", ALERTA, chave);
            return true;
        }
        String valor = parametro.valor().trim();
        if (parametro.tipo() == TipoConfiguracaoAutomacao.BOOLEAN && "false".equalsIgnoreCase(valor)) {
            return false;
        }
        if (parametro.tipo() == TipoConfiguracaoAutomacao.BOOLEAN && "true".equalsIgnoreCase(valor)) {
            return true;
        }
        log.error("{} parametro {} invalido; vale o padrao (ligado)", ALERTA, chave);
        return true;
    }

    private int inteiro(String chave, int maximo) {
        ConfiguracaoAutomacao parametro = configuracoes.porChave(chave).orElse(null);
        if (parametro == null) {
            log.warn("{} parametro {} ausente; vale o padrao (0 = sem limite)", ALERTA, chave);
            return 0;
        }
        try {
            int valor = Integer.parseInt(parametro.valor().trim());
            if (valor >= 0 && valor <= maximo) {
                return valor;
            }
        } catch (NumberFormatException invalido) {
            // cai no alerta abaixo
        }
        log.error("{} parametro {} invalido ou fora de 0..{}; vale o padrao (0 = sem limite)", ALERTA, chave, maximo);
        return 0;
    }
}
