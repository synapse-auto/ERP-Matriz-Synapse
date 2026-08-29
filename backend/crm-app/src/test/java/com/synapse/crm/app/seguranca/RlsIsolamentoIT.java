package com.synapse.crm.app.seguranca;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import com.synapse.crm.app.PostgresIT;
import com.synapse.crm.app.config.DataSourceConfig;
import com.synapse.crm.core.domain.lead.Lead;
import com.synapse.crm.core.domain.lead.StatusBasicoLead;
import com.synapse.crm.core.domain.lead.VisibilidadeLead;
import com.synapse.crm.sharedkernel.identidade.ContextoDeServico;
import com.synapse.crm.sharedkernel.identidade.PapelUsuario;
import com.synapse.crm.sharedkernel.identidade.UsuarioAutenticado;

/**
 * RLS por SQL cru — fora do {@code LeadRepositorio} e de todas as camadas da E02.
 *
 * <p>E esse o ponto da etapa: provar que o isolamento vale tambem para quem nao passa pela
 * aplicacao, que e o caso dos read models de dashboard e relatorio (docs/01 secao 2.2) e de uma
 * consulta manual no psql.
 *
 * <p>Pool de uma conexao so, de proposito: assim todas as transacoes do teste compartilham a mesma
 * conexao fisica, e o teste de vazamento entre transacoes passa a ter valor real.
 */
@SpringBootTest
class RlsIsolamentoIT extends PostgresIT {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    @Qualifier(DataSourceConfig.GENERAL_DATA_SOURCE) private DataSource dataSource;

    @Autowired
    private TransactionTemplate transacao;

    private UUID ana;
    private UUID bruno;
    private UUID gestor;
    private String marcador;
    private final List<Lead> universo = new ArrayList<>();

    @BeforeEach
    void prepararBase() {
        marcador = "rls" + UUID.randomUUID().toString().substring(0, 8);
        ana = criarUsuario("Ana", PapelUsuario.ATENDENTE);
        bruno = criarUsuario("Bruno", PapelUsuario.ATENDENTE);
        gestor = criarUsuario("Gestora", PapelUsuario.GESTOR);

        universo.clear();
        universo.add(criarLead("da ana", ana, StatusBasicoLead.EM_ATENDIMENTO));
        universo.add(criarLead("do bruno", bruno, StatusBasicoLead.EM_ATENDIMENTO));
        universo.add(criarLead("do bruno finalizado", bruno, StatusBasicoLead.FINALIZADO));
        universo.add(criarLead("potencial", null, StatusBasicoLead.IA));
    }

    @AfterEach
    void limparContexto() {
        ApoioRls.sair();
    }

    @Test
    @DisplayName("atendente enxerga o proprio lead e os em IA, e nada do colega")
    void sqlCru_atendente_veApenasOSeuMaisOsEmIa() {
        List<UUID> visiveis = lidosComo(ana, PapelUsuario.ATENDENTE);

        assertThat(visiveis).containsExactlyInAnyOrder(id("da ana"), id("potencial"));
        assertThat(visiveis).doesNotContain(id("do bruno"), id("do bruno finalizado"));
    }

    @Test
    @DisplayName("o outro atendente enxerga a propria carteira, inclusive finalizados")
    void sqlCru_outroAtendente_veApenasAPropriaCarteira() {
        List<UUID> visiveis = lidosComo(bruno, PapelUsuario.ATENDENTE);

        assertThat(visiveis)
                .containsExactlyInAnyOrder(id("do bruno"), id("do bruno finalizado"), id("potencial"));
    }

    @Test
    @DisplayName("gestao enxerga a base inteira")
    void sqlCru_gestor_veTudo() {
        assertThat(lidosComo(gestor, PapelUsuario.GESTOR)).hasSize(universo.size());
    }

    @Test
    @DisplayName("contexto de servico enxerga tudo, sem se passar por um usuario")
    void sqlCru_contextoDeServico_veTudo() {
        List<UUID> visiveis = ContextoDeServico.buscarComo(
                "teste-de-rls", () -> emTransacao());

        assertThat(visiveis).hasSize(universo.size());
    }

    @Test
    @DisplayName("transacao sem contexto nao enxerga nada — falha fechado")
    void sqlCru_semContexto_naoVeNada() {
        ApoioRls.sair();

        assertThat(emTransacao()).isEmpty();
    }

    @Test
    @DisplayName("principal que nao e JWT tambem conta como sem contexto")
    void sqlCru_principalDesconhecido_naoVeNada() {
        ApoioRls.entrarComoPrincipalDesconhecido();

        assertThat(emTransacao()).isEmpty();
    }

    /**
     * A politica SQL e a regra de dominio precisam concordar. A {@link VisibilidadeLead} e a fonte
     * da verdade — a politica a persegue. No dia em que alguem acrescentar um modo de visibilidade,
     * o {@code switch} exaustivo quebra a compilacao da Specification e este teste quebra a
     * politica: as duas metades caem juntas, que e exatamente o ponto.
     */
    @Test
    @DisplayName("paridade: a politica devolve o mesmo conjunto que a regra de dominio")
    void paridade_politicaEDominio_concordamParaTodosOsPapeis() {
        record Caso(UUID usuarioId, PapelUsuario papel) {}

        List<Caso> casos = List.of(
                new Caso(ana, PapelUsuario.ATENDENTE),
                new Caso(bruno, PapelUsuario.ATENDENTE),
                new Caso(gestor, PapelUsuario.GESTOR),
                new Caso(gestor, PapelUsuario.SUBGESTOR),
                new Caso(gestor, PapelUsuario.ADMINISTRADOR));

        for (Caso caso : casos) {
            VisibilidadeLead regra =
                    VisibilidadeLead.de(new UsuarioAutenticado(caso.usuarioId(), caso.papel(), false));
            List<UUID> peloDominio =
                    universo.stream().filter(regra::permite).map(Lead::id).toList();

            List<UUID> pelaPolitica = lidosComo(caso.usuarioId(), caso.papel());

            assertThat(pelaPolitica)
                    .as("papel %s deve enxergar o mesmo pelo banco e pelo dominio", caso.papel())
                    .containsExactlyInAnyOrderElementsOf(peloDominio);
        }
    }

    /**
     * {@code SET LOCAL} morre com a transacao. Se alguem trocar por {@code SET}, o contexto
     * sobreviveria na conexao e a proxima transacao — de outro usuario — herdaria a visao da
     * anterior. Com PgBouncer em modo transaction, previsto para producao, essa seria a falha mais
     * grave imaginavel neste sistema: o atendente seguinte enxergaria a carteira do anterior.
     *
     * <p>O teste usa UMA conexao JDBC pegada a mao e roda duas transacoes nela. Nao depende de
     * sorte de pool nem de tamanho de pool: as duas transacoes acontecem, comprovadamente, no mesmo
     * backend do Postgres — {@code pg_backend_pid()} confirma.
     */
    @Test
    @DisplayName("o contexto de uma transacao nao vaza para a seguinte na mesma conexao")
    void contexto_naoVazaEntreTransacoesDaMesmaConexao() throws Exception {
        try (Connection conexao = dataSource.getConnection()) {
            conexao.setAutoCommit(false);

            int backendDaPrimeira;
            String donoOriginal;

            // Primeira transacao: exatamente o que o AplicadorDeContextoRls faz.
            try (Statement comando = conexao.createStatement()) {
                donoOriginal = valor(comando, "SELECT current_user");
                comando.execute("SET LOCAL ROLE synapse_app");
                comando.execute("SELECT set_config('app.papel', 'GESTOR', TRUE)");

                assertThat(valor(comando, "SELECT current_setting('app.papel', TRUE)"))
                        .isEqualTo("GESTOR");
                assertThat(valor(comando, contagemDeLeads()))
                        .as("o gestor enxerga a base inteira")
                        .isEqualTo(String.valueOf(universo.size()));

                backendDaPrimeira = Integer.parseInt(valor(comando, "SELECT pg_backend_pid()"));
            }
            conexao.commit();

            // Segunda transacao, mesma conexao fisica, nenhum contexto publicado.
            try (Statement comando = conexao.createStatement()) {
                assertThat(Integer.parseInt(valor(comando, "SELECT pg_backend_pid()")))
                        .as("as duas transacoes precisam rodar no mesmo backend para o teste valer")
                        .isEqualTo(backendDaPrimeira);

                assertThat(valor(comando, "SELECT current_user"))
                        .as("SET LOCAL ROLE sobreviveu ao commit")
                        .isEqualTo(donoOriginal);

                assertThat(valor(comando, "SELECT current_setting('app.papel', TRUE)"))
                        .as("o contexto do gestor sobreviveu ao commit — trocaram SET LOCAL por SET")
                        .isNullOrEmpty();

                // Assume a role de novo, agora sem publicar contexto nenhum.
                comando.execute("SET LOCAL ROLE synapse_app");
                assertThat(valor(comando, contagemDeLeads()))
                        .as("sem contexto, a politica precisa negar tudo")
                        .isEqualTo("0");
            }
            conexao.commit();
        }
    }

    @Test
    @DisplayName("participante convidado enxerga apenas o atendimento explicitamente compartilhado")
    void participante_convidado_veAtendimentoELeadSemAbrirCarteiraDoColega() {
        UUID lead = UUID.randomUUID();
        UUID atendimento = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO lead (id, nome, atendente_responsavel_id, status_basico) VALUES (?, ?, ?, 'EM_ATENDIMENTO'::status_basico_lead)",
                lead, marcador + " lead compartilhado", bruno);
        jdbc.update(
                "INSERT INTO atendimento (id, lead_id, atendente_id, status) VALUES (?, ?, ?, 'EM_ATENDIMENTO'::status_atendimento)",
                atendimento, lead, bruno);

        assertThat(lidosComo(ana, PapelUsuario.ATENDENTE)).doesNotContain(lead);
        ApoioRls.sair();
        jdbc.update("INSERT INTO atendimento_participante (atendimento_id, usuario_id) VALUES (?, ?)", atendimento, ana);

        ApoioRls.entrarComo(ana, PapelUsuario.ATENDENTE);
        List<UUID> atendimentosVisiveis = transacao.execute(status ->
                jdbc.queryForList("SELECT id FROM atendimento WHERE id = ?", UUID.class, atendimento));
        List<UUID> leadsVisiveis = transacao.execute(status ->
                jdbc.queryForList("SELECT id FROM lead WHERE id = ?", UUID.class, lead));

        assertThat(atendimentosVisiveis).containsExactly(atendimento);
        assertThat(leadsVisiveis).containsExactly(lead);
        List<UUID> carteiraDaAna = transacao.execute(status -> jdbc.queryForList(
                "SELECT id FROM atendimento WHERE atendente_id = ?", UUID.class, ana));
        assertThat(carteiraDaAna)
                .as("a lista operacional continua sendo a carteira do atendente")
                .isEmpty();
    }

    @Test
    @DisplayName("RLS impede que nao participante se insira no chat alheio")
    void chat_naoParticipante_naoConsegueEntrarNemLerMensagens() {
        UUID conversa = UUID.randomUUID();
        UUID mensagem = UUID.randomUUID();
        jdbc.update("INSERT INTO chat_interno_conversa(id, tipo) VALUES (?, 'DIRETA')", conversa);
        jdbc.update("INSERT INTO chat_interno_participante(conversa_id, usuario_id) VALUES (?, ?)", conversa, bruno);
        jdbc.update("INSERT INTO chat_interno_mensagem(id, conversa_id, remetente_id, tipo, conteudo) VALUES (?, ?, ?, 'TEXTO', 'segredo')",
                mensagem, conversa, bruno);

        ApoioRls.entrarComo(ana, PapelUsuario.ATENDENTE);
        assertThatThrownBy(() -> transacao.execute(status -> {
            jdbc.update("INSERT INTO chat_interno_participante(conversa_id, usuario_id) VALUES (?, ?)", conversa, ana);
            return null;
        })).isInstanceOf(DataAccessException.class);

        List<UUID> mensagensVisiveis = transacao.execute(status -> jdbc.queryForList(
                "SELECT id FROM chat_interno_mensagem WHERE conversa_id = ?", UUID.class, conversa));
        assertThat(mensagensVisiveis).isEmpty();
    }

    @Test
    @DisplayName("RLS impede que nao participante leia ou grave reacao do chat alheio")
    void chat_naoParticipante_naoLeNemGravaReacao() {
        UUID conversa = UUID.randomUUID();
        UUID mensagem = UUID.randomUUID();
        UUID reacao = UUID.randomUUID();
        jdbc.update("INSERT INTO chat_interno_conversa(id, tipo) VALUES (?, 'DIRETA')", conversa);
        jdbc.update(
                "INSERT INTO chat_interno_participante(conversa_id, usuario_id) VALUES (?, ?)",
                conversa,
                bruno);
        jdbc.update(
                "INSERT INTO chat_interno_mensagem(id, conversa_id, remetente_id, tipo, conteudo) VALUES (?, ?, ?, 'TEXTO', 'segredo')",
                mensagem,
                conversa,
                bruno);
        jdbc.update(
                "INSERT INTO chat_interno_mensagem_reacao(id, mensagem_id, usuario_id, emoji) VALUES (?, ?, ?, '👍')",
                reacao,
                mensagem,
                bruno);

        ApoioRls.entrarComo(gestor, PapelUsuario.GESTOR);
        List<UUID> reacoesVisiveis = transacao.execute(status -> jdbc.queryForList(
                "SELECT id FROM chat_interno_mensagem_reacao WHERE mensagem_id = ?",
                UUID.class,
                mensagem));
        assertThat(reacoesVisiveis)
                .as("gestor fora da conversa nao le reacao via papel amplo")
                .isEmpty();

        ApoioRls.entrarComo(ana, PapelUsuario.ATENDENTE);
        assertThatThrownBy(() -> transacao.execute(status -> {
                    jdbc.update(
                            "INSERT INTO chat_interno_mensagem_reacao(mensagem_id, usuario_id, emoji) VALUES (?, ?, '❤️')",
                            mensagem,
                            ana);
                    return null;
                }))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    @DisplayName("funcao de conversa direta recusa chamador fora do par")
    void criarConversaDireta_chamadorForaDoPar_ehRecusado() {
        ApoioRls.entrarComo(ana, PapelUsuario.ATENDENTE);

        assertThatThrownBy(() -> transacao.execute(status -> {
                    jdbc.queryForObject("SELECT app_criar_conversa_direta(?, ?)", UUID.class, bruno, gestor);
                    return null;
                }))
                .isInstanceOf(DataAccessException.class);
    }

    private String contagemDeLeads() {
        return "SELECT count(*) FROM lead WHERE nome LIKE '" + marcador + "%'";
    }

    private static String valor(Statement comando, String sql) throws Exception {
        try (ResultSet resultado = comando.executeQuery(sql)) {
            resultado.next();
            return resultado.getString(1);
        }
    }

    // --- apoio ---------------------------------------------------------------

    private List<UUID> lidosComo(UUID usuarioId, PapelUsuario papel) {
        ApoioRls.entrarComo(usuarioId, papel);
        return emTransacao();
    }

    /** Abre a transacao (onde o contexto RLS e publicado) e le. Tipo explicito para o inferidor. */
    private List<UUID> emTransacao() {
        return transacao.execute(status -> idsVisiveis());
    }

    /** SQL cru: nao passa pelo LeadRepositorio nem pela Specification. */
    private List<UUID> idsVisiveis() {
        return jdbc.queryForList(
                "SELECT id FROM lead WHERE nome LIKE ? ORDER BY nome", UUID.class, marcador + "%");
    }

    private UUID id(String sufixo) {
        return universo.stream()
                .filter(lead -> lead.nome().endsWith(sufixo))
                .findFirst()
                .orElseThrow()
                .id();
    }

    private UUID criarUsuario(String nome, PapelUsuario papel) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO usuario (id, nome, email, senha_hash, papel)"
                        + " VALUES (?, ?, ?, 'x', CAST(? AS papel_usuario))",
                id,
                nome,
                marcador + "-" + nome + "@dev.local",
                papel.name());
        return id;
    }

    private Lead criarLead(String sufixo, UUID dono, StatusBasicoLead status) {
        UUID id = UUID.randomUUID();
        String nome = marcador + " " + sufixo;
        jdbc.update(
                "INSERT INTO lead (id, nome, atendente_responsavel_id, status_basico)"
                        + " VALUES (?, ?, ?, CAST(? AS status_basico_lead))",
                id,
                nome,
                dono,
                status.name());
        return Lead.apenasParaVisibilidade(id, nome, status, dono);
    }
}
