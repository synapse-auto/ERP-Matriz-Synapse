package com.synapse.crm.atendimento.infrastructure.canal;

import com.synapse.crm.atendimento.domain.canal.TradutorDeCanal.MotivoDeDescarte;

/**
 * Sinal interno dos tradutores: este item nao vira mensagem, por este motivo.
 *
 * <p>Nunca atravessa o tradutor — quem traduz o POST captura e converte em
 * {@link com.synapse.crm.atendimento.domain.canal.TradutorDeCanal.ItemDescartado}. Sem stack trace:
 * e fluxo esperado, nao falha, e o custo de montar a pilha por item seria desperdicio.
 */
final class ItemNaoTraduzido extends RuntimeException {

    private final MotivoDeDescarte motivo;

    ItemNaoTraduzido(MotivoDeDescarte motivo) {
        super(motivo.name(), null, false, false);
        this.motivo = motivo;
    }

    MotivoDeDescarte motivo() {
        return motivo;
    }
}
