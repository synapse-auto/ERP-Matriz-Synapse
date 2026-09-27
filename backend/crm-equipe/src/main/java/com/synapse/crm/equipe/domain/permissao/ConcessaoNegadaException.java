package com.synapse.crm.equipe.domain.permissao;

/**
 * Quem pediu pode ate executar a acao, mas nao pode concedê-la a este alvo. Vira 403.
 *
 * <p>Distinta de {@link PermissaoInvalidaException}: aqui o payload e coerente com o catalogo; o
 * problema e a alcada de quem concede (delegacao de SUBGESTOR, alvo superior, autoelevacao).
 */
public class ConcessaoNegadaException extends RuntimeException {

    public enum Codigo {
        /** SUBGESTOR sem {@link Capacidade#EQUIPE_EXCECOES_ATENDENTES}. */
        SEM_DELEGACAO,
        /** Ninguem ajusta as proprias permissoes. */
        ALVO_PROPRIO,
        /** SUBGESTOR so alcanca ATENDENTES; nunca outro subgestor ou superior. */
        ALVO_FORA_DA_ALCADA,
        /** Acao fora do conjunto delegavel. */
        FORA_DO_CONJUNTO_DELEGAVEL,
        /** Ninguem concede o que nao tem. */
        ACIMA_DA_PROPRIA_PERMISSAO,
        /** Nivel de modulo nao e delegavel; so interruptores. */
        NIVEL_NAO_DELEGAVEL,
        /** Somente GESTOR e ADMINISTRADOR editam perfis. */
        PERFIL_SO_PARA_SUPERIORES
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
