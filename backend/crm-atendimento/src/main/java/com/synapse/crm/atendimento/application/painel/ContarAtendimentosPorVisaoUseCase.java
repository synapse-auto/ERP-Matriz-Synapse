package com.synapse.crm.atendimento.application.painel;

import java.util.EnumMap;
import java.util.Map;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.sharedkernel.identidade.UsuarioAutenticado;
import com.synapse.crm.sharedkernel.identidade.UsuarioContext;
import com.synapse.crm.sharedkernel.persistencia.Pools;

/**
 * Os badges das abas de Atendimentos (E17b §Bloco 6) — uma contagem por {@link VisaoAtendimento},
 * na mesma chamada, porque a tela mostra todas as abas do papel de uma vez.
 *
 * <p>Mesma regra de {@link ListarAtendimentosVisiveisUseCase}: quem pede nunca escolhe "de todos" —
 * o papel decide, sempre a partir do {@link UsuarioContext}.
 */
@Service
public class ContarAtendimentosPorVisaoUseCase {

    private final PainelDeAtendimentosRepositorio painel;
    private final UsuarioContext usuarioContext;

    public ContarAtendimentosPorVisaoUseCase(
            PainelDeAtendimentosRepositorio painel, UsuarioContext usuarioContext) {
        this.painel = painel;
        this.usuarioContext = usuarioContext;
    }

    @PreAuthorize("isAuthenticated()")
    @Transactional(transactionManager = Pools.CHAT_TRANSACTION_MANAGER, readOnly = true)
    public Map<VisaoAtendimento, Long> executar() {
        UsuarioAutenticado atual = usuarioContext.atual();
        boolean restritoAoProprioAtendente = !atual.enxergaTodosOsLeads();

        Map<VisaoAtendimento, Long> contagens = new EnumMap<>(VisaoAtendimento.class);
        for (VisaoAtendimento visao : VisaoAtendimento.values()) {
            contagens.put(visao, painel.contar(visao, atual.id(), restritoAoProprioAtendente));
        }
        return contagens;
    }
}
