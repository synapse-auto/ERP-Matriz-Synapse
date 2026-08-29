package com.synapse.crm.equipe.application.chat;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.synapse.crm.sharedkernel.emoji.ResumoDeReacao;

/** Porta de reacoes do chat interno; consulta sempre em lote. */
public interface ReacaoDeChatInternoRepositorio {

    Map<UUID, List<ResumoDeReacao>> resumir(List<UUID> mensagemIds, UUID usuarioId);

    List<ResumoDeReacao> resumirUma(UUID mensagemId, UUID usuarioId);

    boolean definir(UUID conversaId, UUID mensagemId, UUID usuarioId, String emoji);

    void remover(UUID conversaId, UUID mensagemId, UUID usuarioId);
}
