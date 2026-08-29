package com.synapse.crm.atendimento.application.reacao;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.synapse.crm.sharedkernel.emoji.ResumoDeReacao;

/** Porta de reacoes da mensagem de atendimento; consulta sempre em lote. */
public interface ReacaoDeMensagemRepositorio {

    Map<Chave, List<ResumoDeReacao>> resumir(List<Chave> chaves, UUID usuarioId);

    List<ResumoDeReacao> resumirUma(Chave chave, UUID usuarioId);

    boolean definir(Chave chave, UUID atendimentoId, UUID usuarioId, String emoji);

    void remover(Chave chave, UUID atendimentoId, UUID usuarioId);

    record Chave(UUID mensagemId, Instant enviadoEm) {
        public Chave {
            enviadoEm = enviadoEm.truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        }
    }
}
