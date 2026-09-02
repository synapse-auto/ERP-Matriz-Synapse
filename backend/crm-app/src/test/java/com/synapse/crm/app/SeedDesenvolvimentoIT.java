package com.synapse.crm.app;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * Prova que o seed roda no perfil dev — e que e o perfil, nao a convencao, que o libera.
 *
 * <p>O par deste teste vive em {@code SchemaMigracoesIT}, que confere o outro lado: no perfil
 * padrao a pasta {@code db/seed} nem entra nas locations do Flyway.
 */
@SpringBootTest
@ActiveProfiles("dev")
class SeedDesenvolvimentoIT extends PostgresIT {

    /**
     * E-mails do {@code R__seed_dev}. Nao usar {@code LIKE '%@dev.local'}: fixtures de RLS e de
     * primeiro acesso reutilizam o dominio e deixam {@code senha_hash = 'x'} no container
     * compartilhado.
     */
    private static final String FILTRO_EMAILS_SEED =
            "email IN ('admin@dev.local', 'gestor@dev.local', 'subgestor@dev.local',"
                    + " 'ana@dev.local', 'bruno@dev.local')";

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private Flyway flyway;

    @Test
    @DisplayName("no perfil dev o Flyway inclui a pasta do seed")
    void flyway_perfilDev_incluiOSeedNasLocations() {
        assertThat(flyway.getConfiguration().getLocations())
                .anyMatch(local -> local.getDescriptor().contains("db/seed"));
    }

    @Test
    @DisplayName("as etapas do funil sao criadas em ordem, incluindo ganho e perdido")
    void etapas_seedAplicado_ficamEmOrdem() {
        List<String> nomes =
                jdbc.queryForList("SELECT nome FROM etapa_atendimento ORDER BY ordem", String.class);

        assertThat(nomes)
                .startsWith("Novo contato")
                .contains("Fechamento", "Pos-venda", "Perdido")
                .hasSizeGreaterThanOrEqualTo(5);
    }

    @Test
    @DisplayName("existe um usuario para cada papel")
    void usuarios_seedAplicado_cobremTodosOsPapeis() {
        List<String> papeis = jdbc.queryForList(
                "SELECT DISTINCT papel::text FROM usuario WHERE " + FILTRO_EMAILS_SEED, String.class);

        assertThat(papeis)
                .containsExactlyInAnyOrder("ADMINISTRADOR", "GESTOR", "SUBGESTOR", "ATENDENTE");
    }

    @Test
    @DisplayName("as senhas do seed sao hashes BCrypt, nunca texto puro")
    void usuarios_seedAplicado_guardamBcrypt() {
        List<String> hashes = hashesDoSeed();

        assertThat(hashes).isNotEmpty().allMatch(hash -> hash.startsWith("$2a$"));
    }

    /**
     * {@code RlsIsolamentoIT} (e qualquer fixture) nao pode quebrar este teste so porque o e-mail
     * termina em {@code @dev.local}. O LIKE antigo pega hash {@code x} deixado no Postgres
     * compartilhado e o CI fica intermitente.
     */
    @Test
    @DisplayName("fixture com @dev.local e hash x nao e confundida com o seed")
    void usuarios_seedAplicado_ignoramHashDeFixtureNoMesmoDominio() {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO usuario (id, nome, email, senha_hash, papel)"
                        + " VALUES (?, 'fixture rls', ?, 'x', CAST('ATENDENTE' AS papel_usuario))",
                id,
                "rls-" + id + "@dev.local");
        try {
            assertThat(hashesDoSeed()).isNotEmpty().allMatch(hash -> hash.startsWith("$2a$"));
        } finally {
            jdbc.update("DELETE FROM usuario WHERE id = ?", id);
        }
    }

    @Test
    @DisplayName("as feature flags iniciais existem")
    void featureFlags_seedAplicado_existemAsIniciais() {
        List<String> chaves = jdbc.queryForList("SELECT chave FROM feature_flag", String.class);

        assertThat(chaves)
                .contains(
                        "campanhas",
                        "chat_interno",
                        "fidelizacao",
                        "relatorios",
                        "dashboard",
                        "automacao_regras",
                        "horarios");
    }

    @Test
    @DisplayName("os parametros numericos da automacao trazem tipo, unidade e faixa")
    void configuracaoAutomacao_seedAplicado_trazFaixaPreenchida() {
        Integer semFaixa = jdbc.queryForObject(
                """
                SELECT count(*) FROM configuracao_automacao
                 WHERE tipo = 'INT' AND (valor_min IS NULL OR valor_max IS NULL OR unidade IS NULL)
                """,
                Integer.class);

        assertThat(semFaixa).isZero();
    }

    /**
     * O banco guarda referencia ao secret manager, nunca o token. Um valor que pareca credencial de
     * verdade aqui e o comeco de um segredo commitado.
     */
    @Test
    @DisplayName("a credencial de exemplo guarda referencia, nao token")
    void canalCredencial_seedAplicado_guardaApenasReferencia() {
        List<String> refs = jdbc.queryForList(
                "SELECT token_ref FROM canal_credencial WHERE token_ref IS NOT NULL", String.class);

        assertThat(refs).isNotEmpty().allMatch(ref -> ref.startsWith("secret://"));
    }

    private List<String> hashesDoSeed() {
        return jdbc.queryForList("SELECT senha_hash FROM usuario WHERE " + FILTRO_EMAILS_SEED, String.class);
    }
}
