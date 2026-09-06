package com.synapse.crm.automacaoconfig.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Customizacao opcional da marca da instancia.
 *
 * <p>{@code null} em tema ou logo significa que o valor ainda vem dos recursos do classpath. A
 * decisao de fallback fica na camada de aplicacao; o dominio apenas carrega o estado persistido.
 */
public record MarcaDaInstancia(
        String temaJson, String logoReferenciaStorage, UUID atualizadoPorId, Instant atualizadoEm) {}
