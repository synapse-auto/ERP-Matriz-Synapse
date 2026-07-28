package com.synapse.crm.core.interfaces.lead;

import java.time.Instant;
import java.util.UUID;

import com.synapse.crm.core.domain.lead.LeadResumo;
import com.synapse.crm.core.domain.lead.StatusBasicoLead;

/**
 * O que qualquer listagem de lead devolve.
 *
 * <p>Sem {@code notas} e sem {@code resumoIa}: nao existem neste tipo. A listagem simples e o filtro
 * modular compartilham este record de proposito — duas formas do mesmo dado seriam duas chances de
 * uma delas ganhar um campo longo sem ninguem notar.
 */
record LeadDaLista(
        UUID id,
        String nome,
        String telefone,
        String empresa,
        StatusBasicoLead status,
        UUID etapaAtendimentoId,
        UUID atendenteResponsavelId,
        int numAtendimentos,
        int numMensagens,
        Instant criadoEm) {

    static LeadDaLista de(LeadResumo lead) {
        return new LeadDaLista(
                lead.id(),
                lead.nome(),
                lead.telefone(),
                lead.empresa(),
                lead.statusBasico(),
                lead.etapaAtendimentoId(),
                lead.atendenteResponsavelId(),
                lead.numAtendimentos(),
                lead.numMensagens(),
                lead.criadoEm());
    }
}
