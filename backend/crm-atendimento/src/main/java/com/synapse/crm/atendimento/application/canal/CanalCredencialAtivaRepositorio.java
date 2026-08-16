package com.synapse.crm.atendimento.application.canal;

/** Porta de leitura minima usada para isolar a entrada do webhook por numero de destino. */
public interface CanalCredencialAtivaRepositorio {

    ConfiguracaoCanalAtivo carregarConfiguracao();
}
