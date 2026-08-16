package com.synapse.crm.atendimento.application.historico;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Porta de leitura paginada do historico, separada do caminho critico de escrita. */
public interface HistoricoDeMensagensRepositorio {

    List<MensagemDoHistorico> anteriores(
            UUID atendimentoId, Instant cursorEnviadoEm, UUID cursorId, int limite);

    List<MensagemDoHistorico> desde(UUID atendimentoId, Instant desde);
}
