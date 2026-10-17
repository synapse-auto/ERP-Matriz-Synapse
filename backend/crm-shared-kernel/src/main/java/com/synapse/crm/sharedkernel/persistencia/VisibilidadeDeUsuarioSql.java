package com.synapse.crm.sharedkernel.persistencia;

/** Predicado compartilhado dos read models de pessoas. Nao modifica o alcance de leads/RLS. */
public final class VisibilidadeDeUsuarioSql {
    private VisibilidadeDeUsuarioSql() {}

    public static String visivel(String alias) {
        return "(" + alias + ".usuario_teste = FALSE OR (SELECT app_administrador_vigente()))";
    }
}
