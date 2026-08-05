package com.synapse.crm.core.interfaces.lead;

import java.time.Instant;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

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
        @Schema(description = "Identificador do lead.") UUID id,
        @Schema(description = "Nome do lead.") String nome,
        @Schema(description = "Telefone principal.") String telefone,
        @Schema(description = "Empresa, quando informada.") String empresa,
        @Schema(description = "Status básico.") StatusBasicoLead status,
        @Schema(description = "Etapa atual do funil.") UUID etapaAtendimentoId,
        @Schema(description = "Atendente responsável; nulo quando o lead está com a IA.") UUID atendenteResponsavelId,
        @Schema(description = "Quantidade consolidada de atendimentos.") int numAtendimentos,
        @Schema(description = "Quantidade consolidada de mensagens.") int numMensagens,
        @Schema(description = "Instante de criação em UTC.") Instant criadoEm) {

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
