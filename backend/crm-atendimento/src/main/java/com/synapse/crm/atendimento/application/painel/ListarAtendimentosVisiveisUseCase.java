package com.synapse.crm.atendimento.application.painel;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    static final String MARCADOR_TRUNCADA = "[LISTAGEM_PAINEL_TRUNCADA]";
    private static final Logger log = LoggerFactory.getLogger(ListarAtendimentosVisiveisUseCase.class);

    private final PainelDeAtendimentosRepositorio painel;
    private final UsuarioContext usuarioContext;

    public ListarAtendimentosVisiveisUseCase(
            PainelDeAtendimentosRepositorio painel, UsuarioContext usuarioContext) {
        this.painel = painel;
        this.usuarioContext = usuarioContext;
    }

    /**
     * A lista simples tem teto ({@code synapse.painel.listagem-maxima}). Passou do teto, o corte nao e silencioso: o
     * resultado vem marcado como truncado (o controller devolve {@code X-Lista-Truncada}) e grava-se um WARN
     * {@code [LISTAGEM_PAINEL_TRUNCADA]} com papel, aba e teto — sem dado pessoal.
     */
    @PreAuthorize("isAuthenticated()")
    @Transactional(transactionManager = Pools.CHAT_TRANSACTION_MANAGER, readOnly = true)
    public ListaDoPainel executar(VisaoAtendimento visao) {
        UsuarioAutenticado atual = usuarioContext.atual();
        visao.exigirAcesso(atual);
        boolean restritoAoProprioAtendente = !atual.enxergaTodosOsLeads();
        ListaDoPainel lista = painel.listar(visao, atual.id(), restritoAoProprioAtendente);
        if (lista.truncada()) {
            log.warn("{} papel={} aba={} teto={} (a lista tem mais cartoes do que o teto; o resto nao foi devolvido)",
                    MARCADOR_TRUNCADA, atual.papel(), visao, lista.teto());
        }
        return lista;
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
