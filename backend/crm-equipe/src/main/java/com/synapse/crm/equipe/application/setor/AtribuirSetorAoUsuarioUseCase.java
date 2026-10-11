package com.synapse.crm.equipe.application.setor;

import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.equipe.application.permissao.AlvoDePermissaoNaoEncontradoException;
import com.synapse.crm.equipe.application.usuario.AlcadaSobreIntegrantes;
import com.synapse.crm.equipe.application.usuario.EquipeRepositorio;
import com.synapse.crm.equipe.domain.setor.AtribuicaoDeSetor;
import com.synapse.crm.equipe.domain.setor.Setor;
import com.synapse.crm.equipe.domain.setor.SetorInativoException;
import com.synapse.crm.equipe.domain.setor.SetorNaoEncontradoException;
import com.synapse.crm.equipe.domain.usuario.Usuario;
import com.synapse.crm.sharedkernel.auditoria.Auditable;

/**
 * Coloca um integrante num setor (ou o tira de qualquer setor, com {@code setorId} nulo).
 *
 * <p>É editar um integrante: vale a mesma alçada das demais operações sobre a equipe (o SUBGESTOR delegado só
 * alcança ATENDENTE, nunca a si mesmo). Nenhuma permissão, papel ou acesso a lead muda por isso.
 */
@Service
public class AtribuirSetorAoUsuarioUseCase {

    private final HabilitacaoDeSetores habilitacao;
    private final SetorRepositorio setores;
    private final EquipeRepositorio equipe;
    private final AlcadaSobreIntegrantes alcada;

    public AtribuirSetorAoUsuarioUseCase(
            HabilitacaoDeSetores habilitacao,
            SetorRepositorio setores,
            EquipeRepositorio equipe,
            AlcadaSobreIntegrantes alcada) {
        this.habilitacao = habilitacao;
        this.setores = setores;
        this.equipe = equipe;
        this.alcada = alcada;
    }

    @PreAuthorize("hasAnyRole('SUBGESTOR','GESTOR','ADMINISTRADOR') and @capacidades.permite('equipe.editar')")
    @Transactional
    @Auditable(acao = "ATRIBUIR_SETOR_AO_USUARIO", entidadeTipo = "USUARIO_SETOR")
    public AtribuicaoDeSetor executar(UUID usuarioId, UUID setorId) {
        habilitacao.exigir();
        Usuario alvo = equipe.travarParaAlteracao(usuarioId)
                .orElseThrow(() -> new AlvoDePermissaoNaoEncontradoException());
        alcada.exigirAlvo(usuarioId, alvo.papel());
        if (setorId == null) {
            setores.remover(usuarioId);
            return new AtribuicaoDeSetor(usuarioId, null);
        }
        Setor setor = setores.porId(setorId)
                .orElseThrow(() -> new SetorNaoEncontradoException("Setor não encontrado."));
        if (!setor.ativo()) {
            throw new SetorInativoException("O setor está inativo e não aceita novos integrantes.");
        }
        setores.atribuir(usuarioId, setorId, alcada.atual().id());
        return new AtribuicaoDeSetor(usuarioId, setorId);
    }
}
