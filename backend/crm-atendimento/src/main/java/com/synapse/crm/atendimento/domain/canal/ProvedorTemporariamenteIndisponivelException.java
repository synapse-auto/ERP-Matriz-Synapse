package com.synapse.crm.atendimento.domain.canal;

/**
 * O provedor nao foi consultado: o disjuntor esta aberto. Nao e falha da chamada — e "tente de novo
 * mais tarde". Quem retenta (a fila de entrada) nao deve gastar tentativa com isto; comparar a
 * mensagem de {@link IllegalStateException} quebraria no dia em que o texto mudasse.
 */
public class ProvedorTemporariamenteIndisponivelException extends RuntimeException {

    public ProvedorTemporariamenteIndisponivelException(String motivo) {
        super(motivo);
    }

    public ProvedorTemporariamenteIndisponivelException(String motivo, Throwable causa) {
        super(motivo, causa);
    }
}
