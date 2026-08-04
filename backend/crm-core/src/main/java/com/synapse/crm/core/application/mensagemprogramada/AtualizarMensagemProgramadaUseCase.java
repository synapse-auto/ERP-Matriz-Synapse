package com.synapse.crm.core.application.mensagemprogramada;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.synapse.crm.core.domain.mensagemprogramada.MensagemProgramada;

@Service
public class AtualizarMensagemProgramadaUseCase {
    private final MensagemProgramadaRepositorio mensagens;
    public AtualizarMensagemProgramadaUseCase(MensagemProgramadaRepositorio mensagens) { this.mensagens = mensagens; }
    @PreAuthorize("hasAnyRole('ATENDENTE','SUBGESTOR','GESTOR','ADMINISTRADOR')")
    @Transactional
    public Optional<MensagemProgramada> executar(UUID id, String conteudo, Instant dataEnvio) {
        Optional<MensagemProgramada> alterada = mensagens.atualizarAgendada(id, conteudo.trim(), dataEnvio);
        if (alterada.isEmpty() && mensagens.porIdVisivel(id).isPresent()) throw new MensagemProgramadaNaoEditavelException();
        return alterada;
    }
}
