package com.synapse.crm.atendimento.application.canal;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Lista inclusive canais inativos, pois uma ficha historica ainda pode aponta-los como origem. */
@Service
public class ListarCanaisUseCase {

    private final CanalConsultaRepositorio canais;

    public ListarCanaisUseCase(CanalConsultaRepositorio canais) {
        this.canais = canais;
    }

    @PreAuthorize("hasAnyRole('ATENDENTE', 'SUBGESTOR', 'GESTOR', 'ADMINISTRADOR')")
    @Transactional(readOnly = true)
    public List<CanalResumo> executar() {
        return canais.listar();
    }
}
