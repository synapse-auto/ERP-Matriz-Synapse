package com.synapse.crm.atendimento.application;

import java.util.Comparator;
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
    private final TransferirAtendimentoUseCase transferir;

    public TransferirAtendimentoDaAutomacaoUseCase(
            ListarAtendentesDisponiveisUseCase listarDisponiveis,
            AtendimentoRepositorio atendimentos,
            TransferirAtendimentoUseCase transferir) {
        this.listarDisponiveis = listarDisponiveis;
        this.atendimentos = atendimentos;
        this.transferir = transferir;
    }

    @PreAuthorize("hasRole('SERVICO')")
    @Transactional(transactionManager = Pools.CHAT_TRANSACTION_MANAGER)
    public Atendimento executar(UUID atendimentoId) {
        atendimentos.bloquearDistribuicaoDaAutomacao();

        AtendenteDisponivelParaIa destino = listarDisponiveis.executar().stream()
                .min(Comparator.comparingLong((AtendenteDisponivelParaIa candidato) ->
                                atendimentos.contarAbertosDoAtendente(candidato.usuarioId()))
                        .thenComparing(AtendenteDisponivelParaIa::nome)
                        .thenComparing(AtendenteDisponivelParaIa::usuarioId))
                .orElseThrow(NenhumAtendenteDisponivelException::new);

        return transferir.executarPelaAutomacao(atendimentoId, destino.usuarioId());
    }
}
