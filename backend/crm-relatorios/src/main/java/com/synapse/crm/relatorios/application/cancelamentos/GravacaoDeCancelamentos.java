package com.synapse.crm.relatorios.application.cancelamentos;

import java.time.Instant;
import java.util.UUID;

import com.synapse.crm.relatorios.domain.cancelamentos.NovoCancelamento;

/** Escrita atomica com unique constraints, replay identico e conflito sem sobrescrever dados. */
public interface GravacaoDeCancelamentos {
    Resultado registrar(NovoCancelamento evento, String chave, Instant recebidoEm);
    record Resultado(UUID id, Instant recebidoEm, boolean repetido) {}
}
