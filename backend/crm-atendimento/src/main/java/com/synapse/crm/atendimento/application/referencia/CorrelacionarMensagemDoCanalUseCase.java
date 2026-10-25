package com.synapse.crm.atendimento.application.referencia;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.atendimento.domain.canal.IdentidadeDeMensagemDoCanal;
import com.synapse.crm.atendimento.domain.canal.TradutorDeCanal.StatusComIdentidade;
import com.synapse.crm.atendimento.domain.evento.MudancaDeStatusDeEntrega;
import com.synapse.crm.sharedkernel.persistencia.Pools;

/** Reconciliacao atomica tanto de aceite tardio quanto de status antecipado. Nenhum I/O externo. */
@Service
public class CorrelacionarMensagemDoCanalUseCase {
    private static final Logger log = LoggerFactory.getLogger(CorrelacionarMensagemDoCanalUseCase.class);
    private final CorrelacaoDeMensagemDoCanalRepositorio repositorio;
    private final ApplicationEventPublisher eventos;
    private final Clock relogio;

    public CorrelacionarMensagemDoCanalUseCase(CorrelacaoDeMensagemDoCanalRepositorio repositorio,
            ApplicationEventPublisher eventos, Clock relogio) {
        this.repositorio = repositorio;
        this.eventos = eventos;
        this.relogio = relogio;
    }

    public record Vinculo(IdentidadeDeMensagemDoCanal identidade, UUID mensagemId, Instant enviadoEm, UUID atendimentoId) {}

    @PreAuthorize("hasRole('SERVICO')")
    @Transactional(transactionManager = Pools.CHAT_TRANSACTION_MANAGER)
    public void executar(List<Vinculo> vinculos, List<StatusComIdentidade> observacoes) {
        for (Vinculo vinculo : vinculos) {
            if (repositorio.registrar(vinculo.identidade(), vinculo.mensagemId(), vinculo.enviadoEm(), vinculo.atendimentoId())) {
                aplicar(vinculo.identidade());
            } else {
                // O aceite externo ja ocorreu: nao roubar alias nem desfazer a outbox e reenviar.
                // IDs externos/credenciais nao entram no log; o aceite bruto permanece na outbox.
                log.error("[CORRELACAO_RECUSADA] mensagem={} atendimento={}; conferir aceite persistido antes de reconciliar",
                        vinculo.mensagemId(), vinculo.atendimentoId());
            }
        }
        for (StatusComIdentidade observacao : observacoes) {
            repositorio.observar(observacao);
            aplicar(observacao.identidade());
        }
    }

    private void aplicar(IdentidadeDeMensagemDoCanal identidade) {
        repositorio.aplicar(identidade).ifPresent(aplicado -> eventos.publishEvent(new MudancaDeStatusDeEntrega(
                aplicado.mensagemId(), aplicado.atendimentoId(), aplicado.leadId(), aplicado.status().name(), Instant.now(relogio))));
    }
}
