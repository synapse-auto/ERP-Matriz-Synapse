package com.synapse.crm.atendimento.domain.mensagem;

/** Tipo da mensagem. Espelha o ENUM {@code tipo_mensagem} do banco. */
public enum TipoMensagem {
    TEXTO,
    AUDIO,
    IMAGEM,
    DOCUMENTO,
    BOTOES,
    LISTA;

    /** Tipos de mídia carregam arquivo e precisam de {@code midiaUrl}. */
    public boolean exigeMidia() {
        return this == AUDIO || this == IMAGEM || this == DOCUMENTO;
    }

    public boolean exigeOpcoes() {
        return this == BOTOES || this == LISTA;
    }
}
