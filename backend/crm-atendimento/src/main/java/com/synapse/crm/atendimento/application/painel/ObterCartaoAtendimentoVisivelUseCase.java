package com.synapse.crm.atendimento.application.painel;

import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.atendimento.application.RecursoDeAtendimentoIndisponivelException;
import com.synapse.crm.sharedkernel.identidade.UsuarioContext;
import com.synapse.crm.sharedkernel.persistencia.Pools;

/**
 * Resolve o cartão de uma abertura já autorizada sem reaplicar a visão operacional da lista.
 *
 * <p>A autorização continua no banco: a consulta parte de {@code atendimento}, protegido por RLS,
 * e devolve 404 tanto para um id inexistente quanto para um recurso fora do alcance da sessão.
 */
@Service
public class ObterCartaoAtendimentoVisivelUseCase {
    private final PainelDeAtendimentosRepositorio painel;
    private final UsuarioContext usuarioContext;

    public ObterCartaoAtendimentoVisivelUseCase(
            PainelDeAtendimentosRepositorio painel, UsuarioContext usuarioContext) {
        this.painel = painel;
        this.usuarioContext = usuarioContext;
    }

    @PreAuthorize("isAuthenticated()")
    @Transactional(transactionManager = Pools.CHAT_TRANSACTION_MANAGER, readOnly = true)
    public CartaoAtendimento executar(UUID atendimentoId) {
        return painel.porAtendimentoId(atendimentoId, usuarioContext.atual().id())
                .orElseThrow(() -> new RecursoDeAtendimentoIndisponivelException(
                        "atendimento", atendimentoId));
    }
}
