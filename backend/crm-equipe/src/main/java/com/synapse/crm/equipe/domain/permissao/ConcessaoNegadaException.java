package com.synapse.crm.equipe.domain.permissao;

/**
 * Quem pediu pode ate executar a acao, mas nao pode concedê-la a este alvo. Vira 403.
 *
 * <p>Distinta de {@link PermissaoInvalidaException}: aqui o payload e coerente com o catalogo; o
 * problema e a alcada de quem concede (delegacao de SUBGESTOR, alvo superior, autoelevacao).
 */
public class ConcessaoNegadaException extends RuntimeException {

    public enum Codigo {
        /**
         * Sem a delegacao que a operacao exige: {@link Capacidade#EQUIPE_EXCECOES_ATENDENTES} para
         * excecoes, {@link Capacidade#EQUIPE_PERFIS} para perfis.
         */
        SEM_DELEGACAO,
        /** Ninguem ajusta as proprias permissoes. */
        ALVO_PROPRIO,
        /** SUBGESTOR so alcanca ATENDENTES e o perfil deles; nunca outro subgestor, o proprio perfil ou superior. */
        ALVO_FORA_DA_ALCADA,
        /** Acao fora do conjunto delegavel. */
        FORA_DO_CONJUNTO_DELEGAVEL,
        /** Ninguem concede o que nao tem — nem pelo interruptor, nem pelo nivel ou pela dependencia. */
        ACIMA_DA_PROPRIA_PERMISSAO
    }

    private final Codigo codigo;
    private final String chave;

    public ConcessaoNegadaException(Codigo codigo, String chave) {
        super("Concessao negada: " + codigo + (chave == null ? "" : " (" + chave + ")"));
        this.codigo = codigo;
        this.chave = chave;
    }

    public ConcessaoNegadaException(Codigo codigo) {
        this(codigo, null);
    }

    public Codigo codigo() {
        return codigo;
    }

    public String chave() {
        return chave;
    }
}
