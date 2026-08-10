package com.synapse.crm.core.application.tag;

import java.util.List;

import com.synapse.crm.core.domain.tag.ContagemDeTag;

/**
 * O mini-dashboard de Tags (E17b §Bloco 6): total de leads visiveis, quantos tem pelo menos uma tag,
 * e a contagem por tag — da mais usada para a menos usada.
 *
 * <p>Percentual e "tag mais usada" nao moram aqui de proposito: sao derivados de {@code porTag} e
 * {@code leadsComTag}/{@code totalLeadsVisiveis}, e calcular na camada de apresentacao evita um
 * numero congelado que discorda do resto do objeto se um dos dois lados mudar sem o outro.
 */
public record AgregacaoDeTags(long totalLeadsVisiveis, long leadsComTag, List<ContagemDeTag> porTag) {

    public AgregacaoDeTags {
        porTag = List.copyOf(porTag);
    }
}
