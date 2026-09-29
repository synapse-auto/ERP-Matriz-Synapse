package com.synapse.crm.equipe.application.permissao;

import java.util.Set;

import com.synapse.crm.equipe.domain.permissao.PermissaoInvalidaException;
import com.synapse.crm.equipe.domain.permissao.Violacao;

/**
 * Excecoes por usuario sao opcionais por instancia (Base PAI, docs/47 secao 10). Com a flag
 * {@value #CHAVE} desligada — ou ausente da tabela, o padrao de uma instancia nova — a aba aparece
 * como "Em breve" e toda gravacao de excecao e recusada.
 *
 * <p>Excecoes ja salvas continuam valendo no calculo: desligar a edicao nao abre nem fecha acesso
 * de ninguem, so impede mudar.
 */
final class FuncionalidadeDeExcecoes {

    static final String CHAVE = "gestao_excecoes";

    private FuncionalidadeDeExcecoes() {}

    static boolean habilitada(Set<String> flags) {
        return flags.contains(CHAVE);
    }

    /** Mesmo contrato de modulo desligado: 422 com {@code FLAG_DESLIGADA}, nada persistido. */
    static void exigirHabilitada(Set<String> flags) {
        if (!habilitada(flags)) {
            throw new PermissaoInvalidaException(new Violacao(CHAVE, Violacao.Codigo.FLAG_DESLIGADA));
        }
    }
}
