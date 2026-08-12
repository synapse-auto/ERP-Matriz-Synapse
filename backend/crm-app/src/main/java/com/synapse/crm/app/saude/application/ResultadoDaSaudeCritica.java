package com.synapse.crm.app.saude.application;

import java.time.Instant;
import java.util.List;

/** Resposta do endpoint e entrada do roteador de alertas. */
public record ResultadoDaSaudeCritica(
        EstadoDaSaude status,
        SeveridadeSaude severidade,
        Instant verificadoEm,
        List<ComponenteDaSaude> componentes) {

    public ResultadoDaSaudeCritica {
        componentes = List.copyOf(componentes);
    }

    public List<ComponenteDaSaude> componentesComFalha() {
        return componentes.stream().filter(ComponenteDaSaude::falhou).toList();
    }
}
