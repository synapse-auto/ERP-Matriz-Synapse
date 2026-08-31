package com.synapse.crm.atendimento.application;

/**
 * O telefone nao pode ser usado para iniciar atendimento por quem pediu.
 *
 * <p>A mensagem e deliberadamente igual quando o numero ja pertence a uma carteira invisivel ou
 * quando a criacao nao alcanca um lead. Ela orienta a operacao sem confirmar existencia, nome ou
 * responsavel, preservando a RN-CRM-01.
 */
public class ContatoIndisponivelParaInicioException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ContatoIndisponivelParaInicioException() {
        super("Numero indisponivel para iniciar atendimento. Procure a gestao.");
    }
}
