package com.synapse.crm.core.domain.tag;

import java.util.Objects;

/**
 * Quantos leads (dentro de um recorte de visibilidade ja resolvido por quem chama) usam uma tag
 * (E17b §Bloco 6 — mini-dashboard de Tags).
 *
 * <p>Nao carrega o proprio recorte: {@code quantidade} so faz sentido junto do conjunto de leads que
 * originou a contagem, que e responsabilidade de quem monta este objeto, nao dele.
 */
public record ContagemDeTag(Tag tag, long quantidade) {

    public ContagemDeTag {
        Objects.requireNonNull(tag, "tag e obrigatoria");
        if (quantidade < 0) {
            throw new IllegalArgumentException("quantidade nao pode ser negativa");
        }
    }
}
