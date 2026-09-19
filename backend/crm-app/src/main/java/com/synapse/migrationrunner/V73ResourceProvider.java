package com.synapse.migrationrunner;

import java.util.Collection;
import java.util.Objects;

import org.flywaydb.core.api.ResourceProvider;
import org.flywaydb.core.api.resource.LoadableResource;

/** Remove somente o SQL monolítico da V73 quando o runner usa a implementação paginada. */
final class V73ResourceProvider implements ResourceProvider {

    private static final String NOME_V73 = "V73__normalizar_prefixo_discagem_leads.sql";

    private final ResourceProvider delegate;

    V73ResourceProvider(ResourceProvider delegate) {
        this.delegate = Objects.requireNonNull(delegate, "resourceProvider do Flyway");
    }

    @Override
    public LoadableResource getResource(String name) {
        if (name != null && name.endsWith(NOME_V73)) {
            return null;
        }
        return delegate.getResource(name);
    }

    @Override
    public Collection<LoadableResource> getResources(String location, String[] suffixes) {
        return delegate.getResources(location, suffixes).stream()
                .filter(recurso -> !NOME_V73.equals(recurso.getFilename()))
                .toList();
    }
}
