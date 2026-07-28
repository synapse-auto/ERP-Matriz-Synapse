package com.synapse.crm.app.atendimento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
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
import com.synapse.crm.atendimento.application.AtendimentoRepositorio;
import com.synapse.crm.atendimento.application.EnviarMensagemUseCase;
import com.synapse.crm.atendimento.application.FinalizarAtendimentoUseCase;
import com.synapse.crm.atendimento.application.RecursoDeAtendimentoIndisponivelException;
import com.synapse.crm.atendimento.application.RegistrarMensagemRecebidaUseCase;
import com.synapse.crm.atendimento.application.TransferirAtendimentoUseCase;
import com.synapse.crm.atendimento.domain.atendimento.Atendimento;
import com.synapse.crm.atendimento.domain.atendimento.StatusAtendimento;
import com.synapse.crm.sharedkernel.identidade.ContextoDeServico;
import com.synapse.crm.sharedkernel.identidade.PapelUsuario;
import com.synapse.crm.sharedkernel.persistencia.Pools;

/**
 * Caminho critico de mensagem, ponta a ponta, com Postgres real.
 *
 * <p>Tres coisas esta suite existe para provar, e as tres sao do tipo que falha em silencio:
 *
 * <ol>
 *   <li>que os contadores e {@code ultima_interacao_em} sao escritos na <b>mesma</b> transacao da
 *       mensagem — a divida da E03b, cuja falha nao quebra nada, apenas faz o filtro
 *       {@code semRetornoDias} mentir;
 *   <li>que a RN-CRM-06 move o lead de fato, e que ela nao vira porta lateral para furar o
 *       isolamento de agenda da E02;
 *   <li>que os listeners <b>nao</b> rodam antes do commit — porque um listener dentro da transacao
 *       vira latencia no caminho que nao pode ficar indisponivel entre 08:00 e 18:30.
 * </ol>
 */
@SpringBootTest
@ActiveProfiles("dev")
class AtendimentoIT extends PostgresIT {

    private static final String PREFIXO = "E04-";
    private static final String CONTEUDO = "bom dia, quero um orcamento";

    @Autowired
    private RegistrarMensagemRecebidaUseCase registrarRecebida;

    @Autowired
    private EnviarMensagemUseCase enviar;

    @Autowired
    private TransferirAtendimentoUseCase transferir;

    @Autowired
    private FinalizarAtendimentoUseCase finalizar;

    @Autowired
    private AtendimentoRepositorio atendimentos;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    @Qualifier(Pools.CHAT_TRANSACTION_MANAGER) private PlatformTransactionManager gerenteDoChat;

    private TransactionTemplate transacaoDeChat;

    private UUID idAna;
    private UUID idBruno;
    private UUID idGestor;
    private UUID leadDaAna;
    private UUID leadSemDono;

    @BeforeEach
    void prepararCenario() {
        transacaoDeChat = new TransactionTemplate(gerenteDoChat);

        limpar();
        idAna = idDoUsuario("ana@dev.local");
        idBruno = idDoUsuario("bruno@dev.local");
        idGestor = idDoUsuario("gestor@dev.local");

        leadDaAna = criarLead("Cliente da Ana", idAna, "EM_ATENDIMENTO");
        // Grupo "Potenciais": sem dono, visivel a qualquer atendente.
        leadSemDono = criarLead("Cliente sem dono", null, "IA");
    }

    @AfterEach
    void limparContexto() {
        ApoioRls.sair();
    }

    @Nested
    @DisplayName("mensagem recebida")
    class Recebida {

        @Test
        @DisplayName("abre atendimento quando nao ha nenhum aberto")
        void recebida_semAtendimentoAberto_abreUm() {
            var resultado = comoServico(() -> registrarRecebida.executar(entrada(leadDaAna)));

            assertThat(resultado.abriuAtendimento()).isTrue();
            assertThat(resultado.atendimento().status()).isEqualTo(StatusAtendimento.EM_IA);
            assertThat(contarAtendimentos(leadDaAna)).isEqualTo(1);
        }

        /**
         * Sem reuso, cada mensagem do cliente viraria um atendimento e a metrica de atendimentos por
         * lead (RF-CRM-71) passaria a contar mensagens.
         */
        @Test
        @DisplayName("reusa o atendimento aberto na segunda mensagem")
        void recebida_comAtendimentoAberto_reusa() {
            var primeira = comoServico(() -> registrarRecebida.executar(entrada(leadDaAna)));
            var segunda = comoServico(() -> registrarRecebida.executar(entrada(leadDaAna)));

            assertThat(segunda.abriuAtendimento()).isFalse();
            assertThat(segunda.atendimento().id()).isEqualTo(primeira.atendimento().id());
            assertThat(contarAtendimentos(leadDaAna)).isEqualTo(1);
        }

        /**
         * O teste que paga a divida da E03b. As tres colunas sao escritas junto com a mensagem, na
         * mesma transacao — se algum caminho de escrita de mensagem nao passar pela porta
         * {@code LeadNoCaminhoDeMensagem}, ele aparece aqui.
         */
        @Test
        @DisplayName("contadores e ultima_interacao_em sao escritos com a mensagem")
        void recebida_atualizaContadoresEUltimaInteracao() {
            Instant antes = Instant.now().minusSeconds(1);

            comoServico(() -> registrarRecebida.executar(entrada(leadDaAna)));

            assertThat(contador(leadDaAna, "num_atendimentos")).isEqualTo(1);
            assertThat(contador(leadDaAna, "num_mensagens")).isEqualTo(1);
            assertThat(ultimaInteracao(leadDaAna)).isNotNull().isAfter(antes);

            // Segunda mensagem: soma mensagem, nao soma atendimento.
            comoServico(() -> registrarRecebida.executar(entrada(leadDaAna)));

            assertThat(contador(leadDaAna, "num_atendimentos")).isEqualTo(1);
            assertThat(contador(leadDaAna, "num_mensagens")).isEqualTo(2);
        }

        /**
         * O par negativo do teste acima. Se a mensagem gravasse numa conexao e os contadores em
         * outra, o rollback derrubaria so uma parte — e o teste anterior continuaria verde.
         */
        @Test
        @DisplayName("rollback nao deixa mensagem, contador nem evento")
        void rollback_naoDeixaRastro() {
            assertThatThrownBy(() -> comoServico(() -> transacaoDeChat.execute(status -> {
                        registrarRecebida.executar(entrada(leadDaAna));
                        throw new IllegalStateException("falha proposital depois de registrar");
                    })))
                    .isInstanceOf(IllegalStateException.class);

            assertThat(contador(leadDaAna, "num_mensagens")).isZero();
            assertThat(contador(leadDaAna, "num_atendimentos")).isZero();
            assertThat(ultimaInteracao(leadDaAna)).isNull();
            assertThat(contarAtendimentos(leadDaAna)).isZero();
            assertThat(contarTimeline(leadDaAna)).isZero();
        }
    }

    @Nested
    @DisplayName("RN-CRM-06: enviar mensagem transfere o lead")
    class EnvioTransfereLead {

        /**
         * O caso real da regra: o lead esta no grupo "Potenciais" e o Bruno responde primeiro. O lead
         * passa a ser dele, e a timeline registra quem assumiu.
         */
        @Test
        @DisplayName("atendente que responde lead sem dono passa a ser o dono")
        void envio_emLeadSemDono_transfereParaQuemEnviou() {
            ApoioRls.entrarComo(idBruno, PapelUsuario.ATENDENTE);

            var resultado = enviar.executar(leadSemDono, CONTEUDO);

            assertThat(resultado.transferiuOLead()).isTrue();
            assertThat(donoDoLead(leadSemDono)).isEqualTo(idBruno);
            assertThat(tiposDaTimeline(leadSemDono)).contains("LEAD_TRANSFERIDO_POR_ENVIO");
            assertThat(descricoesDaTimeline(leadSemDono))
                    .anySatisfy(descricao -> assertThat(descricao).contains(idBruno.toString()));
        }

        /**
         * A RN-CRM-06 <b>nao</b> e porta lateral para furar a RN-CRM-01. O Bruno nao alcanca o lead
         * da Ana, entao nao ha o que transferir — e a resposta e a mesma de "nao existe".
         */
        @Test
        @DisplayName("atendente nao alcanca o lead do colega, nem para transferi-lo")
        void envio_emLeadDeColega_naoTransfereNemVaza() {
            ApoioRls.entrarComo(idBruno, PapelUsuario.ATENDENTE);

            assertThatThrownBy(() -> enviar.executar(leadDaAna, CONTEUDO))
                    .isInstanceOf(RecursoDeAtendimentoIndisponivelException.class);

            assertThat(donoDoLead(leadDaAna)).isEqualTo(idAna);
            assertThat(contador(leadDaAna, "num_mensagens")).isZero();
        }

        /**
         * Quem enxerga a base inteira alcanca qualquer lead — e, para esse, a regra transfere de
         * fato. E o unico papel para o qual "mandar mensagem no lead de outro" e operacao possivel.
         */
        @Test
        @DisplayName("gestor que responde lead de atendente assume o lead")
        void envio_gestorEmLeadDeAtendente_transfere() {
            ApoioRls.entrarComo(idGestor, PapelUsuario.GESTOR);

            var resultado = enviar.executar(leadDaAna, CONTEUDO);

            assertThat(resultado.transferiuOLead()).isTrue();
            assertThat(donoDoLead(leadDaAna)).isEqualTo(idGestor);
            assertThat(descricoesDaTimeline(leadDaAna))
                    .anySatisfy(descricao -> assertThat(descricao).contains("antes de " + idAna));
        }

        /** Responder o proprio lead nao e transferencia: nao pode gerar evento de troca de dono. */
        @Test
        @DisplayName("responder o proprio lead nao registra transferencia")
        void envio_noProprioLead_naoTransfere() {
            ApoioRls.entrarComo(idAna, PapelUsuario.ATENDENTE);

            var resultado = enviar.executar(leadDaAna, CONTEUDO);

            assertThat(resultado.transferiuOLead()).isFalse();
            assertThat(donoDoLead(leadDaAna)).isEqualTo(idAna);
            assertThat(tiposDaTimeline(leadDaAna))
                    .contains("MENSAGEM_ENVIADA")
                    .doesNotContain("LEAD_TRANSFERIDO_POR_ENVIO");
        }

        @Test
        @DisplayName("o envio tambem soma mensagem e marca a interacao")
        void envio_atualizaContadoresEUltimaInteracao() {
            ApoioRls.entrarComo(idAna, PapelUsuario.ATENDENTE);

            enviar.executar(leadDaAna, CONTEUDO);

            assertThat(contador(leadDaAna, "num_mensagens")).isEqualTo(1);
            assertThat(ultimaInteracao(leadDaAna)).isNotNull();
        }
    }

    @Nested
    @DisplayName("transferencia e finalizacao")
    class Ciclo {

        @Test
        @DisplayName("transferir move o atendimento e o lead juntos")
        void transferir_paraOutroAtendente_moveOsDois() {
            ApoioRls.entrarComo(idAna, PapelUsuario.ATENDENTE);
            UUID atendimentoId = enviar.executar(leadDaAna, CONTEUDO).atendimento().id();

            ApoioRls.entrarComo(idGestor, PapelUsuario.GESTOR);
            Atendimento depois = transferir.executar(atendimentoId, idBruno, idGestor);

            assertThat(depois.pertenceA(idBruno)).isTrue();
            assertThat(donoDoLead(leadDaAna)).isEqualTo(idBruno);
            assertThat(tiposDaTimeline(leadDaAna)).contains("ATENDIMENTO_TRANSFERIDO");
        }

        @Test
        @DisplayName("devolver para a IA solta o lead de volta para Potenciais")
        void transferir_paraIa_devolveOLead() {
            ApoioRls.entrarComo(idAna, PapelUsuario.ATENDENTE);
            UUID atendimentoId = enviar.executar(leadDaAna, CONTEUDO).atendimento().id();

            ApoioRls.entrarComo(idGestor, PapelUsuario.GESTOR);
            Atendimento depois = transferir.executar(atendimentoId, null, idGestor);

            assertThat(depois.status()).isEqualTo(StatusAtendimento.EM_IA);
            assertThat(statusDoLead(leadDaAna)).isEqualTo("IA");
        }

        @Test
        @DisplayName("finalizar encerra o atendimento e o lead")
        void finalizar_atendimentoAberto_encerraOsDois() {
            ApoioRls.entrarComo(idAna, PapelUsuario.ATENDENTE);
            UUID atendimentoId = enviar.executar(leadDaAna, CONTEUDO).atendimento().id();

            Atendimento encerrado = finalizar.executar(atendimentoId, idAna);

            assertThat(encerrado.status()).isEqualTo(StatusAtendimento.FINALIZADO);
            assertThat(encerrado.finalizadoEm()).isNotNull();
            assertThat(statusDoLead(leadDaAna)).isEqualTo("FINALIZADO");
            assertThat(tiposDaTimeline(leadDaAna)).contains("ATENDIMENTO_FINALIZADO");
            assertThat(contarAuditoria(leadDaAna, "ATENDIMENTO_FINALIZADO")).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("eventos e isolamento")
    class EventosEIsolamento {

        /**
         * O teste que prova a fase. Dentro da transacao a timeline ainda esta vazia; depois do
         * commit, a linha aparece. Se algum listener virasse sincrono, a primeira assercao falharia —
         * e e ela que protege a latencia do caminho critico.
         */
        @Test
        @DisplayName("os listeners so escrevem depois do commit")
        void listeners_naoRodamAntesDoCommit() {
            comoServico(() -> transacaoDeChat.execute(status -> {
                registrarRecebida.executar(entrada(leadDaAna));

                // Ainda dentro da transacao do chat: nenhuma reacao pode ter acontecido.
                assertThat(contarTimeline(leadDaAna)).isZero();
                return null;
            }));

            assertThat(contarTimeline(leadDaAna)).isEqualTo(1);
        }

        /**
         * A politica RLS de {@code atendimento} ja existia desde a E02b. O que este teste confirma e
         * que o caminho novo — repositorio JDBC no pool do chat — passa por ela, e nao por fora.
         */
        @Test
        @DisplayName("atendente nao le atendimento de lead alheio")
        void rls_atendimentoDeColega_naoEAlcancado() {
            ApoioRls.entrarComo(idAna, PapelUsuario.ATENDENTE);
            UUID atendimentoDaAna = enviar.executar(leadDaAna, CONTEUDO).atendimento().id();

            ApoioRls.entrarComo(idBruno, PapelUsuario.ATENDENTE);
            var comoBruno = transacaoDeChat.execute(status -> atendimentos.porId(atendimentoDaAna));
            assertThat(comoBruno).isEmpty();

            // O par positivo: sem ele, este teste passaria ate com o repositorio quebrado.
            ApoioRls.entrarComo(idAna, PapelUsuario.ATENDENTE);
            var comoAna = transacaoDeChat.execute(status -> atendimentos.porId(atendimentoDaAna));
            assertThat(comoAna).isPresent();
        }
    }

    // --- apoio ----------------------------------------------------------------

    private RegistrarMensagemRecebidaUseCase.MensagemRecebida entrada(UUID leadId) {
        return new RegistrarMensagemRecebidaUseCase.MensagemRecebida(leadId, null, null, CONTEUDO);
    }

    /** O webhook de canal (E05) roda assim: sem usuario, com contexto de servico. */
    private <T> T comoServico(Supplier<T> acao) {
        ApoioRls.sair();
        return ContextoDeServico.buscarComo("teste-canal", acao);
    }

    private void limpar() {
        jdbc.update(
                "DELETE FROM audit_log WHERE lead_id IN (SELECT id FROM lead WHERE nome LIKE ?)",
                PREFIXO + "%");
        jdbc.update(
                "DELETE FROM evento_timeline WHERE lead_id IN (SELECT id FROM lead WHERE nome LIKE ?)",
                PREFIXO + "%");
        jdbc.update(
                """
                DELETE FROM mensagem WHERE atendimento_id IN (
                    SELECT a.id FROM atendimento a
                      JOIN lead l ON l.id = a.lead_id
                     WHERE l.nome LIKE ?)
                """,
                PREFIXO + "%");
        jdbc.update(
                "DELETE FROM atendimento WHERE lead_id IN (SELECT id FROM lead WHERE nome LIKE ?)",
                PREFIXO + "%");
        jdbc.update("DELETE FROM lead WHERE nome LIKE ?", PREFIXO + "%");
    }

    private UUID idDoUsuario(String email) {
        return jdbc.queryForObject("SELECT id FROM usuario WHERE email = ?", UUID.class, email);
    }

    private UUID criarLead(String nome, UUID dono, String status) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO lead (id, nome, atendente_responsavel_id, status_basico)"
                        + " VALUES (?, ?, ?, ?::status_basico_lead)",
                id,
                PREFIXO + nome,
                dono,
                status);
        return id;
    }

    private int contador(UUID leadId, String coluna) {
        return jdbc.queryForObject("SELECT " + coluna + " FROM lead WHERE id = ?", Integer.class, leadId);
    }

    private Instant ultimaInteracao(UUID leadId) {
        Timestamp valor = jdbc.queryForObject(
                "SELECT ultima_interacao_em FROM lead WHERE id = ?", Timestamp.class, leadId);
        return valor == null ? null : valor.toInstant();
    }

    private UUID donoDoLead(UUID leadId) {
        return jdbc.queryForObject(
                "SELECT atendente_responsavel_id FROM lead WHERE id = ?", UUID.class, leadId);
    }

    private String statusDoLead(UUID leadId) {
        return jdbc.queryForObject(
                "SELECT status_basico::text FROM lead WHERE id = ?", String.class, leadId);
    }

    private int contarAtendimentos(UUID leadId) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM atendimento WHERE lead_id = ?", Integer.class, leadId);
    }

    private int contarTimeline(UUID leadId) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM evento_timeline WHERE lead_id = ?", Integer.class, leadId);
    }

    private int contarAuditoria(UUID leadId, String acao) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM audit_log WHERE lead_id = ? AND acao = ?",
                Integer.class,
                leadId,
                acao);
    }

    private List<String> tiposDaTimeline(UUID leadId) {
        return jdbc.queryForList(
                "SELECT tipo FROM evento_timeline WHERE lead_id = ?", String.class, leadId);
    }

    private List<String> descricoesDaTimeline(UUID leadId) {
        return jdbc.queryForList(
                "SELECT descricao FROM evento_timeline WHERE lead_id = ?", String.class, leadId);
    }
}
