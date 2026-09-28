package com.synapse.crm.equipe.application.permissao;

/**
 * Expressoes SpEL da area Gestao, escritas uma vez. O papel continua sendo o teto; a capacidade
 * restringe dentro dele. SUBGESTOR le Gestao com {@code equipe.ver} e so edita com delegacao.
 */
public final class AutorizacaoDeGestao {

    public static final String LER = "hasAnyRole('GESTOR','ADMINISTRADOR') or (hasRole('SUBGESTOR') and @capacidades.permite('equipe.ver'))";

    public static final String EDITAR_PERFIS = "hasAnyRole('GESTOR','ADMINISTRADOR') or (hasRole('SUBGESTOR') and @capacidades.permite('equipe.perfis'))";

    public static final String EDITAR_EXCECOES = "hasAnyRole('GESTOR','ADMINISTRADOR') or (hasRole('SUBGESTOR') and @capacidades.permite('equipe.excecoes_atendentes'))";

    private AutorizacaoDeGestao() {}
}
