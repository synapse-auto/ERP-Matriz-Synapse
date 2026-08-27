package com.synapse.crm.equipe.application.feedback;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.synapse.crm.equipe.domain.feedback.Feedback;
import com.synapse.crm.equipe.domain.feedback.TipoFeedback;
import com.synapse.crm.sharedkernel.identidade.PapelUsuario;

public interface FeedbackRepositorio {
    Feedback salvar(Feedback feedback);

    Pagina listar(TipoFeedback tipo, Instant antesDe, UUID antesDoId, int limite);

    record FeedbackResumo(UUID id, UUID autorId, String autorNome, PapelUsuario autorPapel,
            String autorFotoUrl, TipoFeedback tipo, String areaChave, String descricao,
            Instant criadoEm) {}

    record Pagina(List<FeedbackResumo> itens, Instant proximoCriadoEm, UUID proximoId) {}
}
