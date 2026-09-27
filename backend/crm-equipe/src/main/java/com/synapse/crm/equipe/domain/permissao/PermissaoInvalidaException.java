package com.synapse.crm.equipe.domain.permissao;

import java.util.List;

/** Payload de permissao incoerente com o catalogo ou com o teto do papel. Vira 422 com a lista inteira. */
public class PermissaoInvalidaException extends RuntimeException {

    private final List<Violacao> violacoes;

    public PermissaoInvalidaException(List<Violacao> violacoes) {
        super("Configuracao de permissoes invalida: " + violacoes);
        this.violacoes = List.copyOf(violacoes);
    }

    public PermissaoInvalidaException(Violacao violacao) {
        this(List.of(violacao));
    }

    public List<Violacao> violacoes() {
        return violacoes;
    }
}
