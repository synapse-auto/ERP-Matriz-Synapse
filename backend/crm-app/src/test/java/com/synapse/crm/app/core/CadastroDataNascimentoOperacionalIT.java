package com.synapse.crm.app.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.synapse.crm.app.PostgresIT;

/** Executa o proprio artefato operacional contra PostgreSQL real, sem migration global. */
@SpringBootTest
@ActiveProfiles("dev")
class CadastroDataNascimentoOperacionalIT extends PostgresIT {

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    @AfterEach
    void limparCampoDoTeste() {
        jdbc.update("DELETE FROM campo_customizado WHERE chave = 'data_nascimento'");
        jdbc.update("DELETE FROM campo_customizado WHERE chave = 'e48_outro_campo'");
    }

    @Test
    @DisplayName("instancia sem metadado cadastra DATA depois dos campos existentes; repetir e neutro")
    void cadastraUmaVezSemReordenar() throws IOException {
        jdbc.update("""
                INSERT INTO campo_customizado (chave, rotulo, tipo, ordem)
                VALUES ('e48_outro_campo', 'Outro campo', 'TEXTO', 9)
                """);

        executarArtefato();
        Map<String, Object> primeiro = lerNascimento();
        executarArtefato();

        assertThat(primeiro)
                .containsEntry("chave", "data_nascimento")
                .containsEntry("rotulo", "Data de nascimento")
                .containsEntry("tipo", "DATA")
                .containsEntry("obrigatorio", false)
                .containsEntry("filtravel", false);
        assertThat(((Number) primeiro.get("ordem")).intValue()).isEqualTo(10);
        assertThat(primeiro.get("opcoes")).isNull();
        assertThat(lerNascimento()).isEqualTo(primeiro);
        assertThat(jdbc.queryForObject(
                        "SELECT ordem FROM campo_customizado WHERE chave = 'e48_outro_campo'",
                        Short.class))
                .isEqualTo((short) 9);
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM campo_customizado WHERE chave = 'data_nascimento'",
                        Integer.class))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("chave existente com tipo diferente aborta sem mudar metadados")
    void tipoIncompativelRecusado() throws IOException {
        jdbc.update("""
                INSERT INTO campo_customizado
                    (chave, rotulo, tipo, obrigatorio, filtravel, ordem)
                VALUES ('data_nascimento', 'Data de nascimento', 'TEXTO', false, false, 7)
                """);
        Map<String, Object> antes = lerNascimento();

        assertThatThrownBy(this::executarArtefato).isInstanceOf(DataAccessException.class);

        assertThat(lerNascimento()).isEqualTo(antes);
    }

    @Test
    @DisplayName("rotulo ou flags incompatíveis também não são sobrescritos")
    void metadadosIncompativeisRecusados() throws IOException {
        jdbc.update("""
                INSERT INTO campo_customizado
                    (chave, rotulo, tipo, obrigatorio, filtravel, ordem)
                VALUES ('data_nascimento', 'Aniversario', 'DATA', true, false, 7)
                """);
        Map<String, Object> antes = lerNascimento();

        assertThatThrownBy(this::executarArtefato).isInstanceOf(DataAccessException.class);

        assertThat(lerNascimento()).isEqualTo(antes);
    }

    private Map<String, Object> lerNascimento() {
        return jdbc.queryForMap("""
                SELECT chave, rotulo, tipo, opcoes, obrigatorio, filtravel, ordem
                  FROM campo_customizado WHERE chave = 'data_nascimento'
                """);
    }

    private void executarArtefato() throws IOException {
        Path atual = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (atual != null) {
            Path script = atual.resolve("docker/provisionamento/habilitar-data-nascimento.sql");
            if (Files.isRegularFile(script)) {
                jdbc.execute(Files.readString(script));
                return;
            }
            atual = atual.getParent();
        }
        throw new IOException("artefato operacional de data de nascimento nao encontrado");
    }
}
