package com.synapse.crm.atendimento.infrastructure.outbox;

import java.time.Instant;
import java.util.List;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.atendimento.application.Outbox;
import com.synapse.crm.atendimento.application.ResumoIaAutomacaoGateway.Resultado;
import com.synapse.crm.atendimento.application.resumo.SolicitacaoResumoIaRepositorio;
import com.synapse.crm.atendimento.domain.evento.ResumoIaParaTempoReal;
import com.synapse.crm.atendimento.infrastructure.automacao.ResumoIaAutomacaoProperties;
import com.synapse.crm.sharedkernel.persistencia.Pools;

/** Transações curtas; nunca mantêm conexão/lock do chat durante HTTP. */
@Component
public class PublicadorDeSolicitacaoResumoIaTransacoes {
    private final Outbox outbox;
    private final SolicitacaoResumoIaRepositorio solicitacoes;
    private final ResumoIaAutomacaoProperties propriedades;
    private final ApplicationEventPublisher eventos;

    public PublicadorDeSolicitacaoResumoIaTransacoes(Outbox outbox, SolicitacaoResumoIaRepositorio solicitacoes,
            ResumoIaAutomacaoProperties propriedades, ApplicationEventPublisher eventos) {
        this.outbox = outbox;
        this.solicitacoes = solicitacoes;
        this.propriedades = propriedades;
        this.eventos = eventos;
    }

    @Transactional(transactionManager = Pools.CHAT_TRANSACTION_MANAGER)
    public List<Outbox.SolicitacaoResumoIaPendente> reservar(Instant agora) {
        return outbox.reservarSolicitacoesResumoIaPendentes(
                // Reserva uma unidade por rodada; não deixa itens esperando HTTP com lease envelhecida.
                1, agora, agora.plus(propriedades.reservaExpiracao()));
    }

    @Transactional(transactionManager = Pools.CHAT_TRANSACTION_MANAGER)
    public boolean precisaEntregar(Outbox.SolicitacaoResumoIaPendente pendente, Instant agora) {
        var ciclo = solicitacoes.porId(pendente.solicitacaoId()).orElse(null);
        if (ciclo == null || ciclo.status() != SolicitacaoResumoIaRepositorio.Status.PENDENTE) {
            // Um callback é evidência mais forte que a resposta HTTP perdida. Não repetir IA.
            outbox.marcarPublicado(pendente.outboxId(), agora);
            return false;
        }
        if (!solicitacoes.cicloVigente(pendente.solicitacaoId())) {
            outbox.esgotar(pendente.outboxId(), agora, "CICLO_OBSOLETO");
            falharEntrega(pendente, "CICLO_OBSOLETO", agora);
            return false;
        }
        return true;
    }

    @Transactional(transactionManager = Pools.CHAT_TRANSACTION_MANAGER)
    public void registrarResultado(Outbox.SolicitacaoResumoIaPendente pendente, Resultado resultado, Instant agora) {
        var ciclo = solicitacoes.porId(pendente.solicitacaoId()).orElse(null);
        if (resultado == Resultado.ACEITO || ciclo == null
                || ciclo.status() != SolicitacaoResumoIaRepositorio.Status.PENDENTE) {
            outbox.marcarPublicado(pendente.outboxId(), agora);
            return;
        }
        if (resultado == Resultado.RESULTADO_INCERTO) {
            // A entrega pode ter ocorrido. Suspende reenvios, não fabrica falha/conclusão.
            outbox.esgotar(pendente.outboxId(), agora, "WEBHOOK_RESULTADO_INCERTO");
            return;
        }
        boolean recusado = resultado == Resultado.RECUSADO || resultado == Resultado.AUTENTICACAO_RECUSADA;
        if (!recusado && pendente.tentativas() + 1 < propriedades.maximoDeTentativas()) {
            outbox.reagendar(pendente.outboxId(), agora.plus(propriedades.esperaApos(pendente.tentativas())),
                    "WEBHOOK_INDISPONIVEL");
            return;
        }
        String codigo = resultado == Resultado.AUTENTICACAO_RECUSADA ? "WEBHOOK_AUTENTICACAO_RECUSADA"
                : recusado ? "WEBHOOK_RECUSADO" : "WEBHOOK_INDISPONIVEL";
        outbox.esgotar(pendente.outboxId(), agora, codigo);
        falharEntrega(pendente, codigo, agora);
    }

    private void falharEntrega(Outbox.SolicitacaoResumoIaPendente pendente, String codigo, Instant agora) {
        if (solicitacoes.falharEntregaSePendente(pendente.solicitacaoId(), codigo, agora)) {
            eventos.publishEvent(new ResumoIaParaTempoReal(pendente.atendimentoId(), pendente.leadId(),
                    pendente.solicitacaoId(), "FALHOU", codigo, agora));
        }
    }
}
