package com.synapse.crm.app;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

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
    @DisplayName("as etapas do funil sao criadas em ordem, de Novo contato a Pos-venda")
    void etapas_seedAplicado_ficamEmOrdem() {
        List<String> nomes =
                jdbc.queryForList("SELECT nome FROM etapa_atendimento ORDER BY ordem", String.class);

        assertThat(nomes)
                .startsWith("Novo contato")
                .endsWith("Pos-venda")
                .hasSizeGreaterThanOrEqualTo(5);
    }

    @Test
    @DisplayName("existe um usuario para cada papel")
    void usuarios_seedAplicado_cobremTodosOsPapeis() {
        List<String> papeis = jdbc.queryForList(
                "SELECT DISTINCT papel::text FROM usuario WHERE email LIKE '%@dev.local'", String.class);

        assertThat(papeis)
                .containsExactlyInAnyOrder("ADMINISTRADOR", "GESTOR", "SUBGESTOR", "ATENDENTE");
    }

    @Test
    @DisplayName("as senhas do seed sao hashes BCrypt, nunca texto puro")
    void usuarios_seedAplicado_guardamBcrypt() {
        List<String> hashes = jdbc.queryForList(
                "SELECT senha_hash FROM usuario WHERE email LIKE '%@dev.local'", String.class);

        assertThat(hashes).isNotEmpty().allMatch(hash -> hash.startsWith("$2a$"));
    }

    @Test
    @DisplayName("as feature flags iniciais existem")
    void featureFlags_seedAplicado_existemAsIniciais() {
        List<String> chaves = jdbc.queryForList("SELECT chave FROM feature_flag", String.class);

        assertThat(chaves).contains("campanhas", "chat_interno", "fidelizacao", "relatorios", "dashboard");
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
}
