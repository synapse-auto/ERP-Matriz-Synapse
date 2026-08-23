package com.synapse.crm.atendimento.application;

import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.atendimento.domain.atendimento.Atendimento;
import com.synapse.crm.equipe.application.disponibilidade.ListarAtendentesDisponiveisUseCase;
import com.synapse.crm.equipe.domain.disponibilidade.AtendenteDisponivelParaIa;
import com.synapse.crm.sharedkernel.persistencia.Pools;

/**
 * Escolhe o destino elegivel e delega a mudanca ao caso de uso canonico de transferencia.
 *
 * <p>O contrato nao recebe um usuario: disponibilidade e carga atual sao fatos do servidor, e nao
 * dados que a Automacao possa forjar para furar a distribuicao comercial.
 */
@Service
public class TransferirAtendimentoDaAutomacaoUseCase {

    private final ListarAtendentesDisponiveisUseCase listarDisponiveis;
    private final AtendimentoRepositorio atendimentos;
    private final AtendenteParaTransferenciaRepositorio destinos;
    private final TransferirAtendimentoUseCase transferir;

    public TransferirAtendimentoDaAutomacaoUseCase(
            ListarAtendentesDisponiveisUseCase listarDisponiveis,
            AtendimentoRepositorio atendimentos,
            AtendenteParaTransferenciaRepositorio destinos,
            TransferirAtendimentoUseCase transferir) {
        this.listarDisponiveis = listarDisponiveis;
        this.atendimentos = atendimentos;
        this.destinos = destinos;
        this.transferir = transferir;
    }

    @PreAuthorize("hasRole('SERVICO')")
    @Transactional(transactionManager = Pools.CHAT_TRANSACTION_MANAGER)
    public Atendimento executar(UUID atendimentoId) {
        atendimentos.bloquearDistribuicaoDaAutomacao();

        AtendenteDisponivelParaIa destino = listarDisponiveis.executar().stream()
                .findFirst()
                .orElseThrow(NenhumAtendenteDisponivelException::new);

        return transferir.executarPelaAutomacao(atendimentoId, destino.usuarioId());
    }

    /** Transferência explícita: o destino é validado no banco e nunca aceito como UUID arbitrário. */
    @PreAuthorize("hasRole('SERVICO')")
    @Transactional(transactionManager = Pools.CHAT_TRANSACTION_MANAGER)
    public Atendimento executar(UUID atendimentoId, UUID atendenteId) {
        destinos.ativoAtendente(atendenteId)
                .orElseThrow(() -> new AtendenteDestinoInvalidoException(atendenteId));
        return transferir.executarPelaAutomacao(atendimentoId, atendenteId);
    }
}
