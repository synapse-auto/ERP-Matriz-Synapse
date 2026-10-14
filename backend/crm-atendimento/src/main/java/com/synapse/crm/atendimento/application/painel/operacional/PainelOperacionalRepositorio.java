package com.synapse.crm.atendimento.application.painel.operacional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.synapse.crm.core.domain.lead.VisibilidadeLead;

/** A Specification é obrigatória em todas as leituras, além da RLS da transação. */
public interface PainelOperacionalRepositorio {
    int limiteMinutos();
    List<PainelOperacional.Linha> agregar(VisibilidadeLead visibilidade, UUID usuarioId,
            Instant agora, Instant inicioDia, Instant fimDia, int limiteMinutos, FiltroOperacional filtro);
    List<PainelOperacional.Conversa> listar(VisibilidadeLead visibilidade, UUID usuarioId,
            Instant agora, Instant inicioDia, Instant fimDia, int limiteMinutos,
            FiltroOperacional filtro, Instant depoisDe, UUID depoisId, int tamanho);
}
