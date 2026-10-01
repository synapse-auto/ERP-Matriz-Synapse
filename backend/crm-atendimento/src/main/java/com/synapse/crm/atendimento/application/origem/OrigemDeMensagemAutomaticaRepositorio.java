package com.synapse.crm.atendimento.application.origem;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/** Porta de {@code mensagem_origem_automacao} (V85). */
public interface OrigemDeMensagemAutomaticaRepositorio {

    /** Idempotente por mensagem: repetir o registro nao duplica nem altera a origem gravada. */
    void registrar(UUID mensagemId, UUID atendimentoId, UUID leadId, Instant enviadoEm, OrigemDaMensagem origem);

    /**
     * Mensagens e leads distintos por dia (no fuso informado) e origem, em {@code [de, ate]}.
     * Mensagens da IA sem linha de origem (anteriores a V85 ou de caminho nao coberto) aparecem como
     * {@link #SEM_ORIGEM_REGISTRADA}.
     */
    List<TotalPorOrigem> totaisPorDia(LocalDate de, LocalDate ate, ZoneId zona);

    String SEM_ORIGEM_REGISTRADA = "SEM_ORIGEM_REGISTRADA";

    record TotalPorOrigem(LocalDate dia, String origem, long mensagens, long leads) {}
}
