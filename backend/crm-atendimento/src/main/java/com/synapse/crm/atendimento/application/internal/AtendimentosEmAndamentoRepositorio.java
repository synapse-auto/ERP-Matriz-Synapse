package com.synapse.crm.atendimento.application.internal;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.synapse.crm.atendimento.domain.atendimento.StatusAtendimento;

/** Read model minimo dos atendimentos que a Automacao ainda pode processar. */
public interface AtendimentosEmAndamentoRepositorio {

    Pagina listar(Filtro filtro);

    record Filtro(Instant atividadeDesde, Instant atividadeAte, int pagina, int tamanho) {}

    record Item(
            UUID atendimentoId,
            UUID leadId,
            StatusAtendimento status,
            Responsavel responsavel,
            Instant ultimaMensagemEm) {}

    record Responsavel(UUID id, String nome) {}

    record Pagina(List<Item> atendimentos, int pagina, int tamanho, boolean temMais) {
        public Pagina {
            atendimentos = List.copyOf(atendimentos);
        }
    }
}
