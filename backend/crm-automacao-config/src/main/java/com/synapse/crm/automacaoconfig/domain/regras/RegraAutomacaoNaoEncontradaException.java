package com.synapse.crm.automacaoconfig.domain.regras;

import java.util.UUID;

public class RegraAutomacaoNaoEncontradaException extends RuntimeException {
    public RegraAutomacaoNaoEncontradaException(UUID id) { super("Regra de automacao nao encontrada: " + id); }
}
