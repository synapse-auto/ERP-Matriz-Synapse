package com.synapse.crm.equipe.infrastructure.persistencia;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.synapse.crm.equipe.application.autenticacao.UsuarioRepositorio;
import com.synapse.crm.equipe.domain.usuario.Usuario;

/**
 * Adaptador JPA da porta de usuarios.
 *
 * <p>Leitura via JPA; a escrita de {@link #atualizarSenha} vai por {@link JdbcTemplate} direto,
 * como {@code EquipeRepositorioJdbc} ja faz para a tabela {@code usuario} — nao existe caminho de
 * escrita JPA para esta entidade em nenhum outro lugar do modulo, entao nao ha motivo para abrir um
 * agora so por causa deste UPDATE de uma coluna.
 */
@Repository
class UsuarioRepositorioJpa implements UsuarioRepositorio {

    private final UsuarioJpaRepository jpa;
    private final JdbcTemplate jdbc;

    UsuarioRepositorioJpa(UsuarioJpaRepository jpa, JdbcTemplate jdbc) {
        this.jpa = jpa;
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Usuario> porEmail(String email) {
        return jpa.findByEmailIgnoreCase(email).map(UsuarioEntity::paraDominio);
    }

    @Override
    public Optional<Usuario> porId(UUID id) {
        return jpa.findById(id).map(UsuarioEntity::paraDominio);
    }

    @Override
    public void atualizarSenha(UUID usuarioId, String novoHash, Instant quando) {
        jdbc.update(
                "UPDATE usuario SET senha_hash = ?, senha_alterada_em = ? WHERE id = ?",
                novoHash,
                Timestamp.from(quando),
                usuarioId);
    }

}
