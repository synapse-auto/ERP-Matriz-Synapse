package com.synapse.crm.automacaoconfig.infrastructure.persistencia;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.synapse.crm.automacaoconfig.application.festivas.MensagemFestivaRepositorio;
import com.synapse.crm.automacaoconfig.domain.festivas.MensagemFestiva;

/**
 * Persistencia JDBC das datas festivas.
 *
 * <p>Este recurso e usado durante o upgrade controlado da V73: enquanto uma
 * instalacao em V72 aguarda o runner pesado, o boot normal nao pode exigir as
 * colunas adicionadas pela V74 atraves da validacao JPA. A consulta so ocorre
 * quando a API e usada, depois de as migrations aplicaveis estarem presentes.
 */
@Repository
class MensagemFestivaRepositorioJdbc implements MensagemFestivaRepositorio {
    private final JdbcTemplate jdbc;

    MensagemFestivaRepositorioJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<MensagemFestiva> listarTodas() {
        return jdbc.query(
                "SELECT id, titulo, icone, data, texto, ativo "
                        + "FROM mensagem_festiva ORDER BY data ASC, titulo ASC",
                MensagemFestivaRepositorioJdbc::mapear);
    }

    @Override
    public Optional<MensagemFestiva> porId(UUID id) {
        return jdbc.query(
                        "SELECT id, titulo, icone, data, texto, ativo "
                                + "FROM mensagem_festiva WHERE id = ?",
                        MensagemFestivaRepositorioJdbc::mapear,
                        id)
                .stream()
                .findFirst();
    }

    @Override
    public MensagemFestiva salvar(MensagemFestiva mensagem) {
        jdbc.update(
                "INSERT INTO mensagem_festiva (id, titulo, icone, data, texto, ativo) VALUES (?, ?, ?, ?, ?, ?) "
                        + "ON CONFLICT (id) DO UPDATE SET titulo = EXCLUDED.titulo, icone = EXCLUDED.icone, "
                        + "data = EXCLUDED.data, texto = EXCLUDED.texto, ativo = EXCLUDED.ativo",
                mensagem.id(),
                mensagem.titulo(),
                mensagem.icone(),
                mensagem.data(),
                mensagem.mensagem(),
                mensagem.ativo());
        return porId(mensagem.id()).orElseThrow();
    }

    @Override
    public void excluir(UUID id) {
        jdbc.update("DELETE FROM mensagem_festiva WHERE id = ?", id);
    }

    private static MensagemFestiva mapear(ResultSet rs, int rowNum) throws SQLException {
        return new MensagemFestiva(
                rs.getObject("id", UUID.class),
                rs.getString("titulo"),
                rs.getString("icone"),
                rs.getObject("data", java.time.LocalDate.class),
                rs.getString("texto"),
                rs.getBoolean("ativo"));
    }
}
