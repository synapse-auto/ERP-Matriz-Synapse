package com.synapse.crm.atendimento.application.finalizacaomassa;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.atendimento.application.FinalizarAtendimentoUseCase;
import com.synapse.crm.atendimento.application.FinalizarAtendimentoUseCase.Desfecho;
import com.synapse.crm.atendimento.application.RecursoDeAtendimentoIndisponivelException;
import com.synapse.crm.atendimento.application.finalizacaomassa.FinalizacaoEmMassaRepositorio.ItemPendente;
import com.synapse.crm.atendimento.application.finalizacaomassa.FinalizacaoEmMassaRepositorio.OperacaoDeFinalizacao;
import com.synapse.crm.atendimento.domain.atendimento.AtendimentoJaFinalizadoException;
import com.synapse.crm.atendimento.domain.finalizacaomassa.MotivoDoItemDeFinalizacao;
import com.synapse.crm.atendimento.domain.finalizacaomassa.PeriodoDeFinalizacao;
import com.synapse.crm.atendimento.domain.finalizacaomassa.StatusDoItemDeFinalizacao;
import com.synapse.crm.sharedkernel.persistencia.Pools;

/**
 * As transacoes curtas do worker, uma por passo. Ficam num bean proprio porque {@code @Transactional} so vale
 * em chamada entre beans: e isto que faz cada item ser sua propria transacao (falha parcial nunca desfaz o que
 * ja foi finalizado) e mantem a selecao e a conclusao fora da conexao do item.
 */
@Service
public class TransacoesDaFinalizacaoEmMassa {

    private final FinalizacaoEmMassaRepositorio repositorio;
    private final FinalizarAtendimentoUseCase finalizar;

    public TransacoesDaFinalizacaoEmMassa(FinalizacaoEmMassaRepositorio repositorio, FinalizarAtendimentoUseCase finalizar) {
        this.repositorio = repositorio;
        this.finalizar = finalizar;
    }

    @PreAuthorize("hasRole('SERVICO')")
    @Transactional(transactionManager = Pools.CHAT_TRANSACTION_MANAGER)
    public Optional<OperacaoDeFinalizacao> reivindicar(Instant agora, Duration lease) {
        return repositorio.reivindicarProxima(agora, lease);
    }

    @PreAuthorize("hasRole('SERVICO')")
    @Transactional(transactionManager = Pools.CHAT_TRANSACTION_MANAGER, readOnly = true)
    public List<ItemPendente> proximosPendentes(UUID operacaoId, int limite) {
        return repositorio.proximosPendentes(operacaoId, limite);
    }

    /** Finaliza e marca o item na MESMA transacao: nunca fica "finalizado sem registro" nem o contrario. */
    @PreAuthorize("hasRole('SERVICO')")
    @Transactional(transactionManager = Pools.CHAT_TRANSACTION_MANAGER)
    public void processarItem(OperacaoDeFinalizacao operacao, ItemPendente item, Instant agora) {
        PeriodoDeFinalizacao periodo = new PeriodoDeFinalizacao(
                operacao.dataDe(), operacao.dataAte(), operacao.horaInicio(), operacao.horaFim(),
                ZoneId.of(operacao.fuso()), operacao.periodoInicio(), operacao.periodoFim());
        StatusDoItemDeFinalizacao status;
        MotivoDoItemDeFinalizacao motivo = null;
        try {
            Desfecho desfecho = finalizar.executarPelaFinalizacaoEmMassa(
                    item.atendimentoId(), operacao.solicitanteId(), item.atendenteId(), periodo);
            status = desfecho.finalizado() ? StatusDoItemDeFinalizacao.FINALIZADO : StatusDoItemDeFinalizacao.IGNORADO;
            motivo = desfecho.ignoradoPor();
        } catch (AtendimentoJaFinalizadoException ja) {
            status = StatusDoItemDeFinalizacao.IGNORADO;
            motivo = MotivoDoItemDeFinalizacao.JA_FINALIZADO;
        } catch (RecursoDeAtendimentoIndisponivelException indisponivel) {
            status = StatusDoItemDeFinalizacao.IGNORADO;
            motivo = MotivoDoItemDeFinalizacao.INDISPONIVEL;
        }
        repositorio.marcarItem(item.operacaoId(), item.atendimentoId(), status, motivo, agora);
    }

    /** Falha inesperada: a transacao do item foi desfeita; aqui so se registra, em transacao nova. */
    @PreAuthorize("hasRole('SERVICO')")
    @Transactional(transactionManager = Pools.CHAT_TRANSACTION_MANAGER, propagation = Propagation.REQUIRES_NEW)
    public void registrarFalha(ItemPendente item, Instant agora) {
        repositorio.marcarItem(
                item.operacaoId(), item.atendimentoId(), StatusDoItemDeFinalizacao.FALHA,
                MotivoDoItemDeFinalizacao.ERRO_INESPERADO, agora);
    }

    @PreAuthorize("hasRole('SERVICO')")
    @Transactional(transactionManager = Pools.CHAT_TRANSACTION_MANAGER)
    public boolean concluirSeNaoHaPendentes(UUID operacaoId, Instant agora) {
        return repositorio.concluirSeNaoHaPendentes(operacaoId, agora);
    }

    @PreAuthorize("hasRole('SERVICO')")
    @Transactional(transactionManager = Pools.CHAT_TRANSACTION_MANAGER)
    public void liberarLease(UUID operacaoId) {
        repositorio.liberarLease(operacaoId);
    }
}
