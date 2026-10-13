package com.synapse.crm.equipe.application.setor;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.equipe.domain.setor.Setor;
import com.synapse.crm.sharedkernel.auditoria.Auditable;

/** Cria um setor. Estrutura da instância: só GESTOR e ADMINISTRADOR (com a capacidade de editar a equipe). */
@Service
public class CriarSetorUseCase {

    private final HabilitacaoDeSetores habilitacao;
    private final SetorRepositorio setores;

    public CriarSetorUseCase(HabilitacaoDeSetores habilitacao, SetorRepositorio setores) {
        this.habilitacao = habilitacao;
        this.setores = setores;
    }

    @PreAuthorize("hasAnyRole('GESTOR','ADMINISTRADOR') and @capacidades.permite('equipe.editar')")
    @Transactional
    @Auditable(acao = "CRIAR_SETOR", entidadeTipo = "SETOR")
    public Setor executar(String nome, boolean recebeDaTriagem) {
        habilitacao.exigir();
        return setores.criar(Setor.normalizarNome(nome), recebeDaTriagem);
    }
}
