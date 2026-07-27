package com.synapse.crm.app.config.rls;

/** Nao foi possivel publicar o contexto RLS na transacao. */
public class ContextoRlsIndisponivelException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ContextoRlsIndisponivelException(String papel, Throwable causa) {
        super(
                "Nao foi possivel publicar o contexto RLS (papel=" + papel + ") na transacao. "
                        + "Sem esse contexto as politicas do banco negam toda leitura das tabelas "
                        + "protegidas, entao a transacao e abortada em vez de seguir cega.",
                causa);
    }
}
