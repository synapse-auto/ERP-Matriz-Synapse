package com.synapse.crm.app.provisionamento;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.synapse.crm.app.PostgresIT;

/**
 * Exercita a mesma logica de UPDATE que {@code docker/provisionamento/provisionar-instancia.sql}
 * usa para {@code senha_alterada_em} (E31b bloco 2). Nao roda o .sql via {@code psql} — dependeria
 * do binario no ambiente de teste, e o script inteiro tem diretivas {@code \set}/{@code \gset} que
 * so o cliente psql entende — mas a clausula sob teste e copiada literalmente do arquivo real.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
class ProvisionamentoSenhaIT extends PostgresIT {

    /** Copiada de docker/provisionamento/provisionar-instancia.sql — mesma clausula, letra por letra. */
    private static final String UPSERT =
            """
            INSERT INTO usuario (id, nome, email, senha_hash, papel, ativo)
            VALUES (gen_random_uuid(), ?, ?, ?, 'ADMINISTRADOR', TRUE)
            ON CONFLICT (email) DO UPDATE
                SET nome = EXCLUDED.nome,
                    senha_hash = EXCLUDED.senha_hash,
                    papel = EXCLUDED.papel,
                    ativo = TRUE,
                    senha_alterada_em = CASE
                        WHEN usuario.senha_hash IS DISTINCT FROM EXCLUDED.senha_hash THEN NULL
                        ELSE usuario.senha_alterada_em
                    END
            """;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("reexecutar com hash novo zera senha_alterada_em; com o mesmo hash, preserva")
    void reprovisionar_zeraSoQuandoHashMuda() {
        String email = "admin-e31b-" + UUID.randomUUID() + "@teste.local";
        jdbc.update(UPSERT, "Administrador", email, "hash-v1");
        jdbc.update("UPDATE usuario SET senha_alterada_em = now() WHERE email = ?", email);

        // Mesmo hash: reconciliar canal/etapas/flags nao pode forcar troca de senha a toa.
        jdbc.update(UPSERT, "Administrador", email, "hash-v1");
        assertThat(jdbc.queryForObject(
                        "SELECT senha_alterada_em FROM usuario WHERE email = ?", Timestamp.class, email))
                .isNotNull();

        // Hash novo: alguem redefiniu a senha por fora do produto — volta ao primeiro acesso.
        jdbc.update(UPSERT, "Administrador", email, "hash-v2");
        assertThat(jdbc.queryForObject(
                        "SELECT senha_alterada_em FROM usuario WHERE email = ?", Timestamp.class, email))
                .isNull();
    }
}
