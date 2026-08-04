package com.synapse.crm.atendimento.application.canal;

import java.util.List;

/** Porta de leitura dos canais configurados nesta instancia. */
public interface CanalConsultaRepositorio {

    List<CanalResumo> listar();
}
