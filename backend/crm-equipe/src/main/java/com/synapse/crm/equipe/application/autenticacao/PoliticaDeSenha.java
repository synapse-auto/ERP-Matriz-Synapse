package com.synapse.crm.equipe.application.autenticacao;

/**
 * Porta de configuracao da politica de senha (E29).
 *
 * <p>Existe pela mesma razao de {@link PoliticaDeSessao}: {@link AlterarSenhaUseCase} le a regra
 * sem importar {@code @ConfigurationProperties} de infrastructure, o que quebraria a regra de
 * dependencia do modulo. Sem numero fixo no caso de uso — o valor vem de configuracao, com default.
 */
public interface PoliticaDeSenha {

    /** Tamanho minimo aceito para uma nova senha. */
    int tamanhoMinimo();
}
