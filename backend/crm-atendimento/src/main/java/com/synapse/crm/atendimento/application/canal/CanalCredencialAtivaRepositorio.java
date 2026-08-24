package com.synapse.crm.atendimento.application.canal;

import java.util.Optional;

/** Porta de leitura minima usada para isolar a entrada do webhook por numero de destino. */
public interface CanalCredencialAtivaRepositorio {

    ConfiguracaoCanalAtivo carregarConfiguracao();

    Optional<CanalEntradaAtiva> porIdentificadorExterno(String identificadorExterno);
}
