package com.synapse.crm.core.application.lead;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/** Porta estreita usada pelo contrato interno EV-05; nunca devolve ficha completa do lead. */
public interface AutomacaoEv05LeadRepositorio {

    EstadoResumo resumo(UUID leadId);

    EstadoPreenchimento preenchimento(UUID leadId);

    EscritaResumo gravarResumo(UUID leadId, String resumo, Instant contextoGeradoEm, Instant quando);

    EscritaPreenchimento aplicarPreenchimento(
            UUID leadId,
            String email,
            String cpf,
            String empresa,
            String localizacao,
            Instant quando,
            Set<String> invalidos);

    record EstadoResumo(
            UUID leadId, boolean existe, Instant atualizadoEm, Instant ultimaInteracaoEm) {}

    record EstadoPreenchimento(
            UUID leadId,
            Campo email,
            Campo cpf,
            Campo empresa,
            Campo localizacao,
            Instant ultimaAvaliacaoEm) {}

    record Campo(boolean preenchido, String origem) {}

    record EscritaResumo(UUID leadId, Instant atualizadoEm) {}

    record EscritaPreenchimento(
            UUID leadId,
            ResultadoCampo email,
            ResultadoCampo cpf,
            ResultadoCampo empresa,
            ResultadoCampo localizacao,
            Instant avaliadoEm) {}

    enum ResultadoCampo {
        APLICADO,
        IGNORADO_JA_PREENCHIDO,
        IGNORADO_INVALIDO,
        AUSENTE
    }
}
