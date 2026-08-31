package com.synapse.crm.app.core;

import static com.synapse.crm.app.seguranca.ApoioAutenticacao.EMAIL_ANA;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.StringReader;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.synapse.crm.app.PostgresIT;
import com.synapse.crm.core.application.lead.importacao.PrepararImportacaoLeadsCsv;
import com.synapse.crm.core.application.lead.importacao.PrepararImportacaoLeadsCsv.Resultado;
import com.synapse.crm.core.infrastructure.persistencia.lead.SqlDaImportacaoOperacionalDeLeads;

@SpringBootTest
@ActiveProfiles("dev")
class ImportacaoOperacionalLeadsCsvIT extends PostgresIT {

    private static final String PREFIXO = "E105-importacao-";

    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void limpar() {
        jdbc.update(
                "DELETE FROM mensagem WHERE atendimento_id IN (SELECT a.id FROM atendimento a JOIN lead l ON l.id = a.lead_id WHERE l.nome LIKE ?)",
                PREFIXO + "%");
        jdbc.update(
                "DELETE FROM atendimento WHERE lead_id IN (SELECT id FROM lead WHERE nome LIKE ?)",
                PREFIXO + "%");
        jdbc.update("DELETE FROM lead WHERE nome LIKE ?", PREFIXO + "%");
    }

    @Test
    void reimportarNaoDuplicaEPreservaLeadExistenteComDonoEHistorico() throws Exception {
        UUID atendente = jdbc.queryForObject(
                "SELECT id FROM usuario WHERE email = ?", UUID.class, EMAIL_ANA);
        UUID existente = UUID.randomUUID();
        UUID atendimento = UUID.randomUUID();
        UUID mensagem = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO lead (id,nome,telefone,status_basico,atendente_responsavel_id,num_atendimentos,num_mensagens) VALUES (?,?,?,'EM_ATENDIMENTO',?,1,1)",
                existente,
                PREFIXO + "existente",
                "5561999999999",
                atendente);
        jdbc.update(
                "INSERT INTO atendimento (id,lead_id,atendente_id,status,iniciado_em) VALUES (?,?,?,'EM_ATENDIMENTO',now())",
                atendimento,
                existente,
                atendente);
        jdbc.update(
                "INSERT INTO mensagem (id,atendimento_id,remetente_tipo,remetente_id,tipo,conteudo,enviado_em) VALUES (?,?,'ATENDENTE',?,'TEXTO','historico preservado',now())",
                mensagem,
                atendimento,
                atendente);

        Resultado preparado = new PrepararImportacaoLeadsCsv("55").executar(new StringReader("""
                nome;telefone
                E105-importacao-nao-sobrescrever;+55 61 99999-9999
                E105-importacao-novo;(61) 98888-8888
                """));

        int primeira = importar(preparado);
        int segunda = importar(preparado);

        assertThat(primeira).isEqualTo(1);
        assertThat(segunda).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM lead WHERE telefone IN (?, ?)", Integer.class,
                        "5561999999999", "5561988888888"))
                .isEqualTo(2);
        assertThat(jdbc.queryForMap(
                        "SELECT nome,status_basico::text,atendente_responsavel_id,num_atendimentos,num_mensagens FROM lead WHERE id = ?",
                        existente))
                .containsEntry("nome", PREFIXO + "existente")
                .containsEntry("status_basico", "EM_ATENDIMENTO")
                .containsEntry("atendente_responsavel_id", atendente)
                .containsEntry("num_atendimentos", 1)
                .containsEntry("num_mensagens", 1);
        assertThat(jdbc.queryForObject("SELECT conteudo FROM mensagem WHERE id = ?", String.class, mensagem))
                .isEqualTo("historico preservado");
        assertThat(jdbc.queryForMap(
                        "SELECT status_basico::text,atendente_responsavel_id FROM lead WHERE telefone = ?",
                        "5561988888888"))
                .containsEntry("status_basico", "IA")
                .containsEntry("atendente_responsavel_id", null);
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM atendimento WHERE lead_id = (SELECT id FROM lead WHERE telefone = ?)",
                        Integer.class,
                        "5561988888888"))
                .isZero();
    }

    private int importar(Resultado resultado) {
        return jdbc.execute((ConnectionCallback<Integer>) conexao -> importarNaConexao(conexao, resultado));
    }

    private static int importarNaConexao(Connection conexao, Resultado resultado) throws SQLException {
        boolean autoCommit = conexao.getAutoCommit();
        conexao.setAutoCommit(false);
        try (Statement sql = conexao.createStatement()) {
            sql.execute(SqlDaImportacaoOperacionalDeLeads.criarTabelaTemporaria());
            try (PreparedStatement inserir = conexao.prepareStatement(
                    "INSERT INTO " + SqlDaImportacaoOperacionalDeLeads.TABELA_TEMPORARIA
                            + " (nome,telefone) VALUES (?,?)")) {
                for (var lead : resultado.aceitos()) {
                    inserir.setString(1, lead.nome());
                    inserir.setString(2, lead.telefone());
                    inserir.addBatch();
                }
                inserir.executeBatch();
            }
            int inseridos = sql.executeUpdate(SqlDaImportacaoOperacionalDeLeads.inserirLeads());
            conexao.commit();
            return inseridos;
        } catch (Exception e) {
            conexao.rollback();
            throw e;
        } finally {
            conexao.setAutoCommit(autoCommit);
        }
    }
}
