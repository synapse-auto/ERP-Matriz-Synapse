package com.synapse.crm.atendimento.application.template;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import com.synapse.crm.atendimento.domain.canal.CanalGateway;
import com.synapse.crm.atendimento.domain.canal.PedidoDeEdicaoDeTemplate;
import com.synapse.crm.atendimento.domain.canal.ResultadoDeTemplate;

/** Edita o corpo de um template textual existente no provedor ativo. */
@Service
public class EditarTemplateWhatsAppUseCase {

    private final CanalGateway canal;

    public EditarTemplateWhatsAppUseCase(CanalGateway canal) {
        this.canal = canal;
    }

    @PreAuthorize("hasAnyRole('SUBGESTOR','GESTOR','ADMINISTRADOR')")
    public void executar(String id, String corpo) {
        if (id == null || id.isBlank()) {
            throw new PedidoDeTemplateInvalidoException("template exige um id");
        }
        ResultadoDeTemplate resultado = canal.editarTemplate(
                new PedidoDeEdicaoDeTemplate(id.trim(), CriarTemplateWhatsAppUseCase.validarCorpo(corpo)));
        if (resultado instanceof ResultadoDeTemplate.Recusado recusado) {
            throw new CanalRecusouTemplateException(recusado.motivo());
        }
        if (!(resultado instanceof ResultadoDeTemplate.Aceito)) {
            throw new CanalRecusouTemplateException("provedor nao devolveu resultado de edicao");
        }
    }
}
