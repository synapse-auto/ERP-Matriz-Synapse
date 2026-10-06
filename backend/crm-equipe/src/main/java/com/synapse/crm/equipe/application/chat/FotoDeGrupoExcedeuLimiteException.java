package com.synapse.crm.equipe.application.chat;

/** Arquivo acima do limite de imagem configurado em {@code anexo.tamanho_maximo_imagem_mb}. */
public class FotoDeGrupoExcedeuLimiteException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public FotoDeGrupoExcedeuLimiteException(long limiteEmBytes) {
        super("a foto excede o limite de " + limiteEmBytes + " bytes");
    }
}
