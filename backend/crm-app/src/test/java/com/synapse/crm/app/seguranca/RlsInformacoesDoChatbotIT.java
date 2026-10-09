package com.synapse.crm.app.seguranca;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import com.synapse.crm.app.PostgresIT;
import com.synapse.crm.sharedkernel.identidade.ContextoDeServico;
import com.synapse.crm.sharedkernel.identidade.PapelUsuario;

/**
 * RLS de {@code atendimento_informacao_chatbot} por SQL cru, fora de qualquer camada da aplicacao.
 *
 * <p>A politica de leitura herda a de {@code atendimento}; a de escrita so existe para o contexto de
 * servico, e nao ha politica de UPDATE/DELETE: o snapshot e imutavel. Sem estes testes, uma politica
 * escrita errada (ou ignorada por role de superusuario) passaria despercebida — o build ficaria verde
 * e nada estaria protegido.
 */
@SpringBootTest
class RlsInformacoesDoChatbotIT extends PostgresIT {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TransactionTemplate transacao;

    private String marcador;
    private UUID ana;
    private UUID bruno;
    private UUID gestor;
    private UUID atendimentoDaAna;
    private UUID cardId;

    @BeforeEach
    void preparar() {
        marcador = "rlsinfo" + UUID.randomUUID().toString().substring(0, 8);
        ana = criarUsuario("Ana", PapelUsuario.ATENDENTE);
        bruno = criarUsuario("Bruno", PapelUsuario.ATENDENTE);
        gestor = criarUsuario("Gestora", PapelUsuario.GESTOR);
        UUID lead = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO lead (id, nome, atendente_responsavel_id, status_basico)"
                        + " VALUES (?, ?, ?, CAST('EM_ATENDIMENTO' AS status_basico_lead))",
                lead, marcador + " lead da ana", ana);
        atendimentoDaAna = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO atendimento (id, lead_id, atendente_id, status)"
                        + " VALUES (?, ?, ?, CAST('EM_ATENDIMENTO' AS status_atendimento))",
                atendimentoDaAna, lead, ana);
        cardId = UUID.randomUUID();
        comoServico(() -> jdbc.update(
                "INSERT INTO atendimento_informacao_chatbot (id, atendimento_id, chave_idempotencia, conteudo)"
                        + " VALUES (?, ?, ?, 'Nome: Maria')",
                cardId, atendimentoDaAna, marcador + "-chave"));
    }

    @AfterEach
    void limpar() {
        ApoioRls.sair();
        comoServico(() -> {
            jdbc.update("DELETE FROM atendimento WHERE lead_id IN (SELECT id FROM lead WHERE nome LIKE ?)", marcador + "%");
            jdbc.update("DELETE FROM lead WHERE nome LIKE ?", marcador + "%");
            return 0;
        });
        jdbc.update("DELETE FROM usuario WHERE email LIKE ?", marcador + "%");
    }

    @Test
    @DisplayName("o responsavel e a gestao leem o card; o colega sem acesso e o contexto vazio nao")
    void leituraSegueAVisibilidadeDoAtendimento() {
        assertThat(lidosComo(ana, PapelUsuario.ATENDENTE)).isEqualTo(1);
        assertThat(lidosComo(gestor, PapelUsuario.GESTOR)).isEqualTo(1);
        assertThat(lidosComo(bruno, PapelUsuario.ATENDENTE)).as("colega sem acesso ao atendimento").isZero();

        ApoioRls.sair();
        assertThat(contarCards()).as("sem contexto a transacao falha fechada").isZero();
    }

    @Test
    @DisplayName("usuario nao escreve card, nem o dono do atendimento nem a gestao")
    void somenteServicoInsere() {
        ApoioRls.entrarComo(ana, PapelUsuario.ATENDENTE);
        assertThatThrownBy(() -> inserirEmTransacao("forjado-pela-ana")).isInstanceOf(DataAccessException.class);

        ApoioRls.entrarComo(gestor, PapelUsuario.GESTOR);
        assertThatThrownBy(() -> inserirEmTransacao("forjado-pela-gestora")).isInstanceOf(DataAccessException.class);

        assertThat(comoServico(this::contarCards)).isEqualTo(1);
    }

    @Test
    @DisplayName("o snapshot e imutavel: nem o servico altera ou apaga o card pela aplicacao")
    void snapshotImutavel() {
        // Duas barreiras independentes: o privilegio foi revogado (V101) e, mesmo que alguem o devolva,
        // nao existe politica RLS de UPDATE/DELETE. A primeira falha com erro; a segunda, com 0 linhas.
        for (String privilegio : new String[] {"UPDATE", "DELETE"}) {
            assertThat(jdbc.queryForObject(
                            "SELECT has_table_privilege('synapse_app', 'atendimento_informacao_chatbot', ?)",
                            Boolean.class, privilegio))
                    .as("synapse_app nao deve ter %s", privilegio).isFalse();
        }
        assertThatThrownBy(() -> comoServico(() -> jdbc.update(
                        "UPDATE atendimento_informacao_chatbot SET conteudo = 'reescrito' WHERE id = ?", cardId)))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> comoServico(() -> jdbc.update(
                        "DELETE FROM atendimento_informacao_chatbot WHERE id = ?", cardId)))
                .isInstanceOf(DataAccessException.class);

        assertThat(comoServico(() -> jdbc.queryForObject(
                        "SELECT conteudo FROM atendimento_informacao_chatbot WHERE id = ?", String.class, cardId)))
                .isEqualTo("Nome: Maria");
    }

    @Test
    @DisplayName("a mesma chave de idempotencia nao entra duas vezes, mesmo por SQL")
    void chaveUnica() {
        assertThatThrownBy(() -> comoServico(() -> jdbc.update(
                        "INSERT INTO atendimento_informacao_chatbot (id, atendimento_id, chave_idempotencia, conteudo)"
                                + " VALUES (?, ?, ?, 'duplicado')",
                        UUID.randomUUID(), atendimentoDaAna, marcador + "-chave")))
                .isInstanceOf(DataAccessException.class);
    }

    // --- apoio ---------------------------------------------------------------------------------------

    private int lidosComo(UUID usuario, PapelUsuario papel) {
        ApoioRls.entrarComo(usuario, papel);
        return contarCards();
    }

    private int contarCards() {
        return transacao.execute(status -> jdbc.queryForObject(
                "SELECT count(*) FROM atendimento_informacao_chatbot WHERE atendimento_id = ?",
                Integer.class, atendimentoDaAna));
    }

    private void inserirEmTransacao(String chave) {
        transacao.executeWithoutResult(status -> jdbc.update(
                "INSERT INTO atendimento_informacao_chatbot (id, atendimento_id, chave_idempotencia, conteudo)"
                        + " VALUES (?, ?, ?, 'forjado')",
                UUID.randomUUID(), atendimentoDaAna, marcador + chave));
    }

    private <T> T comoServico(java.util.function.Supplier<T> acao) {
        return ContextoDeServico.buscarComo("teste-rls-informacoes-chatbot", () -> transacao.execute(status -> acao.get()));
    }

    private UUID criarUsuario(String nome, PapelUsuario papel) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO usuario (id, nome, email, senha_hash, papel)"
                        + " VALUES (?, ?, ?, 'x', CAST(? AS papel_usuario))",
                id, nome, marcador + "-" + nome + "@rls.test", papel.name());
        return id;
    }
}
