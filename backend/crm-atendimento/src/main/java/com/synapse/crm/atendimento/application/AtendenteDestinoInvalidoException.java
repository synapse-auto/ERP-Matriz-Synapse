package com.synapse.crm.atendimento.application;

import java.util.UUID;

/** O contrato interno só aceita atendente ativo, nunca gestor, subgestor ou IA. */
public class AtendenteDestinoInvalidoException extends RuntimeException {

    public enum Motivo {
        INEXISTENTE("inexistente"),
        INATIVO("inativo"),
        PAPEL_NAO_ELEGIVEL("papel nao elegivel");

        private final String descricao;

        Motivo(String descricao) {
            this.descricao = descricao;
        }

        public String descricao() {
            return descricao;
        }
    }

    private final UUID atendenteId;
    private final Motivo motivo;

    public AtendenteDestinoInvalidoException(UUID atendenteId) {
        this(atendenteId, Motivo.PAPEL_NAO_ELEGIVEL);
    }

    public AtendenteDestinoInvalidoException(UUID atendenteId, Motivo motivo) {
        super("destino " + atendenteId + " recusado: " + motivo.descricao());
        this.atendenteId = atendenteId;
        this.motivo = motivo;
    }

    public UUID atendenteId() {
        return atendenteId;
    }

    public Motivo motivo() {
        return motivo;
    }
}
