package com.synapse.crm.atendimento.application.internal;

import java.time.Instant;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.sharedkernel.persistencia.Pools;

/** Lista somente conversas nao finalizadas, sem carregar historico ou conteudo de mensagem. */
@Service
public class ListarAtendimentosEmAndamentoUseCase {

    private final AtendimentosEmAndamentoRepositorio repositorio;

    public ListarAtendimentosEmAndamentoUseCase(AtendimentosEmAndamentoRepositorio repositorio) {
        this.repositorio = repositorio;
    }

    @PreAuthorize("hasRole('SERVICO')")
    @Transactional(transactionManager = Pools.CHAT_TRANSACTION_MANAGER, readOnly = true)
    public AtendimentosEmAndamentoRepositorio.Pagina executar(
            Instant atividadeDesde, Instant atividadeAte, int pagina, int tamanho) {
        if (atividadeDesde != null && atividadeAte != null && atividadeDesde.isAfter(atividadeAte)) {
            throw new PeriodoDeAtividadeInvalidoException();
        }
        return repositorio.listar(
                new AtendimentosEmAndamentoRepositorio.Filtro(
                        atividadeDesde, atividadeAte, pagina, tamanho));
    }
}
