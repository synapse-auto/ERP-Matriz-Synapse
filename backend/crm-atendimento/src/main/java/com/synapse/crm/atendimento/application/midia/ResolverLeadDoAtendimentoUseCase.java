package com.synapse.crm.atendimento.application.midia;

import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.atendimento.application.AtendimentoRepositorio;
import com.synapse.crm.atendimento.application.RecursoDeAtendimentoIndisponivelException;
import com.synapse.crm.sharedkernel.persistencia.Pools;

/**
 * O lead deste atendimento — separado de {@link EnviarMidiaUseCase} de proposito.
 *
 * <p>{@code AtendimentoRepositorio.porId} exige transacao ativa (RLS: sem ela a conexao nao recebe
 * {@code SET LOCAL ROLE} e a consulta rodaria como dona das tabelas). O controller de upload nao pode
 * chamar o repositorio direto — precisa de um metodo {@code @Transactional} de verdade, chamado de
 * fora (pelo proxy do Spring), nao por auto-invocacao dentro de {@code EnviarMidiaUseCase.executar}
 * (que so abre transacao mais tarde, dentro de {@code EnviarMensagemUseCase}, e de proposito nao
 * segura a conexao do chat durante a validacao e o upload do arquivo).
 */
@Service
public class ResolverLeadDoAtendimentoUseCase {

    private final AtendimentoRepositorio atendimentos;

    public ResolverLeadDoAtendimentoUseCase(AtendimentoRepositorio atendimentos) {
        this.atendimentos = atendimentos;
    }

    @PreAuthorize("isAuthenticated()")
    @Transactional(transactionManager = Pools.CHAT_TRANSACTION_MANAGER, readOnly = true)
    public UUID executar(UUID atendimentoId) {
        return atendimentos
                .porId(atendimentoId)
                .orElseThrow(
                        () -> new RecursoDeAtendimentoIndisponivelException("atendimento", atendimentoId))
                .leadId();
    }
}
