package com.synapse.crm.core.application.lead;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.core.domain.evento.FichaDoLeadResetada;
import com.synapse.crm.sharedkernel.persistencia.Pools;

/**
 * E195: zera etapa e resumo de IA do lead quando chega o comando {@code #resetgeral}.
 *
 * <p>Existe para repetir teste manual sem criar um lead novo a cada rodada. Nao apaga lead nem
 * conversa: notas, tags, dados customizados e contadores continuam onde estavam.
 *
 * <p>Sem usuario autenticado, como todo o caminho de webhook: roda sob {@code SERVICO}, a mesma
 * convencao de {@code devolverParaIaPeloSistema}. Participa da transacao do chat ja aberta pelo
 * processador — a ficha zera no mesmo commit que gravou a mensagem do comando.
 */
@Service
public class ResetarFichaDoLeadUseCase {

    private final LeadNoCaminhoDeMensagem leads;
    private final ApplicationEventPublisher eventos;
    private final Clock relogio;

    public ResetarFichaDoLeadUseCase(
            LeadNoCaminhoDeMensagem leads, ApplicationEventPublisher eventos, Clock relogio) {
        this.leads = leads;
        this.eventos = eventos;
        this.relogio = relogio;
    }

    /**
     * @return o que a ficha tinha antes; vazio quando o lead nao foi alcancado
     */
    @PreAuthorize("hasRole('SERVICO')")
    @Transactional(transactionManager = Pools.CHAT_TRANSACTION_MANAGER)
    public Optional<LeadNoCaminhoDeMensagem.FichaAnterior> executar(UUID leadId) {
        Optional<LeadNoCaminhoDeMensagem.FichaAnterior> anterior = leads.limparFichaParaResetGeral(leadId);
        anterior.ifPresent(ficha -> eventos.publishEvent(
                new FichaDoLeadResetada(leadId, ficha.etapaId(), ficha.tinhaResumo(), relogio.instant())));
        return anterior;
    }
}
