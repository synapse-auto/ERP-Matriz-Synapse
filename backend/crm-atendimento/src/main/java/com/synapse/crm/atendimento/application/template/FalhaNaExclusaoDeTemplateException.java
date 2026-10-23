package com.synapse.crm.atendimento.application.template;

/** Falha sanitizada da exclusão. Nunca transporta o corpo, a URL ou a mensagem livre do provedor. */
public final class FalhaNaExclusaoDeTemplateException extends RuntimeException {

    public enum Motivo {
        CREDENCIAL_RECUSADA,
        PERMISSAO_RECUSADA,
        RECURSO_INDISPONIVEL,
        RECUSADO,
        RESULTADO_INCERTO,
        PROVEDOR_INDISPONIVEL
    }

    public record Diagnostico(Integer httpStatus, Integer codigo, Integer subcodigo, String correlacao) {}

    private final Motivo motivo;
    private final Diagnostico diagnostico;

    public FalhaNaExclusaoDeTemplateException(Motivo motivo, Diagnostico diagnostico) {
        super("Não foi possível confirmar a exclusão do template: " + motivo.name());
        this.motivo = motivo;
        this.diagnostico = diagnostico;
    }

    public Motivo motivo() { return motivo; }

    public Diagnostico diagnostico() { return diagnostico; }
}
