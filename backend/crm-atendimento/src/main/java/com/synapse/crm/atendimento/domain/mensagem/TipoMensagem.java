package com.synapse.crm.atendimento.domain.mensagem;

/** Tipo da mensagem. Espelha o ENUM {@code tipo_mensagem} do banco. */
public enum TipoMensagem {
    TEXTO,
    AUDIO,
    IMAGEM,
    DOCUMENTO,
    VIDEO,
    BOTOES,
    LISTA,
    LOCALIZACAO,
    /** Cartão de contato compartilhado pelo cliente: nome e telefones em {@code midiaMetadados}. */
    CONTATO;

    /** Tipos de mídia carregam arquivo e precisam de {@code midiaUrl}. */
    public boolean exigeMidia() {
        return this == AUDIO || this == IMAGEM || this == DOCUMENTO || this == VIDEO;
    }

    public boolean exigeOpcoes() {
        return this == BOTOES || this == LISTA;
    }

    /**
     * Tipos que armazenam dados estruturados em {@code midiaMetadados} em vez de texto
     * livre em {@code conteudo}. Não exigem {@code midiaUrl}.
     */
    public boolean exigeMetadados() {
        return this == LOCALIZACAO || this == CONTATO;
    }
}
