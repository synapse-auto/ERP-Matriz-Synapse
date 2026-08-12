package com.synapse.crm.app.saude.application;

import java.time.Instant;

public interface HorarioComercialDoCliente {

    boolean permiteAvisarCliente(Instant instante);
}
