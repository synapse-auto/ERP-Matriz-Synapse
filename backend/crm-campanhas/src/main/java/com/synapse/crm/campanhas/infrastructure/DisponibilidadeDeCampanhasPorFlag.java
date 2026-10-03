package com.synapse.crm.campanhas.infrastructure;

import org.springframework.stereotype.Component;

import com.synapse.crm.atendimento.domain.canal.CanalGateway;
import com.synapse.crm.campanhas.application.DisponibilidadeDeCampanhas;
import com.synapse.crm.sharedkernel.permissao.ConsultaDeFuncionalidades;

/**
 * Campanhas aparecem so quando a funcionalidade {@code campanhas} esta habilitada E o canal ativo administra
 * templates ({@code gerenciaTemplates}). A decisao e por capacidade do canal, nunca pelo nome do provedor ou do
 * cliente: um filho com outro provedor oficial ganha o recurso sem tocar no core.
 */
@Component("disponibilidadeDeCampanhas")
public class DisponibilidadeDeCampanhasPorFlag implements DisponibilidadeDeCampanhas {

    static final String FUNCIONALIDADE = "campanhas";

    private final ConsultaDeFuncionalidades funcionalidades;
    private final CanalGateway canal;

    DisponibilidadeDeCampanhasPorFlag(ConsultaDeFuncionalidades funcionalidades, CanalGateway canal) {
        this.funcionalidades = funcionalidades;
        this.canal = canal;
    }

    @Override
    public boolean disponivel() {
        return canal.gerenciaTemplates() && funcionalidades.habilitadas().contains(FUNCIONALIDADE);
    }
}
