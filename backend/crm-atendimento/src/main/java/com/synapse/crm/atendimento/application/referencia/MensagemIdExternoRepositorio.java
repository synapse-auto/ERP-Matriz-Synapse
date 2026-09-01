package com.synapse.crm.atendimento.application.referencia;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface MensagemIdExternoRepositorio {

    void gravar(String wamid, UUID mensagemId, Instant enviadoEm, UUID atendimentoId);

    Optional<String> wamidDe(UUID mensagemId, Instant enviadoEm);

    boolean existe(String wamid);
}
