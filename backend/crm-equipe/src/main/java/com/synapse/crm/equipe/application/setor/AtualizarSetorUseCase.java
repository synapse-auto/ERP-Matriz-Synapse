package com.synapse.crm.equipe.application.setor;

import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.equipe.domain.setor.Setor;
import com.synapse.crm.equipe.domain.setor.SetorNaoEncontradoException;
import com.synapse.crm.sharedkernel.auditoria.Auditable;

/** Renomeia, liga/desliga "recebe da triagem" e ativa/inativa um setor. Setor nunca é apagado. */
@Service
public class AtualizarSetorUseCase {

    private final HabilitacaoDeSetores habilitacao;
    private final SetorRepositorio setores;

    public AtualizarSetorUseCase(HabilitacaoDeSetores habilitacao, SetorRepositorio setores) {
        this.habilitacao = habilitacao;
        this.setores = setores;
    }

    @PreAuthorize("hasAnyRole('GESTOR','ADMINISTRADOR') and @capacidades.permite('equipe.editar')")
    @Transactional
    @Auditable(acao = "ATUALIZAR_SETOR", entidadeTipo = "SETOR")
    public Setor executar(UUID id, String nome, boolean recebeDaTriagem, boolean ativo) {
        habilitacao.exigir();
        return setores
                .atualizar(id, Setor.normalizarNome(nome), recebeDaTriagem, ativo)
                .orElseThrow(() -> new SetorNaoEncontradoException("Setor não encontrado."));
    }
}
