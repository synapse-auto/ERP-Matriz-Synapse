package com.synapse.crm.atendimento.domain.mensagem;

/** O tipo da origem nao cabe no caminho de envio atual (botoes, lista, destino igual a origem). */
public class EncaminhamentoIncompativelException extends RuntimeException {

    public EncaminhamentoIncompativelException(String detalhe) {
        super(detalhe);
    }
}
