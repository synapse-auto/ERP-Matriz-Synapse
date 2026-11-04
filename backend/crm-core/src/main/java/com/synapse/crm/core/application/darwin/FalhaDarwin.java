package com.synapse.crm.core.application.darwin;

/** Somente códigos sanitizados: nunca guardar corpo HTTP, CPF, telefone, URL ou credencial. */
public class FalhaDarwin extends RuntimeException {
    public enum Codigo { DESABILITADA, CONFIGURACAO, AUTENTICACAO, LIMITE, INDISPONIVEL,
        RESPOSTA_INVALIDA, IDENTIDADE_DIVERGENTE, CONSULTA_INVALIDA, CONCORRENCIA, OCUPADO, CPF_INVALIDO }
    private final Codigo codigo;
    public FalhaDarwin(Codigo codigo) { super("DARWIN_" + codigo.name()); this.codigo = codigo; }
    public Codigo codigo() { return codigo; }
}
