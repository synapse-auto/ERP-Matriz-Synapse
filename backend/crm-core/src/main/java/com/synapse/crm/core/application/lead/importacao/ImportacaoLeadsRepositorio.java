package com.synapse.crm.core.application.lead.importacao;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Porta transacional da importacao HTTP; a implementacao permanece no adaptador SQL. */
public interface ImportacaoLeadsRepositorio {

    Set<String> telefonesExistentes(Collection<String> telefones);

    int inserir(List<LeadParaInsercao> leads);

    record LeadParaInsercao(
            String nome,
            String telefone,
            String empresa,
            String cpf,
            String localizacao,
            UUID etapaId,
            List<UUID> tags) {

        public LeadParaInsercao {
            tags = tags == null ? List.of() : List.copyOf(tags);
        }
    }
}
