package com.synapse.crm.core.application.lead;

import java.util.Optional;
import java.util.UUID;

/**
 * Reserva persistida para tornar idempotentes os comandos internos da Automacao sobre um lead
 * (etapa, data de nascimento) que nao dependem de um atendimento em andamento.
 *
 * <p>Mesmo desenho de {@code IdempotenciaDeComandoAutomacao} (crm-atendimento), com o escopo trocado
 * de {@code atendimentoId} para {@code leadId}: o EV-05 reserva por atendimento porque so age
 * enquanto um atendimento EM_ATENDIMENTO existe; etapa e data de nascimento nao tem essa restricao
 * (ver decisao no relatorio da E196), entao a chave persistente e o proprio lead.
 */
public interface IdempotenciaDeComandoDeLead {

    /** Procura uma reserva existente antes de validar o recurso referenciado. */
    Optional<Reserva> buscar(String chave);

    Reserva reservar(String chave, String operacao, UUID leadId, String hashDaRequisicao);

    void concluir(String chave, String respostaJson);

    record Reserva(
            boolean nova,
            String chave,
            String operacao,
            UUID leadId,
            String hashDaRequisicao,
            String respostaJson) {}
}
