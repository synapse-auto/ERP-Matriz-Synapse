package com.synapse.crm.equipe.application.autenticacao;

/** Porta de verificacao de senha. A implementacao BCrypt mora em infrastructure. */
public interface CodificadorDeSenha {

    boolean confere(String senhaEmTexto, String hashArmazenado);

    String codificar(String senhaEmTexto);
}
