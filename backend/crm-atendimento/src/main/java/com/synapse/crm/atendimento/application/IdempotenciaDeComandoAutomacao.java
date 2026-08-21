package com.synapse.crm.atendimento.application;

import java.util.UUID;

/** Reserva persistida para tornar os comandos internos do n8n seguros para retry. */
public interface IdempotenciaDeComandoAutomacao {

    Reserva reservar(String chave, String operacao, UUID atendimentoId, String hashDaRequisicao);

    void concluir(String chave, String respostaJson);

    record Reserva(
            boolean nova,
            String chave,
            String operacao,
            UUID atendimentoId,
            String hashDaRequisicao,
            String respostaJson) {}
}
