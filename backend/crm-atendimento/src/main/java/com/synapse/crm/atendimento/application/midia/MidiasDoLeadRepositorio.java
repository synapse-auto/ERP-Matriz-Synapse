package com.synapse.crm.atendimento.application.midia;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.synapse.crm.atendimento.domain.mensagem.TipoMensagem;

public interface MidiasDoLeadRepositorio {
    List<MidiaDoLead> listar(UUID leadId, int limite, int deslocamento, Set<TipoMensagem> tipos);
    Optional<MidiaDoLead> porMensagem(UUID leadId, UUID mensagemId);
}
