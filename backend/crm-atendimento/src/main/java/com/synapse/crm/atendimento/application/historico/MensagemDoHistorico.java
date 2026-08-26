package com.synapse.crm.atendimento.application.historico;

import java.time.Instant;
import java.util.UUID;

import com.synapse.crm.atendimento.domain.mensagem.Mensagem;

/** Mensagem enriquecida exclusivamente para leitura, sem levar nome de usuario ao dominio. */
public record MensagemDoHistorico(
        Mensagem mensagem,
        String remetenteNome,
        UUID atendimentoId,
        Instant atendimentoIniciadoEm,
        Instant atendimentoFinalizadoEm,
        String atendimentoResponsavelNome) {}
