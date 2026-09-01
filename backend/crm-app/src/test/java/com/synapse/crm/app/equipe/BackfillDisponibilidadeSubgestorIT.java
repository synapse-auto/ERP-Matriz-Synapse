package com.synapse.crm.app.equipe;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.synapse.crm.app.PostgresIT;

/**
 * A V51 é idempotente: subgestor pré-existente sem linha ganha uma; repetir o
 * SQL não duplica. O valor inicial é o mesmo da V34 (TRUE).
 */
@SpringBootTest
@ActiveProfiles("dev")
class BackfillDisponibilidadeSubgestorIT extends PostgresIT {

    private static final String BACKFILL =
            """
            INSERT INTO disponibilidade_atendente_ia (atendente_id, disponivel_para_ia)
            SELECT id, TRUE
            FROM usuario
            WHERE ativo = TRUE
              AND papel = 'SUBGESTOR'
            ON CONFLICT (atendente_id) DO NOTHING
            """;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("subgestor sem linha ganha disponibilidade; repetir nao duplica")
    void backfill_criaLinhaENaoDuplica() {
        UUID sub = UUID.randomUUID();
        String hash = jdbc.queryForObject(
                "SELECT senha_hash FROM usuario WHERE email = 'subgestor@dev.local'", String.class);
        jdbc.update(
                "INSERT INTO usuario(id,nome,email,senha_hash,papel,ativo,status_presenca)"
                        + " VALUES(?,?,?,?,'SUBGESTOR',TRUE,'OFFLINE')",
                sub,
                "Subgestor Backfill E113",
                sub + "@e113.invalid",
                hash);
        try {
            jdbc.update("DELETE FROM disponibilidade_atendente_ia WHERE atendente_id = ?", sub);

            jdbc.update(BACKFILL);
            jdbc.update(BACKFILL);

            assertThat(jdbc.queryForObject(
                            "SELECT count(*) FROM disponibilidade_atendente_ia WHERE atendente_id = ?",
                            Integer.class,
                            sub))
                    .isOne();
            assertThat(jdbc.queryForObject(
                            "SELECT disponivel_para_ia FROM disponibilidade_atendente_ia WHERE atendente_id = ?",
                            Boolean.class,
                            sub))
                    .isTrue();
        } finally {
            jdbc.update("DELETE FROM disponibilidade_atendente_ia WHERE atendente_id = ?", sub);
            jdbc.update("DELETE FROM usuario WHERE id = ?", sub);
        }
    }
}
