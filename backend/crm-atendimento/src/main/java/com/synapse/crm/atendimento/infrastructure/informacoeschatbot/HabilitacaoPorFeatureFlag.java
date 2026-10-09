package com.synapse.crm.atendimento.infrastructure.informacoeschatbot;

import org.springframework.stereotype.Component;

import com.synapse.crm.atendimento.application.informacoeschatbot.HabilitacaoDasInformacoesDoChatbot;
import com.synapse.crm.sharedkernel.permissao.ConsultaDeFuncionalidades;

/**
 * O card so existe nas instancias que ligaram a feature flag {@value #FUNCIONALIDADE}. A decisao e por
 * capacidade, nunca pelo nome do cliente: um filho novo ganha o recurso ligando a flag, sem tocar no core.
 */
@Component
class HabilitacaoPorFeatureFlag implements HabilitacaoDasInformacoesDoChatbot {

    static final String FUNCIONALIDADE = "informacoes_chatbot_historico";

    private final ConsultaDeFuncionalidades funcionalidades;

    HabilitacaoPorFeatureFlag(ConsultaDeFuncionalidades funcionalidades) {
        this.funcionalidades = funcionalidades;
    }

    @Override
    public boolean habilitada() {
        return funcionalidades.habilitadas().contains(FUNCIONALIDADE);
    }
}
