package com.synapse.crm.campanhas.application;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.sharedkernel.persistencia.Pools;

/**
 * Registra "respondeu" no destinatario da campanha quando o cliente escreve. A resposta segue o fluxo normal
 * (IA ou atendente); aqui so se marca o fato e se soma o contador, uma vez por destinatario.
 *
 * <p>Roda a cada mensagem recebida do sistema inteiro, entao e UMA consulta com indice: a janela de resposta e
 * lida dentro dela, sem carregar a configuracao.
 */
@Service
public class RegistrarRespostaDeCampanhaUseCase {

    private final DestinatarioRepositorio destinatarios;
    private final CampanhaRepositorio campanhas;

    public RegistrarRespostaDeCampanhaUseCase(DestinatarioRepositorio destinatarios, CampanhaRepositorio campanhas) {
        this.destinatarios = destinatarios;
        this.campanhas = campanhas;
    }

    /** {@code REQUIRES_NEW}: chamado em {@code AFTER_COMMIT}, ver {@code AplicarEntregaDeCampanhaUseCase}. */
    @Transactional(transactionManager = Pools.CHAT_TRANSACTION_MANAGER, propagation = Propagation.REQUIRES_NEW)
    public void executar(UUID leadId, Instant quando) {
        destinatarios
                .registrarResposta(leadId, quando)
                .ifPresent(campanhaId -> campanhas.variarContadores(campanhaId, CampanhaRepositorio.Variacao.respondeu()));
    }
}
