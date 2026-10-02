package com.synapse.crm.campanhas.application;

/**
 * Campanhas existem nesta instancia? A funcionalidade esta habilitada E o canal ativo administra templates
 * (capacidade {@code gerenciaTemplates}, nunca o nome do provedor).
 */
public interface DisponibilidadeDeCampanhas {

    boolean disponivel();

    /** Lanca {@link CampanhasIndisponiveisException} quando nao ha campanhas aqui. */
    default void exigir() {
        if (!disponivel()) {
            throw new CampanhasIndisponiveisException();
        }
    }
}
