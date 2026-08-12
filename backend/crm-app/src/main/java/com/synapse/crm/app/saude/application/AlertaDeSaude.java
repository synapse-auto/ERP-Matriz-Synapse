package com.synapse.crm.app.saude.application;

import java.time.Instant;
import java.util.List;
import java.util.Set;

public record AlertaDeSaude(
        SeveridadeSaude severidade,
        Instant ocorridoEm,
        Set<DestinoDoAlerta> destinos,
        List<String> componentes) {

    public AlertaDeSaude {
        destinos = Set.copyOf(destinos);
        componentes = List.copyOf(componentes);
    }
}
