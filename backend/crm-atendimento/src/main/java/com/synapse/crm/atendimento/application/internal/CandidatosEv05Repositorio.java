package com.synapse.crm.atendimento.application.internal;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Read model minimo do ciclo EV-05: apenas atendimentos humanos ainda abertos. */
public interface CandidatosEv05Repositorio {
    Pagina listar(int pagina, int tamanho, Instant atualizadoDesde);

    record Item(UUID atendimentoId, UUID leadId, String situacao, Instant atualizadoEm) {}

    record Pagina(List<Item> itens, int pagina, int tamanho, boolean temMais) {
        public Pagina {
            itens = List.copyOf(itens);
        }
    }
}
