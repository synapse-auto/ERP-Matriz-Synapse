package com.synapse.crm.core.application.lead;

/**
 * O tenant nao tem o campo customizado reservado {@code data_nascimento} (tipo {@code DATA})
 * cadastrado. Gravar em {@code dados_customizados} sem essa chave existir deixaria um valor que a
 * E194 (mensagens de aniversario) nunca leria de volta — {@code AniversariantesDoDiaRepositorioJdbc}
 * so considera a chave quando ela esta cadastrada com esse tipo exato.
 */
public class CampoDataNascimentoNaoCadastradoException extends RuntimeException {

    public CampoDataNascimentoNaoCadastradoException() {
        super("campo customizado 'data_nascimento' (tipo DATA) nao esta cadastrado nesta instancia");
    }
}
