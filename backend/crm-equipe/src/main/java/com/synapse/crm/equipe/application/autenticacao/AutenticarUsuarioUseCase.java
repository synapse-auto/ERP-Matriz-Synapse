package com.synapse.crm.equipe.application.autenticacao;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.equipe.domain.usuario.AutenticacaoInvalidaException;
import com.synapse.crm.equipe.domain.usuario.Usuario;

/** Login por e-mail e senha. */
@Service
public class AutenticarUsuarioUseCase {

    private final UsuarioRepositorio usuarios;
    private final CodificadorDeSenha senhas;
    private final EmissorDeSessao emissor;

    public AutenticarUsuarioUseCase(
            UsuarioRepositorio usuarios, CodificadorDeSenha senhas, EmissorDeSessao emissor) {
        this.usuarios = usuarios;
        this.senhas = senhas;
        this.emissor = emissor;
    }

    @Transactional
    public Sessao executar(String email, String senha) {
        Usuario usuario = usuarios
                .porEmail(email == null ? "" : email.trim().toLowerCase())
                .orElseThrow(AutenticacaoInvalidaException::credenciaisIncorretas);

        // A senha e conferida antes de olhar o campo `ativo` para que o tempo de
        // resposta nao denuncie quais contas existem na empresa.
        boolean senhaConfere = senhas.confere(senha, usuario.senhaHash());

        if (!usuario.ativo()) {
            throw AutenticacaoInvalidaException.usuarioInativo();
        }
        if (!senhaConfere) {
            throw AutenticacaoInvalidaException.credenciaisIncorretas();
        }

        // Login novo abre uma familia de rotacao nova.
        return emissor.emitir(usuario, UUID.randomUUID());
    }
}
