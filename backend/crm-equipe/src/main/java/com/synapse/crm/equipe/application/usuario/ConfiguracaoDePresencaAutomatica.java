package com.synapse.crm.equipe.application.usuario;

/**
 * Chave por instancia da presenca automatica ({@code presenca.automatica} em {@code configuracao_automacao}),
 * lida em tempo de execucao: ligar e desligar nao pede deploy. Ausente ou invalida = desligada.
 */
public interface ConfiguracaoDePresencaAutomatica {

    boolean habilitada();
}
