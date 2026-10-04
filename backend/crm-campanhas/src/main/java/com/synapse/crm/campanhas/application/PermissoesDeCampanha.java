package com.synapse.crm.campanhas.application;

/**
 * Teto de papel e capacidade efetiva da Gestao para cada operacao de campanhas. O teto conserva
 * os acessos anteriores; a capacidade pode restringir um perfil configuravel sem alterar a flag.
 * Quando a funcionalidade/canal esta indisponivel, o caso de uso continua respondendo 404 antes de
 * qualquer leitura. A autorizacao libera somente essa verificacao inicial, nunca dados ou mutacoes.
 */
public final class PermissoesDeCampanha {

    private static final String INDISPONIVEL = "!@disponibilidadeDeCampanhas.disponivel()";

    public static final String LEITURA = "hasAnyRole('GESTOR', 'SUBGESTOR', 'ADMINISTRADOR', 'OPERADOR') and ("
            + INDISPONIVEL + " or @capacidades.permite('campanhas.ver'))";
    /** Carteira, telefones e exportacao nao fazem parte da leitura de metricas do Operador. */
    public static final String LEITURA_DE_DESTINATARIOS = "hasAnyRole('GESTOR', 'SUBGESTOR', 'ADMINISTRADOR') and ("
            + INDISPONIVEL + " or @capacidades.permite('campanhas.ver_destinatarios'))";
    public static final String REGISTRAR_OPT_OUT = "hasAnyRole('GESTOR', 'SUBGESTOR', 'ADMINISTRADOR') and ("
            + INDISPONIVEL + " or @capacidades.permite('campanhas.registrar_opt_out'))";
    public static final String CRIAR = "hasRole('ADMINISTRADOR') and ("
            + INDISPONIVEL + " or @capacidades.permite('campanhas.criar'))";
    public static final String EDITAR = "hasRole('ADMINISTRADOR') and ("
            + INDISPONIVEL + " or @capacidades.permite('campanhas.editar'))";
    public static final String TESTAR = "hasRole('ADMINISTRADOR') and ("
            + INDISPONIVEL + " or @capacidades.permite('campanhas.testar'))";
    public static final String OPERAR = "hasRole('ADMINISTRADOR') and ("
            + INDISPONIVEL + " or @capacidades.permite('campanhas.operar'))";
    public static final String CONFERIR = "hasRole('ADMINISTRADOR') and ("
            + INDISPONIVEL + " or @capacidades.permite('campanhas.conferir'))";
    public static final String CONFIGURAR = "hasRole('ADMINISTRADOR') and ("
            + INDISPONIVEL + " or @capacidades.permite('campanhas.configurar'))";
    public static final String OPT_OUT = "hasRole('ADMINISTRADOR') and ("
            + INDISPONIVEL + " or @capacidades.permite('campanhas.opt_out'))";

    private PermissoesDeCampanha() {}
}
