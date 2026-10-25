package com.synapse.crm.atendimento.infrastructure.canal;

import java.util.Set;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Hosts exatos observados/documentados na homologacao. Vazio recusa downloads, nao a autenticacao. */
@ConfigurationProperties("synapse.canal.z-api.midia-entrada")
public record ZApiMidiaEntradaProperties(Set<String> hostsPermitidos, int concorrencia) {
    public ZApiMidiaEntradaProperties {
        hostsPermitidos = hostsPermitidos == null ? Set.of() : Set.copyOf(hostsPermitidos);
    }
}
