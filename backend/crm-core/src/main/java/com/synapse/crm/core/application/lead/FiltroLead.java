package com.synapse.crm.core.application.lead;

import com.synapse.crm.core.domain.lead.StatusBasicoLead;

/**
 * Criterios opcionais de consulta de lead.
 *
 * <p>Nunca inclui "de quem e o lead": isso nao e escolha de quem consulta, e consequencia de quem
 * esta autenticado. Deixar o chamador informar o atendente abriria exatamente o buraco que a
 * RN-CRM-01 fecha.
 *
 * @param termoBusca trecho do nome, opcional
 * @param status estado macro, opcional
 */
public record FiltroLead(String termoBusca, StatusBasicoLead status) {

    public static FiltroLead nenhum() {
        return new FiltroLead(null, null);
    }

    public boolean temTermoBusca() {
        return termoBusca != null && !termoBusca.isBlank();
    }
}
