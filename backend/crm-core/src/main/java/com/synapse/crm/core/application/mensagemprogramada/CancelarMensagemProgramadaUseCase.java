package com.synapse.crm.core.application.mensagemprogramada;

import java.util.Optional;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.synapse.crm.core.domain.mensagemprogramada.MensagemProgramada;

@Service
public class CancelarMensagemProgramadaUseCase {
    private final MensagemProgramadaRepositorio mensagens;
    public CancelarMensagemProgramadaUseCase(MensagemProgramadaRepositorio mensagens) { this.mensagens = mensagens; }
    @PreAuthorize("hasAnyRole('ATENDENTE','SUBGESTOR','GESTOR','ADMINISTRADOR')")
    @Transactional
    public Optional<MensagemProgramada> executar(UUID id) {
        Optional<MensagemProgramada> cancelada = mensagens.cancelarAgendada(id);
        if (cancelada.isEmpty() && mensagens.porIdVisivel(id).isPresent()) throw new MensagemProgramadaNaoEditavelException();
        return cancelada;
    }
}
