package com.synapse.crm.equipe.application.feedback;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.equipe.domain.feedback.AreaFeedback;
import com.synapse.crm.equipe.domain.feedback.Feedback;
import com.synapse.crm.equipe.domain.feedback.TipoFeedback;
import com.synapse.crm.sharedkernel.identidade.UsuarioContext;

@Service
public class EnviarFeedbackUseCase {
    private final FeedbackRepositorio repositorio;
    private final UsuarioContext usuario;
    private final Clock relogio;

    public EnviarFeedbackUseCase(
            FeedbackRepositorio repositorio, UsuarioContext usuario, Clock relogio) {
        this.repositorio = repositorio;
        this.usuario = usuario;
        this.relogio = relogio;
    }

    @PreAuthorize("hasAnyRole('ATENDENTE','SUBGESTOR','GESTOR','ADMINISTRADOR')")
    @Transactional
    public Feedback executar(TipoFeedback tipo, AreaFeedback area, String descricao) {
        Feedback feedback = new Feedback(UUID.randomUUID(), usuario.atual().id(), tipo, area,
                descricao, Instant.now(relogio));
        return repositorio.salvar(feedback);
    }
}
