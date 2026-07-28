package com.synapse.crm.core.domain.lead;

import java.time.Instant;
import java.util.UUID;

/**
 * Lead como aparece em lista: Kanban, "Ativos", "Pendentes", resultado de busca.
 *
 * <p><strong>Nao tem {@code notas} nem {@code resumoIa} de proposito.</strong> Sao campos de texto
 * longo; carrega-los em tela de lista transfere megabytes para exibir um nome. A protecao e por
 * tipo: o repositorio devolve este record na listagem e simplesmente nao tem onde por os campos
 * longos, entao ninguem os traz por descuido.
 */
public record LeadResumo(
        UUID id,
        String nome,
        String telefone,
        String empresa,
        StatusBasicoLead statusBasico,
        UUID etapaAtendimentoId,
        UUID atendenteResponsavelId,
        int numAtendimentos,
        int numMensagens,
        Instant criadoEm) {}
