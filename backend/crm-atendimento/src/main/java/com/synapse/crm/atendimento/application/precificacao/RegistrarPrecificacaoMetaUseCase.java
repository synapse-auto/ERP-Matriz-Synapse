package com.synapse.crm.atendimento.application.precificacao;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.atendimento.domain.canal.TradutorDeCanal.PrecificacaoObservada;

/** Somente enfileira a observacao. Falha aqui nunca deve rejeitar um webhook autenticado. */
@Service
public class RegistrarPrecificacaoMetaUseCase {

    private final FilaDePrecificacaoMeta fila;

    public RegistrarPrecificacaoMetaUseCase(FilaDePrecificacaoMeta fila) {
        this.fila = fila;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void executar(List<PrecificacaoObservada> observacoes) {
        if (observacoes != null && !observacoes.isEmpty()) {
            fila.registrar(observacoes);
        }
    }
}
