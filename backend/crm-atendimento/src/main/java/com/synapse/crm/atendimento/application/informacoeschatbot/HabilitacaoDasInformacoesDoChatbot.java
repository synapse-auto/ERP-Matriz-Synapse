package com.synapse.crm.atendimento.application.informacoeschatbot;

/**
 * Se a instancia habilitou o card de informacoes do chatbot.
 *
 * <p>Capacidade, nunca nome de cliente: desabilitada e o estado padrao, e desabilitar nao apaga o
 * que ja foi registrado — apenas para de aceitar e de exibir.
 */
public interface HabilitacaoDasInformacoesDoChatbot {

    boolean habilitada();
}
