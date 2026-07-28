package com.synapse.crm.core.application.etapa;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.synapse.crm.core.domain.etapa.EtapaAtendimento;

/** Porta de acesso as etapas do funil. Configuracao da operacao, sem recorte por usuario. */
public interface EtapaRepositorio {

    /** Sempre na ordem do funil: e assim que o Kanban espera receber. */
    List<EtapaAtendimento> listarEmOrdem();

    Optional<EtapaAtendimento> porId(UUID id);

    EtapaAtendimento salvar(EtapaAtendimento etapa);

    void remover(UUID id);
}
