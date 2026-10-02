package com.synapse.crm.atendimento.application.origem;

import java.util.Locale;
import java.util.Optional;

/**
 * De onde veio uma mensagem automatica (E219). Fica em {@code mensagem_origem_automacao}, nunca em
 * {@code mensagem}: a tabela particionada nao ganha coluna (V85).
 */
public enum TipoDeOrigem {
    /** Resposta da IA a uma mensagem do lead. Nunca sofre cooldown nem teto. */
    RESPOSTA_IA(false),
    FOLLOW_UP(true),
    FIDELIZACAO(true),
    FESTIVA(true),
    ANIVERSARIO(true),
    AVALIACAO(true),
    /** Lembrete enviado ao lead (ex.: compromisso agendado). */
    LEMBRETE(true),
    /** Proativa que nao se encaixa nos tipos acima; a regra deve vir em {@code origemRegraId}. */
    OUTRO(true),
    /** Disparo de uma campanha de template em massa (E220), enfileirado pelo proprio CRM. Passa pela politica. */
    CAMPANHA(true),
    /** Mensagem agendada por um atendente e disparada pelo job do CRM. */
    PROGRAMADA(false),
    /** O chamador nao informou a origem (fluxo do n8n ainda nao atualizado). */
    NAO_INFORMADA(false);

    private final boolean proativa;

    TipoDeOrigem(boolean proativa) {
        this.proativa = proativa;
    }

    /** Proativa = a Automacao toma a iniciativa de falar com o lead; so estas passam pela politica. */
    public boolean proativa() {
        return proativa;
    }

    /** Os tipos que a Automacao pode declarar; PROGRAMADA, NAO_INFORMADA e CAMPANHA sao do proprio CRM. */
    public static Optional<TipoDeOrigem> declaradoPelaAutomacao(String valor) {
        if (valor == null || valor.isBlank()) {
            return Optional.empty();
        }
        try {
            TipoDeOrigem tipo = valueOf(valor.trim().toUpperCase(Locale.ROOT));
            return tipo == PROGRAMADA || tipo == NAO_INFORMADA || tipo == CAMPANHA
                    ? Optional.empty()
                    : Optional.of(tipo);
        } catch (IllegalArgumentException desconhecido) {
            return Optional.empty();
        }
    }
}
