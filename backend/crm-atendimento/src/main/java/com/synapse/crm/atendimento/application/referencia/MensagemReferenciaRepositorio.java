package com.synapse.crm.atendimento.application.referencia;

import java.time.Instant;
import java.util.UUID;

import com.synapse.crm.atendimento.domain.mensagem.ReferenciaDeMensagem;

public interface MensagemReferenciaRepositorio {

    void gravar(UUID mensagemId, Instant enviadoEm, ReferenciaDeMensagem referencia);
}
