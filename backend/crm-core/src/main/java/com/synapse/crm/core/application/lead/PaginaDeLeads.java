package com.synapse.crm.core.application.lead;

import java.util.List;

import com.synapse.crm.core.domain.lead.LeadResumo;

/**
 * Uma pagina do filtro modular (E16 §Bloco 1) — mesmo formato de {@code PaginaLembretes} e {@code
 * PaginaMensagensProgramadas}: uma base real de leads nao cabe numa tela, entao a tela nunca recebe
 * mais que {@code tamanho} linhas de uma vez.
 *
 * <p>{@code temMais} vem de buscar {@code tamanho + 1} linhas e cortar a extra, nao de um {@code
 * COUNT} adicional — o total exato, quando a tela precisa dele, e o trabalho de {@code
 * ContarLeadsFiltradosUseCase}.
 */
public record PaginaDeLeads(List<LeadResumo> leads, int pagina, boolean temMais) {}
