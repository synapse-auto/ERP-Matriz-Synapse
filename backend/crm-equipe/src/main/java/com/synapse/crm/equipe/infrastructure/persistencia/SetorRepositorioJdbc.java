package com.synapse.crm.equipe.infrastructure.persistencia;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import com.synapse.crm.equipe.application.setor.SetorRepositorio;
import com.synapse.crm.equipe.domain.setor.AtribuicaoDeSetor;
import com.synapse.crm.equipe.domain.setor.NomeDeSetorEmUsoException;
import com.synapse.crm.equipe.domain.setor.Setor;

/**
 * Setores no pool geral: é cadastro de gestão, fora do caminho de mensagem. As tabelas não têm RLS (como
 * {@code usuario}) porque não guardam dado de lead.
 */
@Repository
class SetorRepositorioJdbc implements SetorRepositorio {

    /** Conta só integrantes ativos; setor sem ninguém continua aparecendo, com zero. */
    private static final String SELECT_SETOR =
            """
            SELECT s.id, s.nome, s.recebe_da_triagem, s.ativo, count(u.id) AS usuarios
              FROM setor s
              LEFT JOIN usuario_setor us ON us.setor_id = s.id
              LEFT JOIN usuario u ON u.id = us.usuario_id AND u.ativo
            """;

    private static final String AGRUPAR = " GROUP BY s.id ";

    private static final RowMapper<Setor> MAPEADOR = (linha, indice) -> new Setor(
            linha.getObject("id", UUID.class),
            linha.getString("nome"),
            linha.getBoolean("recebe_da_triagem"),
            linha.getBoolean("ativo"),
            linha.getInt("usuarios"));

    private final JdbcTemplate jdbc;

    SetorRepositorioJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<Setor> listar() {
        return jdbc.query(SELECT_SETOR + AGRUPAR + "ORDER BY lower(s.nome), s.id", MAPEADOR);
    }

    @Override
    public Optional<Setor> porId(UUID id) {
        return jdbc.query(SELECT_SETOR + " WHERE s.id = ? " + AGRUPAR, MAPEADOR, id).stream().findFirst();
    }

    @Override
    public Setor criar(String nome, boolean recebeDaTriagem) {
        try {
            UUID id = jdbc.queryForObject(
                    "INSERT INTO setor (nome, recebe_da_triagem) VALUES (?, ?) RETURNING id",
                    UUID.class,
                    nome,
                    recebeDaTriagem);
            return porId(id).orElseThrow();
        } catch (DuplicateKeyException duplicado) {
            throw new NomeDeSetorEmUsoException("Já existe um setor com este nome.");
        }
    }

    @Override
    public Optional<Setor> atualizar(UUID id, String nome, boolean recebeDaTriagem, boolean ativo) {
        try {
            int alteradas = jdbc.update(
                    "UPDATE setor SET nome = ?, recebe_da_triagem = ?, ativo = ?, atualizado_em = now() WHERE id = ?",
                    nome,
                    recebeDaTriagem,
                    ativo,
                    id);
            return alteradas == 0 ? Optional.empty() : porId(id);
        } catch (DuplicateKeyException duplicado) {
            throw new NomeDeSetorEmUsoException("Já existe um setor com este nome.");
        }
    }

    @Override
    public List<AtribuicaoDeSetor> listarAtribuicoes() {
        return jdbc.query(
                "SELECT usuario_id, setor_id FROM usuario_setor ORDER BY usuario_id",
                (linha, indice) -> new AtribuicaoDeSetor(
                        linha.getObject("usuario_id", UUID.class), linha.getObject("setor_id", UUID.class)));
    }

    @Override
    public void atribuir(UUID usuarioId, UUID setorId, UUID atribuidoPorId) {
        // Apagar e inserir na mesma transação, e não ON CONFLICT (usuario_id): trocar de setor continua certo
        // no dia em que o índice de "um setor por usuário" for removido.
        jdbc.update("DELETE FROM usuario_setor WHERE usuario_id = ?", usuarioId);
        jdbc.update(
                "INSERT INTO usuario_setor (usuario_id, setor_id, atribuido_por_id) VALUES (?, ?, ?)",
                usuarioId,
                setorId,
                atribuidoPorId);
    }

    @Override
    public boolean remover(UUID usuarioId) {
        return jdbc.update("DELETE FROM usuario_setor WHERE usuario_id = ?", usuarioId) > 0;
    }
}
