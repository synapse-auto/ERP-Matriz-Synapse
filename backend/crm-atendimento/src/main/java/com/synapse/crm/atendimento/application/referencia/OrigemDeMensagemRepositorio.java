package com.synapse.crm.atendimento.application.referencia;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface OrigemDeMensagemRepositorio {

    Optional<OrigemDeMensagem> buscar(UUID mensagemId, Instant enviadoEm);

    Optional<OrigemDeMensagem> buscarPorWamid(String wamid);
}
