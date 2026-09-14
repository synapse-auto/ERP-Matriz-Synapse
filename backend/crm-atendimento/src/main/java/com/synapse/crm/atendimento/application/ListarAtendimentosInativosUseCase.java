package com.synapse.crm.atendimento.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.sharedkernel.persistencia.Pools;

/** Consulta candidatos do job de inatividade em uma transação curta e somente de leitura. */
@Service
public class ListarAtendimentosInativosUseCase {

    private final AtendimentoRepositorio atendimentos;

    public ListarAtendimentosInativosUseCase(AtendimentoRepositorio atendimentos) {
        this.atendimentos = atendimentos;
    }

    /**
     * O contexto de serviço é obrigatório: a varredura precisa considerar todos os donos, mas não
     * concede esse alcance a uma sessão humana.
     */
    @PreAuthorize("hasRole('SERVICO')")
    @Transactional(transactionManager = Pools.CHAT_TRANSACTION_MANAGER, readOnly = true)
    public List<UUID> executar(Instant corte, int limite) {
        if (limite < 1) {
            throw new IllegalArgumentException("limite de atendimentos inativos deve ser positivo");
        }
        return List.copyOf(atendimentos.idsEmAtendimentoInativosAntesDe(corte, limite));
    }
}
