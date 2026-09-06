package com.synapse.crm.automacaoconfig.application;

import com.synapse.crm.automacaoconfig.domain.MarcaDaInstancia;

/** Porta da linha singleton que sobrepoe os recursos de marca do classpath. */
public interface MarcaDaInstanciaRepositorio {

    MarcaDaInstancia obter();

    MarcaDaInstancia salvarTema(String temaJson, java.util.UUID atualizadoPorId, java.time.Instant atualizadoEm);

    MarcaDaInstancia salvarLogo(String logoReferenciaStorage, java.util.UUID atualizadoPorId, java.time.Instant atualizadoEm);
}
