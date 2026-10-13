package com.synapse.crm.equipe.application.setor;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Lista os setores e as atribuições, para quem enxerga a equipe. */
@Service
public class ListarSetoresUseCase {

    private final HabilitacaoDeSetores habilitacao;
    private final SetorRepositorio setores;

    public ListarSetoresUseCase(HabilitacaoDeSetores habilitacao, SetorRepositorio setores) {
        this.habilitacao = habilitacao;
        this.setores = setores;
    }

    @PreAuthorize("hasAnyRole('SUBGESTOR','GESTOR','ADMINISTRADOR') and @capacidades.permite('equipe.ver')")
    @Transactional(readOnly = true)
    public VisaoDeSetores executar() {
        habilitacao.exigir();
        return new VisaoDeSetores(setores.listar(), setores.listarAtribuicoes());
    }
}
