package com.synapse.crm.equipe.application.disponibilidade;

/** O setor pedido para o rodízio não pode ser usado; o motivo vai no corpo da resposta (campo aditivo). */
public class SetorDeDistribuicaoInvalidoException extends RuntimeException {

    /** Por que o setor foi recusado. O chamador distingue "configure o CRM" de "peça outro setor". */
    public enum Motivo {
        SETORES_DESLIGADOS,
        SETOR_INEXISTENTE,
        SETOR_INATIVO,
        SETOR_NAO_RECEBE_DA_TRIAGEM
    }

    private final Motivo motivo;

    public SetorDeDistribuicaoInvalidoException(Motivo motivo) {
        super("Setor invalido para a distribuicao: " + motivo);
        this.motivo = motivo;
    }

    public Motivo motivo() {
        return motivo;
    }
}
