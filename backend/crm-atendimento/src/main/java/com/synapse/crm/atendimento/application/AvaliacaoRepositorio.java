package com.synapse.crm.atendimento.application;

import java.util.Optional;
import java.util.UUID;

import com.synapse.crm.atendimento.domain.avaliacao.Avaliacao;

/** Persistencia de {@code avaliacao}. Sem {@code findAll}: so o atendimento visivel ou a escrita. */
public interface AvaliacaoRepositorio {

    Optional<Avaliacao> porAtendimento(UUID atendimentoId);

    Avaliacao salvar(Avaliacao avaliacao);

    ResultadoSalvar salvarSeAusente(Avaliacao avaliacao);

    record ResultadoSalvar(Avaliacao avaliacao, boolean inserido) {}
}
