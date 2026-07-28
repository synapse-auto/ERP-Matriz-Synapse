package com.synapse.crm.atendimento.domain.mensagem;

/** Midia da mensagem. Espelha o ENUM {@code tipo_mensagem} do banco. */
public enum TipoMensagem {
    TEXTO,
    AUDIO,
    IMAGEM,
    DOCUMENTO;

    /** Fora de {@link #TEXTO}, a mensagem carrega arquivo e precisa de {@code midiaUrl}. */
    public boolean exigeMidia() {
        return this != TEXTO;
    }
}
