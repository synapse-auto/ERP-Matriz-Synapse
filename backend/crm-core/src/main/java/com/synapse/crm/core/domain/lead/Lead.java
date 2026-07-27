package com.synapse.crm.core.domain.lead;

import java.util.Objects;
import java.util.UUID;

/**
 * Lead: a pessoa que procurou a empresa.
 *
 * <p>Java puro — sem JPA, sem Spring. O mapeamento para tabela mora em infrastructure.
 *
 * <p>Nesta etapa carrega so o necessario para a regra de visibilidade (RN-CRM-01). Os demais campos
 * do modelo entram quando houver caso de uso que os leia.
 */
public record Lead(UUID id, String nome, StatusBasicoLead statusBasico, UUID atendenteResponsavelId) {

    public Lead {
        Objects.requireNonNull(id, "id do lead e obrigatorio");
        Objects.requireNonNull(nome, "nome do lead e obrigatorio");
        Objects.requireNonNull(statusBasico, "status basico do lead e obrigatorio");
    }

    /** RN-CRM-02: lead atribuido a um atendente pertence a ele. */
    public boolean pertenceA(UUID atendenteId) {
        return atendenteResponsavelId != null && atendenteResponsavelId.equals(atendenteId);
    }

    /** Sem dono: esta no grupo "Potenciais" e qualquer atendente pode pegar. */
    public boolean estaDisponivelParaTodos() {
        return statusBasico == StatusBasicoLead.IA;
    }
}
