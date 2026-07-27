package com.synapse.crm.equipe.interfaces;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.synapse.crm.equipe.application.usuario.ListarUsuariosUseCase;
import com.synapse.crm.equipe.domain.usuario.Usuario;
import com.synapse.crm.sharedkernel.identidade.PapelUsuario;

/** Consulta da equipe. A restricao por papel esta no caso de uso, nao aqui. */
@RestController
@RequestMapping("/api/v1/usuarios")
class UsuarioController {

    private final ListarUsuariosUseCase listar;

    UsuarioController(ListarUsuariosUseCase listar) {
        this.listar = listar;
    }

    @GetMapping
    List<UsuarioResposta> listar() {
        return listar.executar().stream().map(UsuarioResposta::de).toList();
    }

    /** Sem senha_hash: nunca sai da camada de persistencia. */
    record UsuarioResposta(UUID id, String nome, String email, PapelUsuario papel, boolean ativo) {
        static UsuarioResposta de(Usuario usuario) {
            return new UsuarioResposta(
                    usuario.id(), usuario.nome(), usuario.email(), usuario.papel(), usuario.ativo());
        }
    }
}
