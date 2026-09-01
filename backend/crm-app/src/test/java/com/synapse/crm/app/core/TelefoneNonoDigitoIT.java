package com.synapse.crm.app.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.synapse.crm.app.PostgresIT;
import com.synapse.crm.app.seguranca.ApoioRls;
import com.synapse.crm.core.application.lead.LeadNoCaminhoDeMensagem;
import com.synapse.crm.core.domain.lead.TelefoneCanonico;
import com.synapse.crm.sharedkernel.identidade.ContextoDeServico;
import com.synapse.crm.sharedkernel.identidade.PapelUsuario;
import com.synapse.crm.sharedkernel.persistencia.Pools;

/**
 * E111, ponta a ponta: o cliente com e sem o nono digito e um cliente so.
 *
 * <p>Duas coisas que so um Postgres real prova:
 *
 * <ol>
 *   <li>que a regra em Java e a regra em SQL (a V50) concordam — sao duas implementacoes da mesma
 *       decisao, e uma divergencia entre elas fundiria um cliente e normalizaria outro;
 *   <li>que o webhook, que recebe 12 digitos, e o atendente, que digita 13, chegam ao <b>mesmo</b>
 *       lead — que e a causa provada do "nao consigo puxar o cliente" (E105, Parte 1).
 * </ol>
 */
@SpringBootTest
@ActiveProfiles("dev")
class TelefoneNonoDigitoIT extends PostgresIT {

    private static final String PREFIXO = "E111-";

    /** O mesmo numero nos formatos que chegam ao sistema. */
    private static final String COMO_A_META_ENTREGA = "556181536371";

    private static final String COMO_O_ATENDENTE_DIGITA = "+55 (61) 98153-6371";

    private static final String CANONICO = "5561981536371";

    @Autowired
    private TelefoneCanonico telefoneCanonico;

    @Autowired
    private LeadNoCaminhoDeMensagem leads;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    @Qualifier(Pools.CHAT_TRANSACTION_MANAGER) private PlatformTransactionManager gerenteDoChat;

    private TransactionTemplate transacaoDeChat;

    @BeforeEach
    void prepararCenario() {
        transacaoDeChat = new TransactionTemplate(gerenteDoChat);
        limpar();
    }

    @AfterEach
    void limparContexto() {
        ApoioRls.sair();
        limpar();
    }

    /**
     * O par do teste de paridade da RLS: a regra existe duas vezes, entao alguem precisa provar que
     * as duas concordam. Sem isto, a migration normalizaria de um jeito e o runtime de outro — e o
     * sintoma seria um lead novo por mensagem recebida.
     */
    @Nested
    @DisplayName("paridade entre a regra em Java e a regra em SQL")
    class Paridade {

        @Test
        @DisplayName("dominio e app_telefone_canonico concordam em toda a tabela de casos")
        void dominioESql_concordam() {
            // Uma unica tabela de casos, consumida pelas duas implementacoes.
            List<String> casos = List.of(
                    "556181536371",
                    "556192729612",
                    "556198430401",
                    "5561981536371",
                    "556132241234",
                    "556122241234",
                    "556152241234",
                    "6181536371",
                    "6132241234",
                    "61999999999",
                    "351219999999",
                    "541199999999",
                    "556101111111",
                    "556111111111",
                    "55619815363712",
                    "+55 (61) 98153-6371",
                    "(61) 8153-6371",
                    "61 3224-1234");

            for (String caso : casos) {
                assertThat(peloBanco(caso))
                        .as("Java e SQL devem produzir o mesmo canonico para %s", caso)
                        .isEqualTo(telefoneCanonico.normalizar(caso));
            }
        }

        /** Onde o Java recusa, o SQL devolve NULL: o mesmo "isto nao e telefone", em duas linguagens. */
        @Test
        @DisplayName("entrada curta demais e recusada no dominio e nula no SQL")
        void entradaInvalida_recusadaNosDois() {
            assertThat(peloBanco("1234")).isNull();
            assertThat(peloBanco(" + - ")).isNull();
            assertThat(peloBanco(null)).isNull();
            assertThat(telefoneCanonico.normalizar(null)).isNull();
        }

        private String peloBanco(String entrada) {
            return jdbc.queryForObject("SELECT app_telefone_canonico(?, ?)", String.class, entrada, "55");
        }
    }

    @Nested
    @DisplayName("o cliente com e sem o nono digito e um lead so")
    class MesmoLead {

        /**
         * O caso que prova a etapa. O webhook cria o lead com o {@code wa_id} de 12 digitos; a
         * segunda mensagem chega igual; o atendente puxa o cliente digitando 13. Antes da E111 isso
         * eram tres caminhos e dois cadastros.
         */
        @Test
        @DisplayName("webhook com 12 digitos e atendente com 13 caem no mesmo lead")
        void webhookEAtendente_mesmoLead() {
            UUID pelaPrimeiraMensagem = comoServico(() -> transacaoDeChat.execute(
                    status -> leads.resolverPorTelefone(COMO_A_META_ENTREGA, PREFIXO + "Adjair")));

            UUID pelaSegundaMensagem = comoServico(() -> transacaoDeChat.execute(
                    status -> leads.resolverPorTelefone(COMO_A_META_ENTREGA, PREFIXO + "Adjair")));

            assertThat(pelaSegundaMensagem)
                    .as("a segunda mensagem reusa o lead da primeira")
                    .isEqualTo(pelaPrimeiraMensagem);

            assertThat(jdbc.queryForObject(
                            "SELECT telefone FROM lead WHERE id = ?", String.class, pelaPrimeiraMensagem))
                    .as("o lead e gravado ja canonico")
                    .isEqualTo(CANONICO);

            Optional<UUID> peloAtendente = comoServico(
                    () -> transacaoDeChat.execute(status -> leads.visivelPorTelefone(COMO_O_ATENDENTE_DIGITA)));

            assertThat(peloAtendente)
                    .as("puxar pelo telefone com o nono digito acha o lead que entrou sem ele")
                    .contains(pelaPrimeiraMensagem);

            assertThat(quantosLeadsComOTelefone())
                    .as("nenhum cadastro extra e criado")
                    .isEqualTo(1);
        }

        /**
         * Regressao da E105, Parte 1. O atendente digitava o numero com o nono digito, o lead existia
         * com 12, a busca nao achava, o insert batia no indice unico e a tela devolvia 404.
         */
        @Test
        @DisplayName("E105: o atendente enxerga pelo telefone com nono digito o lead que entrou com 12")
        void puxarCliente_comNonoDigito_achaOLeadQueEntrouSemEle() {
            UUID doWebhook = comoServico(() -> transacaoDeChat.execute(
                    status -> leads.resolverPorTelefone(COMO_A_META_ENTREGA, PREFIXO + "Adjair")));

            UUID atendente = idDoUsuario("ana@dev.local");
            ApoioRls.entrarComo(atendente, PapelUsuario.ATENDENTE);

            // Lead sem dono nasce em IA (grupo Potenciais): alcancavel por qualquer atendente.
            Optional<UUID> encontrado =
                    transacaoDeChat.execute(status -> leads.visivelPorTelefone("5561981536371"));

            assertThat(encontrado).contains(doWebhook);
        }

        /** O par negativo: o fixo do mesmo DDD nao pode virar o celular por engano. */
        @Test
        @DisplayName("fixo do mesmo DDD continua sendo outro lead")
        void fixo_naoSeConfundeComCelular() {
            UUID celular = comoServico(() -> transacaoDeChat.execute(
                    status -> leads.resolverPorTelefone("556181536371", PREFIXO + "Celular")));
            UUID fixo = comoServico(() -> transacaoDeChat.execute(
                    status -> leads.resolverPorTelefone("556132241234", PREFIXO + "Fixo")));

            assertThat(fixo).isNotEqualTo(celular);
            assertThat(jdbc.queryForObject("SELECT telefone FROM lead WHERE id = ?", String.class, fixo))
                    .isEqualTo("556132241234");
        }
    }

    // --- apoio ----------------------------------------------------------------

    /** O webhook de canal roda assim: sem usuario, com contexto de servico. */
    private <T> T comoServico(Supplier<T> acao) {
        ApoioRls.sair();
        return ContextoDeServico.buscarComo("teste-e111", acao);
    }

    private int quantosLeadsComOTelefone() {
        Integer total = jdbc.queryForObject(
                "SELECT count(*) FROM lead WHERE telefone IN (?, ?)",
                Integer.class,
                CANONICO,
                COMO_A_META_ENTREGA);
        return total == null ? 0 : total;
    }

    private UUID idDoUsuario(String email) {
        return jdbc.queryForObject("SELECT id FROM usuario WHERE email = ?", UUID.class, email);
    }

    private void limpar() {
        jdbc.update(
                "DELETE FROM evento_timeline WHERE lead_id IN (SELECT id FROM lead WHERE nome LIKE ?)",
                PREFIXO + "%");
        jdbc.update(
                "DELETE FROM atendimento WHERE lead_id IN (SELECT id FROM lead WHERE nome LIKE ?)",
                PREFIXO + "%");
        jdbc.update("DELETE FROM lead WHERE nome LIKE ?", PREFIXO + "%");
    }
}
