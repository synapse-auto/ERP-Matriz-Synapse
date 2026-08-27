package com.synapse.crm.equipe.application.feedback;

import java.time.Instant;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.equipe.domain.feedback.FeedbackInvalidoException;
import com.synapse.crm.equipe.domain.feedback.TipoFeedback;

@Service
public class ListarFeedbacksUseCase {
    public static final int LIMITE_PADRAO = 20;
    public static final int LIMITE_MAXIMO = 50;

    private final FeedbackRepositorio repositorio;

    public ListarFeedbacksUseCase(FeedbackRepositorio repositorio) {
        this.repositorio = repositorio;
    }

    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Transactional(readOnly = true)
    public FeedbackRepositorio.Pagina executar(
            TipoFeedback tipo, Instant antesDe, UUID antesDoId, int limite) {
        if ((antesDe == null) != (antesDoId == null)) {
            throw new FeedbackInvalidoException("O cursor de paginação está incompleto.");
        }
        if (limite < 1 || limite > LIMITE_MAXIMO) {
            throw new FeedbackInvalidoException(
                    "O limite deve estar entre 1 e " + LIMITE_MAXIMO + ".");
        }
        return repositorio.listar(tipo, antesDe, antesDoId, limite);
    }
}
