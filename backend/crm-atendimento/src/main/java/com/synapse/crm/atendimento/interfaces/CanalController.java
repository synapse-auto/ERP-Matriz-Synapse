package com.synapse.crm.atendimento.interfaces;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.synapse.crm.atendimento.application.canal.CanalResumo;
import com.synapse.crm.atendimento.application.canal.ListarCanaisUseCase;

/** Metadados de canal usados para traduzir a origem da ficha; nunca devolve credenciais. */
@RestController
@RequestMapping("/api/v1/canais")
class CanalController {

    private final ListarCanaisUseCase listar;

    CanalController(ListarCanaisUseCase listar) {
        this.listar = listar;
    }

    @GetMapping
    List<CanalResumo> listar() {
        return listar.executar();
    }
}
