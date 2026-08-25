package com.synapse.crm.equipe.application.usuario;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.equipe.application.autenticacao.CodificadorDeSenha;
import com.synapse.crm.equipe.application.autenticacao.UsuarioRepositorio;
import com.synapse.crm.equipe.domain.usuario.SenhaInvalidaException;
import com.synapse.crm.equipe.domain.usuario.Usuario;
import com.synapse.crm.sharedkernel.auditoria.Auditable;
import com.synapse.crm.sharedkernel.identidade.UsuarioContext;

/** Atualiza os dados de exibicao do proprio usuario; trocar e-mail exige a senha atual. */
@Service
public class AtualizarMeuUsuarioUseCase {

    private final EquipeRepositorio equipe;
    private final UsuarioContext usuario;
    private final UsuarioRepositorio usuarios;
    private final CodificadorDeSenha senhas;

    public AtualizarMeuUsuarioUseCase(
            EquipeRepositorio equipe,
            UsuarioContext usuario,
            UsuarioRepositorio usuarios,
            CodificadorDeSenha senhas) {
        this.equipe = equipe;
        this.usuario = usuario;
        this.usuarios = usuarios;
        this.senhas = senhas;
    }

    @PreAuthorize("isAuthenticated()")
    @Transactional
    @Auditable(acao = "ATUALIZAR_PERFIL", entidadeTipo = "USUARIO")
    public Optional<Usuario> executar(
            UUID usuarioId, String nome, String email, String telefone, String cargo, String senhaAtual) {
        var autenticado = usuario.atual();
        if (!autenticado.id().equals(usuarioId)) {
            throw new AccessDeniedException("usuario autenticado nao corresponde ao perfil informado");
        }
        Usuario antes = usuarios.porId(usuarioId).orElseThrow(SenhaInvalidaException::atualIncorreta);
        String emailNormalizado = email == null
                ? antes.email()
                : email.trim().toLowerCase(Locale.ROOT);
        if (!antes.email().equalsIgnoreCase(emailNormalizado)
                && !senhas.confere(senhaAtual, antes.senhaHash())) {
            throw SenhaInvalidaException.atualIncorreta();
        }
        return equipe.atualizarMeuPerfil(
                usuarioId,
                nome.trim(),
                emailNormalizado,
                telefone,
                cargo);
    }
}
