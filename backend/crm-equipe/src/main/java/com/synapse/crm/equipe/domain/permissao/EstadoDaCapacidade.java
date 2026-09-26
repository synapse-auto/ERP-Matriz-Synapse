package com.synapse.crm.equipe.domain.permissao;

/**
 * Decisao calculada pelo backend para uma capacidade. A tela desenha exatamente isto: nunca mostra
 * ligado o que esta efetivamente bloqueado, e sempre diz por que.
 */
public record EstadoDaCapacidade(Capacidade capacidade, boolean permitido, Motivo motivo, Origem origem) {

    /** Por que o resultado e este. {@link #PERMITIDO} e o unico motivo de um estado permitido. */
    public enum Motivo {
        PERMITIDO,
        /** O papel nunca teve esta acao (bloqueio estrutural). */
        TETO_DO_PAPEL,
        /** Modulo desligado por feature flag. */
        FLAG_DESLIGADA,
        /** Nivel do modulo abaixo do minimo da acao — "liberado ao subir o nivel". */
        NIVEL_DO_MODULO,
        /** Interruptor desligado no perfil ou na excecao. */
        DESLIGADO,
        /** Uma acao da qual esta depende esta negada. */
        DEPENDENCIA
    }

    /** De onde veio a decisao. */
    public enum Origem {
        /** GESTOR/ADMINISTRADOR: acesso fixo ao teto do papel. */
        FIXO,
        /** Recorte estrutural, nao configuravel. */
        ESTRUTURAL,
        PERFIL,
        /** Uma excecao do usuario (da acao ou do nivel do modulo) decidiu. */
        EXCECAO
    }
}
