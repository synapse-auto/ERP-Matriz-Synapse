package com.synapse.crm.relatorios.domain.cancelamentos;

import java.time.Instant;
import java.util.UUID;

/** Projecao minima; recebidoEm e ingestao, canceladoEm e ocorrencia conhecida. registradoEm e alias legado. */
public record CancelamentoAgendamento(
        String id, Instant agendadoPara, Instant registradoEm,
        String contatoNome, String profissionalNome, String tipoAgendamento, String descricao,
        Instant recebidoEm, Instant canceladoEm, UUID leadId, UUID atendimentoId) {

    /** Compatibilidade de fixtures anteriores; producao projeta todos os campos explicitamente. */
    public CancelamentoAgendamento(String id, Instant agendadoPara, Instant registradoEm,
            String contatoNome, String profissionalNome, String tipoAgendamento, String descricao) {
        this(id, agendadoPara, registradoEm, contatoNome, profissionalNome, tipoAgendamento, descricao,
                registradoEm, registradoEm, null, null);
    }

    public boolean temDescricao() {
        return descricao != null && !descricao.isBlank();
    }
}
