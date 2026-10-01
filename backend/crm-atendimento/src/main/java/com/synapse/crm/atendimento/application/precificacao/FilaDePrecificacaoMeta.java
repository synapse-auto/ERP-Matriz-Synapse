package com.synapse.crm.atendimento.application.precificacao;

import java.util.List;
import java.util.Optional;

import com.synapse.crm.atendimento.domain.canal.TradutorDeCanal.PrecificacaoObservada;

/** Entrada duravel e apuracao separada dos eventos de entrega da Meta. */
public interface FilaDePrecificacaoMeta {

    void registrar(List<PrecificacaoObservada> observacoes);

    Optional<String> proximoEventoId();

    boolean processar(String idEvento);

    void reagendar(String idEvento);
}
