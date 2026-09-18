package com.synapse.crm.atendimento.infrastructure.outbox;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.atendimento.application.Outbox;
import com.synapse.crm.atendimento.application.ResumoIaAutomacaoGateway;
import com.synapse.crm.atendimento.application.ResumoIaAutomacaoGateway.Resultado;
import com.synapse.crm.atendimento.application.resumo.SolicitacaoResumoIaRepositorio;
import com.synapse.crm.atendimento.domain.evento.ResumoIaParaTempoReal;
import com.synapse.crm.atendimento.infrastructure.automacao.ResumoIaAutomacaoProperties;
import com.synapse.crm.sharedkernel.persistencia.Pools;

/** Drena a outbox específica do resumo sem ocupar o publisher de mensagens do canal. */
@Component
public class PublicadorDeSolicitacaoResumoIaOperacoes {

    private static final Logger log = LoggerFactory.getLogger(PublicadorDeSolicitacaoResumoIaOperacoes.class);
    private final Outbox outbox;
    private final ResumoIaAutomacaoGateway automacao;
    private final ResumoIaAutomacaoProperties propriedades;
    private final SolicitacaoResumoIaRepositorio solicitacoes;
    private final ApplicationEventPublisher eventos;
    private final Clock relogio;

    public PublicadorDeSolicitacaoResumoIaOperacoes(
            Outbox outbox,
            ResumoIaAutomacaoGateway automacao,
            ResumoIaAutomacaoProperties propriedades,
            SolicitacaoResumoIaRepositorio solicitacoes,
            Clock relogio,
            ApplicationEventPublisher eventos) {
        this.outbox = outbox;
        this.automacao = automacao;
        this.propriedades = propriedades;
        this.solicitacoes = solicitacoes;
        this.relogio = relogio;
        this.eventos = eventos;
    }

    @Transactional(transactionManager = Pools.CHAT_TRANSACTION_MANAGER)
    public int rodada() {
        if (!automacao.configurado()) return 0;
        Instant agora = Instant.now(relogio);
        List<Outbox.SolicitacaoResumoIaPendente> pendentes = outbox.reservarSolicitacoesResumoIaPendentes(
                propriedades.lote(), agora, agora.plus(propriedades.reservaExpiracao()));
        pendentes.forEach(pendente -> entregar(pendente, agora));
        return pendentes.size();
    }

    private void entregar(Outbox.SolicitacaoResumoIaPendente pendente, Instant agora) {
        Resultado resultado = automacao.enviar(
                pendente.solicitacaoId(),
                pendente.leadId(),
                pendente.atendimentoId(),
                pendente.solicitadoEm());
        if (resultado == Resultado.ACEITO) {
            outbox.marcarPublicado(pendente.outboxId(), agora);
            return;
        }
        int tentativas = pendente.tentativas() + 1;
        String erro = resultado == Resultado.RECUSADO
                ? "webhook de resumo recusou a solicitação"
                : "webhook de resumo indisponível";
        if (resultado == Resultado.RECUSADO || tentativas >= propriedades.maximoDeTentativas()) {
            outbox.esgotar(pendente.outboxId(), agora, erro);
            boolean atualizado = solicitacoes.atualizarStatus(
                    pendente.solicitacaoId(),
                    pendente.leadId(),
                    pendente.atendimentoId(),
                    SolicitacaoResumoIaRepositorio.Status.FALHOU,
                    "WEBHOOK_INDISPONIVEL",
                    erro,
                    agora);
            if (atualizado) {
                eventos.publishEvent(new ResumoIaParaTempoReal(
                        pendente.atendimentoId(),
                        pendente.leadId(),
                        pendente.solicitacaoId(),
                        SolicitacaoResumoIaRepositorio.Status.FALHOU.name(),
                        "WEBHOOK_INDISPONIVEL",
                        agora));
            }
            log.error("Solicitação de resumo por IA {} terminou sem entrega: {}", pendente.solicitacaoId(), erro);
        } else {
            outbox.reagendar(
                    pendente.outboxId(), agora.plus(propriedades.esperaApos(pendente.tentativas())), erro);
        }
    }
}
