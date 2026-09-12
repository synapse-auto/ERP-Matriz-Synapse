package com.synapse.crm.atendimento.application.painel;

import java.util.List;

import com.synapse.crm.atendimento.application.participacao.ParticipanteAtendimento;

/** Snapshot autorizado que governa cabeçalho, participantes, seleção e permissão do composer. */
public record EstadoAtendimentoSelecionado(
        CartaoAtendimento cartao,
        long versao,
        List<ParticipanteAtendimento> participantes,
        boolean usuarioAtualEhResponsavel,
        boolean usuarioAtualParticipa,
        boolean podeEnviar) {

    public EstadoAtendimentoSelecionado {
        participantes = participantes == null ? List.of() : List.copyOf(participantes);
    }
}
