package com.synapse.crm.core.domain.tag;

import java.util.Objects;
import java.util.UUID;

/**
 * Etiqueta aplicavel a um lead (RN-CRM-03).
 *
 * <p>Tags sao da operacao inteira, nao de um atendente. Nao existe tag pessoal: por isso nao ha
 * regra de visibilidade aqui, e a restricao e de <em>escrita</em> — so gestao cria e altera, para
 * que a taxonomia nao vire uma bagunca com quinze variacoes de "orcamento".
 */
public record Tag(UUID id, String nome, String cor, String icone) {

    public Tag {
        Objects.requireNonNull(id, "id da tag e obrigatorio");
        Objects.requireNonNull(nome, "nome da tag e obrigatorio");
        Objects.requireNonNull(cor, "cor da tag e obrigatoria");
        if (nome.isBlank()) {
            throw new IllegalArgumentException("nome da tag nao pode ser vazio");
        }
    }

    public static Tag nova(String nome, String cor, String icone) {
        return new Tag(UUID.randomUUID(), nome, cor, icone);
    }

    public Tag com(String nome, String cor, String icone) {
        return new Tag(id, nome, cor, icone);
    }
}
