package com.synapse.crm.atendimento.application.painel;

import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.atendimento.application.RecursoDeAtendimentoIndisponivelException;
import com.synapse.crm.core.application.lead.ObterLeadIdPorTelefoneVisivelUseCase;
import com.synapse.crm.core.domain.lead.TelefoneCanonico;
import com.synapse.crm.sharedkernel.identidade.UsuarioContext;
import com.synapse.crm.sharedkernel.persistencia.Pools;

/** Busca pontual o cartão representativo de um lead por id ou telefone canônico. */
@Service
public class ObterCartaoAtendimentoPorLeadOuTelefoneUseCase {

    private final PainelDeAtendimentosRepositorio painel;
    private final ObterLeadIdPorTelefoneVisivelUseCase obterLeadPorTelefone;
    private final TelefoneCanonico telefoneCanonico;
    private final UsuarioContext usuarioContext;

    public ObterCartaoAtendimentoPorLeadOuTelefoneUseCase(
            PainelDeAtendimentosRepositorio painel,
            ObterLeadIdPorTelefoneVisivelUseCase obterLeadPorTelefone,
            TelefoneCanonico telefoneCanonico,
            UsuarioContext usuarioContext) {
        this.painel = painel;
        this.obterLeadPorTelefone = obterLeadPorTelefone;
        this.telefoneCanonico = telefoneCanonico;
        this.usuarioContext = usuarioContext;
    }

    @PreAuthorize("isAuthenticated()")
    @Transactional(transactionManager = Pools.CHAT_TRANSACTION_MANAGER, readOnly = true)
    public CartaoAtendimento executar(UUID leadId, String telefone) {
        if ((leadId == null) == (telefone == null)) {
            throw new IllegalArgumentException("informe exatamente leadId ou telefone");
        }

        UUID id = leadId;
        if (telefone != null) {
            String canonico = telefoneCanonico.normalizar(telefone);
            id = obterLeadPorTelefone.executar(canonico)
                    .orElseThrow(() -> new RecursoDeAtendimentoIndisponivelException("lead"));
        }

        UUID usuarioId = usuarioContext.atual().id();
        return painel.porLeadId(id, usuarioId)
                .orElseThrow(() -> new RecursoDeAtendimentoIndisponivelException("lead"));
    }
}
