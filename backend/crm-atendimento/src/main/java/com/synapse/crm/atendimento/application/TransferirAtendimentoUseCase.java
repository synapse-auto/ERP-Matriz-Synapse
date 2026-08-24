package com.synapse.crm.atendimento.application;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.atendimento.domain.atendimento.Atendimento;
import com.synapse.crm.atendimento.domain.atendimento.StatusAtendimento;
import com.synapse.crm.atendimento.domain.evento.EventoDeAtendimento;
import com.synapse.crm.core.application.lead.LeadNoCaminhoDeMensagem;
import com.synapse.crm.core.domain.lead.StatusBasicoLead;
import com.synapse.crm.core.domain.timeline.OrigemEvento;
import com.synapse.crm.sharedkernel.persistencia.Pools;

/**
 * Reatribui o atendimento — para outro atendente ou de volta para a IA.
 *
 * <p>O lead acompanha. Deixar o atendimento com um dono e o lead com outro produziria a pior forma de
 * inconsistencia deste sistema: a conversa aparece para um atendente e a comissao conta para outro.
 *
 * <p>Roda no pool do chat pelo mesmo motivo dos casos de uso de mensagem — atendimento e lead mudam
 * juntos ou nao mudam.
 */
@Service
public class TransferirAtendimentoUseCase {

    private final AtendimentoRepositorio atendimentos;
    private final LeadNoCaminhoDeMensagem leads;
    private final ApplicationEventPublisher eventos;
    private final Clock relogio;

    public TransferirAtendimentoUseCase(
            AtendimentoRepositorio atendimentos,
            LeadNoCaminhoDeMensagem leads,
            ApplicationEventPublisher eventos,
            Clock relogio) {
        this.atendimentos = atendimentos;
        this.leads = leads;
        this.eventos = eventos;
        this.relogio = relogio;
    }

    /**
     * @param paraAtendenteId {@code null} devolve a conversa para a IA
     * @param quemPediu autor da transferencia, para a timeline dizer quem moveu o lead
     */
    @PreAuthorize("hasAnyRole('ATENDENTE','GESTOR','SUBGESTOR','ADMINISTRADOR')")
    @Transactional(transactionManager = Pools.CHAT_TRANSACTION_MANAGER)
    public Atendimento executar(UUID atendimentoId, UUID paraAtendenteId, UUID quemPediu) {
        return transferir(atendimentoId, paraAtendenteId, quemPediu, OrigemEvento.USUARIO, false);
    }

    /** Mesma transferencia, com identidade tecnica e somente a partir do estado da IA. */
    @PreAuthorize("hasRole('SERVICO')")
    @Transactional(transactionManager = Pools.CHAT_TRANSACTION_MANAGER)
    public Atendimento executarPelaAutomacao(UUID atendimentoId, UUID paraAtendenteId) {
        if (paraAtendenteId == null) {
            throw new IllegalArgumentException("transferencia da Automacao exige atendente destino");
        }
        return transferir(atendimentoId, paraAtendenteId, null, OrigemEvento.AUTOMACAO, true);
    }

    /** A Automação pode devolver uma conversa humana para a IA, sem fabricar um usuário. */
    @PreAuthorize("hasRole('SERVICO')")
    @Transactional(transactionManager = Pools.CHAT_TRANSACTION_MANAGER)
    public Atendimento devolverParaIaPelaAutomacao(UUID atendimentoId) {
        return transferir(atendimentoId, null, null, OrigemEvento.AUTOMACAO, false);
    }

    /** Devolve uma conversa por comando do sistema, sem fabricar um usuario para a auditoria. */
    @PreAuthorize("hasRole('SERVICO')")
    @Transactional(transactionManager = Pools.CHAT_TRANSACTION_MANAGER)
    public Atendimento devolverParaIaPeloSistema(UUID atendimentoId) {
        return transferir(atendimentoId, null, null, OrigemEvento.SISTEMA, false);
    }

    private Atendimento transferir(
            UUID atendimentoId,
            UUID paraAtendenteId,
            UUID atorId,
            OrigemEvento atorTipo,
            boolean exigirOrigemIa) {
        Instant agora = Instant.now(relogio);

        Atendimento antes = atendimentos
                .porId(atendimentoId)
                .orElseThrow(
                        () -> new RecursoDeAtendimentoIndisponivelException("atendimento", atendimentoId));

        if (exigirOrigemIa && antes.status() != StatusAtendimento.EM_IA) {
            throw new TransferenciaDaAutomacaoInvalidaException(atendimentoId);
        }

        if (paraAtendenteId == null && antes.status() == StatusAtendimento.EM_IA) {
            return antes;
        }

        Atendimento depois =
                paraAtendenteId == null ? antes.devolverParaIa() : antes.transferirPara(paraAtendenteId);
        atendimentos.salvar(depois);

        if (paraAtendenteId == null) {
            // Volta para o grupo "Potenciais": sem dono, visivel a todos os atendentes.
            leads.marcarStatus(antes.leadId(), StatusBasicoLead.IA);
        } else {
            leads.transferirPara(antes.leadId(), paraAtendenteId);
        }

        eventos.publishEvent(new EventoDeAtendimento.AtendimentoTransferido(
                antes.leadId(),
                leads.nomeParaTempoReal(antes.leadId()).orElse(""),
                antes.id(),
                antes.atendenteId(),
                paraAtendenteId,
                atorId,
                atorTipo,
                agora));

        return depois;
    }
}
