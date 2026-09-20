package com.synapse.crm.atendimento.application.painel;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.sharedkernel.identidade.UsuarioAutenticado;
import com.synapse.crm.sharedkernel.identidade.UsuarioContext;
import com.synapse.crm.sharedkernel.persistencia.Pools;

/**
 * A lista de conversas que alimenta a tela de Atendimentos.
 *
 * <p>Quem pede nunca escolhe "quero ver de todo mundo" — o papel decide isso, sempre a partir do
 * {@link UsuarioContext}, nunca de um parametro que o cliente poderia manipular.
 */
@Service
public class ListarAtendimentosVisiveisUseCase {

    private final PainelDeAtendimentosRepositorio painel;
    private final UsuarioContext usuarioContext;

    public ListarAtendimentosVisiveisUseCase(
            PainelDeAtendimentosRepositorio painel, UsuarioContext usuarioContext) {
        this.painel = painel;
        this.usuarioContext = usuarioContext;
    }

    @PreAuthorize("isAuthenticated()")
    @Transactional(transactionManager = Pools.CHAT_TRANSACTION_MANAGER, readOnly = true)
    public List<CartaoAtendimento> executar(VisaoAtendimento visao) {
        UsuarioAutenticado atual = usuarioContext.atual();
        visao.exigirAcesso(atual);
        boolean restritoAoProprioAtendente = !atual.enxergaTodosOsLeads();
        return painel.listar(visao, atual.id(), restritoAoProprioAtendente);
    }

    @PreAuthorize("isAuthenticated()")
    @Transactional(transactionManager = Pools.CHAT_TRANSACTION_MANAGER, readOnly = true)
    public List<CartaoAtendimento> executarPaginado(VisaoAtendimento visao, int limite,
            boolean depoisSemAtendimentoAberto, Instant depoisDe, UUID depoisDoId) {
        return executarPaginado(visao, limite, depoisSemAtendimentoAberto, depoisDe, depoisDoId, null);
    }

    @PreAuthorize("isAuthenticated()")
    @Transactional(transactionManager = Pools.CHAT_TRANSACTION_MANAGER, readOnly = true)
    public List<CartaoAtendimento> executarPaginado(VisaoAtendimento visao, int limite,
            boolean depoisSemAtendimentoAberto, Instant depoisDe, UUID depoisDoId,
            UUID filtroAtendenteId) {
        UsuarioAutenticado atual = usuarioContext.atual();
        visao.exigirAcesso(atual);
        if (filtroAtendenteId != null && visao == VisaoAtendimento.FINALIZADOS
                && !atual.enxergaTodosOsLeads() && !atual.id().equals(filtroAtendenteId)) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "atendente só pode filtrar os próprios atendimentos finalizados");
        }
        if (filtroAtendenteId == null) {
            return painel.listarPaginado(visao, atual.id(), !atual.enxergaTodosOsLeads(),
                    depoisSemAtendimentoAberto, depoisDe, depoisDoId, limite);
        }
        return painel.listarPaginado(visao, atual.id(), !atual.enxergaTodosOsLeads(),
                depoisSemAtendimentoAberto, depoisDe, depoisDoId, limite, filtroAtendenteId);
    }
}
