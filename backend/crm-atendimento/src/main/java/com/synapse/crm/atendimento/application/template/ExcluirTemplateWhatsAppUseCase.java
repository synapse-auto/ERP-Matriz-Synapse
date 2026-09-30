package com.synapse.crm.atendimento.application.template;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import com.synapse.crm.atendimento.domain.canal.CanalGateway;
import com.synapse.crm.atendimento.domain.canal.ResultadoDeTemplate;

/** Exclui uma variante de template na conta WhatsApp da instancia. */
@Service
public class ExcluirTemplateWhatsAppUseCase {

    private final CanalGateway canal;
    private final AutorizacaoDeTemplates autorizacao;

    public ExcluirTemplateWhatsAppUseCase(CanalGateway canal, AutorizacaoDeTemplates autorizacao) {
        this.canal = canal;
        this.autorizacao = autorizacao;
    }

    @PreAuthorize("hasAnyRole('SUBGESTOR','GESTOR','ADMINISTRADOR') and @capacidades.permite('templates.excluir')")
    public void executar(String id, String nome) {
        if (id == null || id.isBlank() || nome == null || nome.isBlank()) {
            throw new PedidoDeTemplateInvalidoException("template exige id e nome");
        }
        autorizacao.exigirVarianteVisivel(id.trim(), nome.trim());
        ResultadoDeTemplate resultado = canal.excluirTemplate(id.trim(), nome.trim());
        if (resultado instanceof ResultadoDeTemplate.Recusado recusado) {
            throw new CanalRecusouTemplateException(recusado.motivo());
        }
        if (!(resultado instanceof ResultadoDeTemplate.Aceito)) {
            throw new CanalRecusouTemplateException("provedor nao devolveu resultado de exclusao");
        }
    }
}
