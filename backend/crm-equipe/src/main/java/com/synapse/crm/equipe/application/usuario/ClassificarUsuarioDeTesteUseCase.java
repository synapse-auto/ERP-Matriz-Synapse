package com.synapse.crm.equipe.application.usuario;

import java.util.Optional;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.equipe.application.permissao.PermissaoRepositorio;
import com.synapse.crm.equipe.domain.usuario.Usuario;
import com.synapse.crm.sharedkernel.auditoria.Auditable;
import com.synapse.crm.sharedkernel.identidade.UsuarioContext;
import com.synapse.crm.sharedkernel.permissao.AcessoDeUsuariosAlterado;

@Service
public class ClassificarUsuarioDeTesteUseCase {
    private final EquipeRepositorio equipe;
    private final ClassificacaoDeTesteRepositorio classificacao;
    private final UsuarioContext contexto;
    private final PermissaoRepositorio permissoes;
    private final ApplicationEventPublisher eventos;
    public ClassificarUsuarioDeTesteUseCase(EquipeRepositorio equipe, ClassificacaoDeTesteRepositorio classificacao, UsuarioContext contexto,
            PermissaoRepositorio permissoes, ApplicationEventPublisher eventos) {
        this.equipe=equipe; this.classificacao=classificacao; this.contexto=contexto;
        this.permissoes=permissoes; this.eventos=eventos;
    }
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Transactional
    @Auditable(acao="CLASSIFICAR_USUARIO_TESTE", entidadeTipo="USUARIO")
    public Optional<Usuario> executar(UUID usuario, boolean teste) {
        UUID ator=contexto.atual().id();
        if (ator.equals(usuario) || !classificacao.administradorVigente(ator)) throw new AccessDeniedException("Classificacao restrita ao administrador vigente, sem autoalteracao.");
        Optional<Usuario> atual=equipe.porId(usuario);
        if (atual.isEmpty()) return Optional.empty();
        if (teste && !atual.get().usuarioTeste()) {
            var vinculos=classificacao.vinculos(usuario);
            if (!vinculos.isEmpty()) throw new UsuarioPossuiVinculosException(vinculos);
        }
        // Nao trava uma conta operacional durante a verificacao mais ampla. O trigger revalida
        // sob a trava apos serializar com novos vinculos; nenhuma relacao e alterada.
        if (equipe.travarParaAlteracao(usuario).isEmpty()) return Optional.empty();
        classificacao.classificar(usuario,teste);
        if (teste != atual.get().usuarioTeste()) eventos.publishEvent(
                new AcessoDeUsuariosAlterado(classificacao.usuariosAtivos(),false,permissoes.incrementarRevisaoGlobal()));
        return equipe.porId(usuario);
    }
}
