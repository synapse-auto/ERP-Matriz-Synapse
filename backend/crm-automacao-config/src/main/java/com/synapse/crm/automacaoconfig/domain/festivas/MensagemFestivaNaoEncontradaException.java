package com.synapse.crm.automacaoconfig.domain.festivas;

import java.util.UUID;

public class MensagemFestivaNaoEncontradaException extends RuntimeException {
    public MensagemFestivaNaoEncontradaException(UUID id) {
        super("Data festiva nao encontrada: " + id);
    }
}
