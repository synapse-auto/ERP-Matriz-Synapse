package com.synapse.crm.app.saude.application;

import java.util.Objects;

/** Diagnostico sanitizado de um ponto do caminho de mensagens. */
public record ComponenteDaSaude(
        String nome, StatusDoComponente status, SeveridadeSaude severidade, String detalhe) {

    public ComponenteDaSaude {
        Objects.requireNonNull(nome);
        Objects.requireNonNull(status);
        Objects.requireNonNull(severidade);
        detalhe = detalhe == null ? "" : detalhe;
    }

    public static ComponenteDaSaude up(String nome, String detalhe) {
        return new ComponenteDaSaude(nome, StatusDoComponente.UP, SeveridadeSaude.NORMAL, detalhe);
    }

    public static ComponenteDaSaude down(
            String nome, SeveridadeSaude severidade, String detalhe) {
        return new ComponenteDaSaude(nome, StatusDoComponente.DOWN, severidade, detalhe);
    }

    public static ComponenteDaSaude naoVerificado(
            String nome, SeveridadeSaude severidade, String detalhe) {
        return new ComponenteDaSaude(
                nome, StatusDoComponente.NAO_VERIFICADO, severidade, detalhe);
    }

    public boolean falhou() {
        return status != StatusDoComponente.UP;
    }
}
