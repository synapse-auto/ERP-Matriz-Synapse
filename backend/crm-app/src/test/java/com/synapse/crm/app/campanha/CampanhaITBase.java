package com.synapse.crm.app.campanha;

import static org.awaitility.Awaitility.await;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.awaitility.core.ConditionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import com.synapse.crm.app.PostgresIT;
import com.synapse.crm.app.canal.CanalFake;
import com.synapse.crm.app.seguranca.ApoioRls;
import com.synapse.crm.atendimento.domain.canal.TemplateDoCanal;
import com.synapse.crm.atendimento.infrastructure.outbox.PublicadorDaOutbox;
import com.synapse.crm.automacaoconfig.infrastructure.ChavesDeCacheConfiguracaoAutomacao;
import com.synapse.crm.campanhas.application.AplicarEntregaDeCampanhaUseCase;
import com.synapse.crm.campanhas.application.ConfiguracaoDeCampanhasUseCases;
import com.synapse.crm.campanhas.application.ConsultasDeCampanhaUseCases;
import com.synapse.crm.campanhas.application.ControleDaCampanhaUseCases;
import com.synapse.crm.campanhas.application.CriarCampanhaUseCase;
import com.synapse.crm.campanhas.application.EnviarTesteDeCampanhaUseCase;
import com.synapse.crm.campanhas.application.IniciarCampanhaUseCase;
import com.synapse.crm.campanhas.application.OptOutUseCases;
import com.synapse.crm.campanhas.application.PedidoDeCampanha;
import com.synapse.crm.campanhas.application.PreverPublicoUseCase;
import com.synapse.crm.campanhas.application.ProcessarDestinatarioDeCampanhaUseCase;
import com.synapse.crm.campanhas.application.TemplatesDoCanal;
import com.synapse.crm.campanhas.domain.Campanha;
import com.synapse.crm.campanhas.domain.CampoDoLead;
import com.synapse.crm.campanhas.domain.DiasDaSemana;
import com.synapse.crm.campanhas.domain.FiltroDePublico;
import com.synapse.crm.campanhas.domain.JanelaDeEnvio;
import com.synapse.crm.campanhas.domain.MapeamentoDeVariaveis;
import com.synapse.crm.campanhas.infrastructure.AgendadorDeCampanhas;
import com.synapse.crm.sharedkernel.identidade.PapelUsuario;

/**
 * Base dos testes de integracao de campanhas (E220): Postgres real, o {@link CanalFake} como provedor e os
 * agendadores desligados. Os ciclos e a outbox sao chamados na mao, pelos mesmos pontos de entrada que o
 * agendador usa.
 *
 * <p>O publico de toda campanha de teste e filtrado pelo prefixo {@code E220-} do nome: o banco de teste ja
 * traz leads do seed, e uma campanha de teste nunca pode alcanca-los.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
// Mesmo conjunto de propriedades de outros ITs (NovoContatoIT, TemplatesWhatsAppIT): compartilha o contexto Spring
// em cache em vez de abrir mais um, com mais pools de conexao. Os agendadores ja nascem desligados no PostgresIT.
@TestPropertySource(properties = "synapse.canal.whatsapp.provedor=fake")
abstract class CampanhaITBase extends PostgresIT {

    protected static final String PREFIXO = "E220-";
    protected static final String TEMPLATE = "promo_natal";
    protected static final String IDIOMA = "pt_BR";
    private static final AtomicInteger SEQUENCIA = new AtomicInteger(10_000_000);

    @Autowired
    protected JdbcTemplate jdbc;

    @Autowired
    protected StringRedisTemplate redis;

    @Autowired
    protected CanalFake canal;

    @Autowired
    protected TemplatesDoCanal templates;

    @Autowired
    protected CriarCampanhaUseCase criar;

    @Autowired
    protected IniciarCampanhaUseCase iniciar;

    @Autowired
    protected ControleDaCampanhaUseCases controle;

    @Autowired
    protected PreverPublicoUseCase prever;

    @Autowired
    protected ConsultasDeCampanhaUseCases consultas;

    @Autowired
    protected ProcessarDestinatarioDeCampanhaUseCase processador;

    @Autowired
    protected AplicarEntregaDeCampanhaUseCase entregas;

    @Autowired
    protected OptOutUseCases optOuts;

    @Autowired
    protected EnviarTesteDeCampanhaUseCase teste;

    @Autowired
    protected ConfiguracaoDeCampanhasUseCases configuracao;

    @Autowired
    protected AgendadorDeCampanhas agendador;

    @Autowired
    protected PublicadorDaOutbox outbox;

    protected UUID adminId;

    @BeforeEach
    void prepararCampanhas() {
        limparCampanhas();
        restaurarConfiguracao();
        jdbc.update("UPDATE feature_flag SET habilitado = TRUE WHERE chave = 'campanhas'");
        canal.limpar();
        registrarTemplateAprovado(TEMPLATE);
        adminId = jdbc.queryForObject("SELECT id FROM usuario WHERE email = 'admin@dev.local'", UUID.class);
        ApoioRls.entrarComo(adminId, PapelUsuario.ADMINISTRADOR);
    }

    @AfterEach
    void encerrarCampanhas() {
        ApoioRls.sair();
        limparCampanhas();
        restaurarConfiguracao();
        jdbc.update("UPDATE feature_flag SET habilitado = FALSE WHERE chave = 'campanhas'");
    }

    // --- cenario --------------------------------------------------------------------------------------

    protected UUID criarLead(String nome, String telefone) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO lead (id, nome, telefone, status_basico) VALUES (?, ?, ?, 'IA')", id, nome, telefone);
        return id;
    }

    /** Leads elegiveis: telefone canonico valido e nome utilizavel, sem conversa nem opt-out. */
    protected List<UUID> criarLeadsElegiveis(int quantidade) {
        List<UUID> ids = new ArrayList<>();
        for (int i = 0; i < quantidade; i++) {
            int numero = SEQUENCIA.incrementAndGet();
            ids.add(criarLead(PREFIXO + "Maria Teste" + numero, "55619" + numero));
        }
        return ids;
    }

    protected void registrarTemplateAprovado(String nome) {
        canal.registrarTemplate(new TemplateDoCanal(
                "id-" + nome,
                nome,
                IDIOMA,
                TemplateDoCanal.Categoria.MARKETING,
                TemplateDoCanal.Status.APROVADO,
                "Ola {{1}}, temos novidades!",
                1));
        templates.descartarCache();
    }

    /** Janela aberta o dia inteiro, todos os dias: o teste nao depende da hora em que roda. */
    protected static JanelaDeEnvio janelaSempreAberta() {
        return new JanelaDeEnvio(
                LocalTime.MIN, LocalTime.of(23, 59, 59), DiasDaSemana.de(EnumSet.allOf(DayOfWeek.class)));
    }

    protected PedidoDeCampanha pedido(String nome, int limiteDiario) {
        return new PedidoDeCampanha(
                nome,
                TEMPLATE,
                IDIOMA,
                new MapeamentoDeVariaveis(
                        List.of(new MapeamentoDeVariaveis.Variavel(1, CampoDoLead.PRIMEIRO_NOME, "cliente"))),
                new FiltroDePublico(List.of(), null, null, null, null, false, PREFIXO),
                limiteDiario,
                janelaSempreAberta(),
                600,
                null,
                null);
    }

    protected Campanha criarEIniciar(String nome, int limiteDiario) {
        Campanha rascunho = criar.executar(pedido(nome, limiteDiario));
        return iniciar.executar(rascunho.id());
    }

    /** Um ciclo do motor pelo ponto de entrada do agendador, com o ritmo liberado (ultimo ciclo ha 2 minutos). */
    protected void rodarCiclo() {
        jdbc.update("UPDATE campanha_template SET ultimo_ciclo_em = now() - interval '2 minutes'");
        agendador.executarCiclo();
    }

    // --- consultas ------------------------------------------------------------------------------------

    protected int contar(String sql, Object... argumentos) {
        Integer total = jdbc.queryForObject(sql, Integer.class, argumentos);
        return total == null ? 0 : total;
    }

    protected int destinatariosCom(UUID campanhaId, String status) {
        return contar(
                "SELECT count(*) FROM campanha_template_destinatario WHERE campanha_id = ? AND status = ?",
                campanhaId,
                status);
    }

    protected String statusDaCampanha(UUID campanhaId) {
        return jdbc.queryForObject("SELECT status FROM campanha_template WHERE id = ?", String.class, campanhaId);
    }

    protected int saidasNaOutbox() {
        return contar("SELECT count(*) FROM outbox_evento WHERE tipo = 'canal.mensagem.enviar'");
    }

    protected ConditionFactory esperar() {
        return await().atMost(Duration.ofSeconds(15)).pollInterval(Duration.ofMillis(100));
    }

    // --- limpeza --------------------------------------------------------------------------------------

    private void limparCampanhas() {
        jdbc.update("DELETE FROM campanha_template");
        jdbc.update("DELETE FROM campanha_envio_dia");
        jdbc.update("DELETE FROM contato_optout");
        jdbc.update("DELETE FROM outbox_evento");
        String doTeste = "(SELECT id FROM lead WHERE nome LIKE '" + PREFIXO + "%')";
        jdbc.update("DELETE FROM envio_proativo_reserva WHERE lead_id IN " + doTeste);
        jdbc.update("DELETE FROM mensagem_origem_automacao WHERE lead_id IN " + doTeste);
        for (String tabela : List.of("evento_timeline", "audit_log")) {
            jdbc.update("DELETE FROM " + tabela + " WHERE lead_id IN " + doTeste);
        }
        String atendimentosDoTeste = "(SELECT id FROM atendimento WHERE lead_id IN " + doTeste + ")";
        jdbc.update("DELETE FROM mensagem_id_externo WHERE atendimento_id IN " + atendimentosDoTeste);
        jdbc.update("DELETE FROM mensagem WHERE atendimento_id IN " + atendimentosDoTeste);
        jdbc.update("DELETE FROM atendimento WHERE lead_id IN " + doTeste);
        jdbc.update("DELETE FROM lead WHERE nome LIKE '" + PREFIXO + "%'");
    }

    /**
     * Parametros da politica proativa (E219) vivem em {@code configuracao_automacao} com cache no Redis: alterar
     * so no banco deixaria o teste lendo o valor antigo, entao a chave do cache sai junto.
     */
    protected void definirConfiguracaoDaAutomacao(String chave, String valor) {
        jdbc.update("UPDATE configuracao_automacao SET valor = ? WHERE chave = ?", valor, chave);
        redis.delete(ChavesDeCacheConfiguracaoAutomacao.porChave(chave));
    }

    private void restaurarConfiguracao() {
        jdbc.update("UPDATE configuracao_automacao SET valor = 'true' WHERE chave = 'campanhas.envio_habilitado'");
        jdbc.update("UPDATE configuracao_automacao SET valor = '200' WHERE chave = 'campanhas.teto_diario_instancia'");
        jdbc.update("UPDATE configuracao_automacao SET valor = '100' WHERE chave = 'campanhas.limite_diario_padrao'");
        jdbc.update("UPDATE configuracao_automacao SET valor = '0' WHERE chave = 'campanhas.limite_meta_informado'");
        jdbc.update("UPDATE configuracao_automacao SET valor = '20' WHERE chave = 'campanhas.pausa.limiar_falha_pct'");
        jdbc.update("UPDATE configuracao_automacao SET valor = '50' WHERE chave = 'campanhas.pausa.janela_envios'");
        jdbc.update("UPDATE configuracao_automacao SET valor = '20' WHERE chave = 'campanhas.pausa.minimo_amostra'");
        jdbc.update("UPDATE configuracao_automacao SET valor = '30' WHERE chave = 'campanhas.conferencia_apos_minutos'");
        definirConfiguracaoDaAutomacao("automacao_proativa.cooldown_horas", "0");
        definirConfiguracaoDaAutomacao("automacao_proativa.habilitada", "true");
        definirConfiguracaoDaAutomacao("automacao_proativa.campanha.habilitada", "true");
    }
}
