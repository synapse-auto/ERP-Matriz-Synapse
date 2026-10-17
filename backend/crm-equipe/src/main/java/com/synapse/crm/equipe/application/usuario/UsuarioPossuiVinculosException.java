package com.synapse.crm.equipe.application.usuario;

import java.util.List;

public class UsuarioPossuiVinculosException extends RuntimeException {
    private final List<ClassificacaoDeTesteRepositorio.Vinculo> vinculos;
    public UsuarioPossuiVinculosException(List<ClassificacaoDeTesteRepositorio.Vinculo> vinculos) {
        super("Usuario possui vinculos operacionais ou historicos; nenhuma relacao foi alterada.");
        this.vinculos = List.copyOf(vinculos);
    }
    public List<ClassificacaoDeTesteRepositorio.Vinculo> vinculos() { return vinculos; }
}
