package com.synapse.crm.relatorios.domain.cancelamentos;

import java.time.Instant;

/** Projecao minima de leitura. Registro nao significa ocorrencia efetiva do cancelamento. */
public record CancelamentoAgendamento(
        String id, Instant agendadoPara, Instant registradoEm,
        String contatoNome, String profissionalNome, String tipoAgendamento, String descricao) {

    public boolean temDescricao() {
        return descricao != null && !descricao.isBlank();
    }
}
