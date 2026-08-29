package com.synapse.crm.atendimento.application.midia;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MidiasDoLeadRepositorio {
    List<MidiaDoLead> listar(UUID leadId, int limite, int deslocamento);
    Optional<MidiaDoLead> porMensagem(UUID leadId, UUID mensagemId);
}
