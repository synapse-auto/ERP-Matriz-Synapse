package com.synapse.crm.atendimento.application.template;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import com.synapse.crm.atendimento.domain.canal.CanalGateway;
import com.synapse.crm.atendimento.domain.canal.TemplateDoCanal;

/**
 * Lista os templates do provedor ativo que o papel atual alcanca. Nao toca o banco do chat.
 *
 * <p>O recorte de templates restritos acontece aqui, no servidor: o navegador de quem nao e
 * administrador nunca recebe o item.
 */
@Service
public class ListarTemplatesWhatsAppUseCase {

    private final CanalGateway canal;
    private final AutorizacaoDeTemplates autorizacao;

    public ListarTemplatesWhatsAppUseCase(CanalGateway canal, AutorizacaoDeTemplates autorizacao) {
        this.canal = canal;
        this.autorizacao = autorizacao;
    }

    @PreAuthorize("isAuthenticated() and @capacidades.permite('templates.ver')")
    public List<TemplateDoCanal> executar() {
        return autorizacao.visiveis(canal.listarTemplates());
    }
}
