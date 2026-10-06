package com.synapse.crm.equipe.domain.usuario;

/** Resultado de gravar uma presenca: de onde veio e para onde foi. {@link #mudou()} falso = o estado ja era esse. */
public record MudancaDePresenca(StatusPresenca anterior, StatusPresenca novo) {

    public boolean mudou() {
        return anterior != novo;
    }
}
