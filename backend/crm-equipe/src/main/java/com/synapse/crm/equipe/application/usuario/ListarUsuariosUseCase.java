package com.synapse.crm.equipe.application.usuario;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import com.synapse.crm.equipe.domain.usuario.Usuario;

/**
 * Lista a equipe. Restrito a gestao.
 *
 * <p>A autorizacao e declarada aqui, no caso de uso, e nao num mapa central de rotas: quem le este
 * arquivo ve quem pode executa-lo. Configuracao central de rota vive longe do codigo que protege e
 * envelhece sem ninguem notar.
 *
 * <p>Um atendente que chamar isto recebe 403 — o Spring Security barra antes de o metodo rodar.
 */
@Service
public class ListarUsuariosUseCase {

    private final EquipeRepositorio usuarios;

    public ListarUsuariosUseCase(EquipeRepositorio usuarios) {
        this.usuarios = usuarios;
    }

    @PreAuthorize("hasAnyRole('GESTOR', 'SUBGESTOR', 'ADMINISTRADOR')")
    public List<Usuario> executar() {
        return usuarios.listar(new FiltroEquipe(true));
    }
}
