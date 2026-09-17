package com.synapse.crm.automacaoconfig.domain.festivas;

import java.time.LocalDate;
import java.util.UUID;

/** Data festiva configurada pela gestao; a automacao ainda nao a executa nesta etapa. */
public record MensagemFestiva(
        UUID id,
        String titulo,
        String icone,
        LocalDate data,
        String mensagem,
        boolean ativo) {}
