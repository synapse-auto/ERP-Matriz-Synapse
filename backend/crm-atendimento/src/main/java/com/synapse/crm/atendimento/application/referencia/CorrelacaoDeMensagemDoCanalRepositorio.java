package com.synapse.crm.atendimento.application.referencia;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.synapse.crm.atendimento.application.MensagemRepositorio.StatusDeEntregaAplicado;
import com.synapse.crm.atendimento.domain.canal.IdentidadeDeMensagemDoCanal;
import com.synapse.crm.atendimento.domain.canal.TradutorDeCanal.StatusComIdentidade;

/** Porta aditiva; nao reescreve IDs ou referencias legadas. */
public interface CorrelacaoDeMensagemDoCanalRepositorio {
    /** False preserva o vinculo anterior; conflito nao deve desfazer um aceite externo. */
    boolean registrar(IdentidadeDeMensagemDoCanal identidade, UUID mensagemId, Instant enviadoEm, UUID atendimentoId);
    void observar(StatusComIdentidade status);
    Optional<StatusDeEntregaAplicado> aplicar(IdentidadeDeMensagemDoCanal identidade);
}
