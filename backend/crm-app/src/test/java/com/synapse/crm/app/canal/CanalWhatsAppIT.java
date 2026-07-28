package com.synapse.crm.app.canal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.synapse.crm.app.PostgresIT;
import com.synapse.crm.app.seguranca.ApoioRls;
import com.synapse.crm.atendimento.application.EnviarMensagemUseCase;
import com.synapse.crm.atendimento.domain.canal.ConteudoDeEnvio;
import com.synapse.crm.atendimento.domain.canal.ForaDaJanelaException;
import com.synapse.crm.atendimento.infrastructure.outbox.PublicadorDaOutbox;
import com.synapse.crm.atendimento.infrastructure.webhook.ProcessadorDeWebhookEntrada;
import com.synapse.crm.core.application.lead.LeadNoCaminhoDeMensagem;
import com.synapse.crm.sharedkernel.identidade.ContextoDeServico;
import com.synapse.crm.sharedkernel.identidade.PapelUsuario;
import com.synapse.crm.sharedkernel.persistencia.Pools;

/**
 * O canal ponta a ponta, com um provedor falso escolhido por configuracao.
 *
 * <p>{@code provedor=fake} e a prova de portabilidade: nenhuma linha de producao menciona o
 * {@link CanalFake}, e o fluxo inteiro — envio, outbox, retry, webhook, idempotencia — roda sobre ele.
 *
 * <p>Os agendamentos ficam praticamente desligados e os jobs sao chamados na mao. Um
 * {@code @Scheduled} de 1s disputando com as assercoes produziria o pior tipo de teste: verde na
 * maioria das vezes.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
@TestPropertySource(
        properties = {
            "synapse.canal.whatsapp.provedor=fake",
            "synapse.canal.outbox.intervalo-ms=3600000",
            "synapse.canal.webhook.intervalo-ms=3600000",
            "synapse.canal.outbox.maximo-de-tentativas=3",
            "synapse.canal.outbox.backoff-inicial=0s"
        })
class CanalWhatsAppIT extends PostgresIT {

    private static final String PREFIXO = "E05-";
    private static final String TELEFONE = "+5561988887777";

    @Autowired
    private EnviarMensagemUseCase enviar;

    @Autowired
    private PublicadorDaOutbox publicador;

    @Autowired
    private ProcessadorDeWebhookEntrada processador;

    @Autowired
    private LeadNoCaminhoDeMensagem leads;

    @Autowired
    private CanalFake canal;

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    @Qualifier(Pools.CHAT_TRANSACTION_MANAGER) private PlatformTransactionManager gerenteDoChat;

    private TransactionTemplate transacao;
    private UUID idAna;
    private UUID leadDaAna;

    @BeforeEach
    void preparar() {
        transacao = new TransactionTemplate(gerenteDoChat);
        canal.limpar();
        limpar();

        idAna = jdbc.queryForObject("SELECT id FROM usuario WHERE email = 'ana@dev.local'", UUID.class);
        leadDaAna = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO lead (id, nome, telefone, atendente_responsavel_id, status_basico,
                                  ultima_interacao_em)
                VALUES (?, ?, ?, ?, 'EM_ATENDIMENTO', now())
                """,
                leadDaAna,
                PREFIXO + "Cliente da Ana",
                TELEFONE,
                idAna);
    }

    @AfterEach
    void sair() {
        ApoioRls.sair();
    }

    @Nested
    @DisplayName("envio pela outbox")
    class Envio {

        @Test
        @DisplayName("a mensagem nasce PENDENTE e so vira ENVIADO quando o provedor aceita")
        void envio_pontaAPonta_terminaEnviado() {
            ApoioRls.entrarComo(idAna, PapelUsuario.ATENDENTE);
            UUID mensagemId = enviar.executar(leadDaAna, "bom dia").mensagem().id();

            // Antes do publisher: gravada, enfileirada, e NADA saiu.
            assertThat(statusDaMensagem(mensagemId)).isEqualTo("PENDENTE");
            assertThat(canal.enviados()).isEmpty();
            assertThat(pendentesNaOutbox()).isEqualTo(1);

            rodarPublisher();

            assertThat(canal.enviados()).hasSize(1);
            assertThat(statusDaMensagem(mensagemId)).isEqualTo("ENVIADO");
            assertThat(pendentesNaOutbox()).isZero();
        }

        /**
         * O cenario que motivou trazer a outbox da E07 para ca: o provedor cai no meio do envio.
         * Nenhuma mensagem se perde — ela espera e sai quando ele volta.
         */
        @Test
        @DisplayName("provedor derrubado no meio do envio: a mensagem sai quando ele volta")
        void envio_comProvedorForaDoAr_saiAoReligar() {
            ApoioRls.entrarComo(idAna, PapelUsuario.ATENDENTE);
            UUID mensagemId = enviar.executar(leadDaAna, "tem estoque?").mensagem().id();

            canal.derrubar("connection refused");
            rodarPublisher();

            // Continua na fila, ainda PENDENTE, com a tentativa contabilizada.
            assertThat(canal.enviados()).isEmpty();
            assertThat(statusDaMensagem(mensagemId)).isEqualTo("PENDENTE");
            assertThat(pendentesNaOutbox()).isEqualTo(1);
            assertThat(tentativasNaOutbox()).isEqualTo(1);

            canal.religar();
            rodarPublisher();

            assertThat(canal.enviados()).hasSize(1);
            assertThat(statusDaMensagem(mensagemId)).isEqualTo("ENVIADO");
        }

        /**
         * Breaker aberto e, para a outbox, indistinguivel de provedor fora do ar: recusa temporaria. O
         * que importa e o efeito — a mensagem <b>permanece</b> na fila, e nao vira erro na tela.
         */
        @Test
        @DisplayName("recusa temporaria mantem a mensagem na outbox, sem erro para o usuario")
        void envio_comBreakerAberto_mantemNaOutbox() {
            ApoioRls.entrarComo(idAna, PapelUsuario.ATENDENTE);
            canal.derrubar("circuit breaker aberto para fake");

            // O envio em si nao falha: o atendente nao ve excecao.
            UUID mensagemId = enviar.executar(leadDaAna, "ola").mensagem().id();
            rodarPublisher();

            assertThat(statusDaMensagem(mensagemId)).isEqualTo("PENDENTE");
            assertThat(pendentesNaOutbox()).isEqualTo(1);
            assertThat(esgotadasNaOutbox()).isZero();
        }

        /** Depois do limite a linha fica para inspecao — nunca some, e a mensagem vira FALHOU. */
        @Test
        @DisplayName("apos o limite de tentativas a linha esgota, com alarme, sem ser descartada")
        void envio_aposOLimite_esgotaSemDescartar() {
            ApoioRls.entrarComo(idAna, PapelUsuario.ATENDENTE);
            UUID mensagemId = enviar.executar(leadDaAna, "alguem ai?").mensagem().id();

            canal.derrubar("provedor fora do ar");
            rodarPublisher();
            rodarPublisher();
            rodarPublisher();

            assertThat(esgotadasNaOutbox()).isEqualTo(1);
            assertThat(statusDaMensagem(mensagemId)).isEqualTo("FALHOU");
            // A linha continua la, com o motivo: descartar em silencio e o que nao pode.
            assertThat(jdbc.queryForObject(
                            "SELECT ultimo_erro FROM outbox_evento WHERE esgotado_em IS NOT NULL",
                            String.class))
                    .contains("provedor fora do ar");
        }

        /** Recusa permanente nao merece varias tentativas: esgota na primeira. */
        @Test
        @DisplayName("recusa permanente esgota de imediato")
        void envio_comRecusaPermanente_esgotaNaPrimeira() {
            ApoioRls.entrarComo(idAna, PapelUsuario.ATENDENTE);
            enviar.executar(leadDaAna, "oi");

            canal.recusarDeVez("131026 numero invalido");
            rodarPublisher();

            assertThat(esgotadasNaOutbox()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("janela de 24h")
    class Janela {

        /**
         * A regra da Meta vive no adaptador, mas o efeito e do dominio: texto livre fora da janela
         * falha <b>antes</b> de chegar ao provedor. Nada e gravado, nada e enfileirado.
         */
        @Test
        @DisplayName("texto livre fora da janela falha antes de chegar ao provedor")
        void textoLivre_foraDaJanela_falhaAntesDoProvedor() {
            ApoioRls.entrarComo(idAna, PapelUsuario.ATENDENTE);
            canal.fecharJanela();

            assertThatThrownBy(() -> enviar.executar(leadDaAna, "voce ainda tem interesse?"))
                    .isInstanceOf(ForaDaJanelaException.class)
                    .hasMessageContaining("template");

            assertThat(canal.enviados()).isEmpty();
            assertThat(pendentesNaOutbox()).isZero();
            assertThat(mensagensDoLead()).isZero();
        }

        /** Template passa fora da janela — e o caminho de toda reativacao e campanha. */
        @Test
        @DisplayName("template passa fora da janela")
        void template_foraDaJanela_eAceito() {
            ApoioRls.entrarComo(idAna, PapelUsuario.ATENDENTE);
            canal.fecharJanela();

            enviar.executar(leadDaAna, ConteudoDeEnvio.MensagemTemplate.de("reativacao", "pt_BR", "Ana"));
            rodarPublisher();

            assertThat(canal.enviados()).hasSize(1);
            assertThat(canal.enviados().get(0).conteudo())
                    .isInstanceOf(ConteudoDeEnvio.MensagemTemplate.class);
        }
    }

    @Nested
    @DisplayName("webhook de entrada")
    class Webhook {

        @Test
        @DisplayName("mensagem recebida vira atendimento e mensagem na conversa")
        void webhook_mensagemNova_criaAtendimento() {
            assertThat(postarWebhook(payload("ext-1", "bom dia"), CanalFake.ASSINATURA_VALIDA)
                            .getStatusCode())
                    .isEqualTo(HttpStatus.OK);

            rodarProcessador();

            assertThat(mensagensDoLead()).isEqualTo(1);
            assertThat(jdbc.queryForObject(
                            "SELECT count(*) FROM atendimento WHERE lead_id = ?",
                            Integer.class,
                            leadDaAna))
                    .isEqualTo(1);
        }

        /**
         * Provedores reentregam. Sem a chave de idempotencia, cada reentrega viraria uma mensagem
         * duplicada na conversa do atendente — e ninguem repara ate o cliente reclamar.
         */
        @Test
        @DisplayName("reentrega do mesmo evento nao duplica a mensagem")
        void webhook_reentregue_naoDuplica() {
            String payload = payload("ext-repetido", "esta ai?");

            postarWebhook(payload, CanalFake.ASSINATURA_VALIDA);
            postarWebhook(payload, CanalFake.ASSINATURA_VALIDA);
            postarWebhook(payload, CanalFake.ASSINATURA_VALIDA);
            rodarProcessador();

            assertThat(linhasNoWebhookEntrada()).isEqualTo(1);
            assertThat(mensagensDoLead()).isEqualTo(1);
        }

        /** A rota e publica: sem esta checagem, injetar mensagem falsa e um curl. */
        @Test
        @DisplayName("assinatura invalida e recusada sem gravar nada")
        void webhook_assinaturaInvalida_recusa() {
            ResponseEntity<String> resposta =
                    postarWebhook(payload("ext-forjado", "sou o banco"), "sha256=errada");

            assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(linhasNoWebhookEntrada()).isZero();
        }

        @Test
        @DisplayName("sem assinatura tambem e recusado")
        void webhook_semAssinatura_recusa() {
            assertThat(postarWebhook(payload("ext-sem", "oi"), null).getStatusCode())
                    .isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(linhasNoWebhookEntrada()).isZero();
        }
    }

    @Nested
    @DisplayName("credencial e transacao")
    class Infra {

        /**
         * Troca de numero: a credencial antiga e desativada, nao apagada, e o atendimento continua
         * apontando para ela. O historico nao pode mudar quando a empresa muda de numero.
         */
        @Test
        @DisplayName("trocar o numero preserva o historico e a mensagem em transito")
        void trocaDeNumero_preservaHistoricoEMensagemEmTransito() {
            UUID canalId = criarCanal();
            UUID antiga = criarCredencial(canalId, "+5561900000001", true);
            UUID atendimentoId = criarAtendimentoCom(antiga);

            ApoioRls.entrarComo(idAna, PapelUsuario.ATENDENTE);
            UUID mensagemId = enviar.executar(leadDaAna, "mensagem em transito").mensagem().id();

            // Fluxo de troca: desativa a antiga com vigente_ate, ativa a nova.
            jdbc.update(
                    "UPDATE canal_credencial SET ativo = FALSE, vigente_ate = now() WHERE id = ?", antiga);
            UUID nova = criarCredencial(canalId, "+5561900000002", true);

            rodarPublisher();

            // A mensagem que ja estava na fila saiu.
            assertThat(statusDaMensagem(mensagemId)).isEqualTo("ENVIADO");
            // A antiga continua no banco e o atendimento continua apontando para ela.
            assertThat(jdbc.queryForObject(
                            "SELECT canal_credencial_id FROM atendimento WHERE id = ?",
                            UUID.class,
                            atendimentoId))
                    .isEqualTo(antiga)
                    .isNotEqualTo(nova);
        }

        /**
         * A brecha da E04, agora fechada. Fora de transacao a conexao vem crua, sem {@code SET LOCAL
         * ROLE}, e o PostgreSQL ignora RLS para o dono das tabelas — a consulta devolveria os leads de
         * todo mundo, sem erro nenhum.
         */
        @Test
        @DisplayName("adaptador JDBC chamado fora de transacao lanca")
        void adaptadorJdbc_foraDeTransacao_lanca() {
            ApoioRls.entrarComo(idAna, PapelUsuario.ATENDENTE);

            // A assercao e pela mensagem, e nao pelo tipo: o adaptador e um @Repository,
            // entao o proxy de traducao de excecao do Spring embrulha o que sai dele. O
            // que importa provar e que a chamada NAO passou.
            assertThatThrownBy(() -> leads.alcancavel(leadDaAna))
                    .hasMessageContaining("exige transacao ativa")
                    .hasMessageContaining("RLS");

            // O par positivo: dentro de transacao, funciona. Sem ele, este teste passaria
            // ate com o adaptador completamente quebrado.
            Boolean alcancouDentroDaTransacao =
                    transacao.execute(status -> leads.alcancavel(leadDaAna));
            assertThat(alcancouDentroDaTransacao).isTrue();
        }
    }

    // --- apoio ----------------------------------------------------------------

    private void rodarPublisher() {
        ContextoDeServico.executarComo("teste", publicador::rodada);
    }

    private void rodarProcessador() {
        ContextoDeServico.executarComo("teste", processador::rodada);
    }

    private static String payload(String id, String texto) {
        return "{\"id\":\"" + id + "\",\"de\":\"" + TELEFONE + "\",\"nome\":\"Cliente\",\"texto\":\""
                + texto + "\"}";
    }

    private ResponseEntity<String> postarWebhook(String payload, String assinatura) {
        HttpHeaders cabecalhos = new HttpHeaders();
        cabecalhos.setContentType(MediaType.APPLICATION_JSON);
        if (assinatura != null) {
            cabecalhos.set("X-Hub-Signature-256", assinatura);
        }
        return http.postForEntity("/webhook/canal", new HttpEntity<>(payload, cabecalhos), String.class);
    }

    private String statusDaMensagem(UUID mensagemId) {
        return jdbc.queryForObject(
                "SELECT status_entrega::text FROM mensagem WHERE id = ?", String.class, mensagemId);
    }

    private int pendentesNaOutbox() {
        return jdbc.queryForObject(
                "SELECT count(*) FROM outbox_evento WHERE publicado_em IS NULL AND esgotado_em IS NULL",
                Integer.class);
    }

    private int esgotadasNaOutbox() {
        return jdbc.queryForObject(
                "SELECT count(*) FROM outbox_evento WHERE esgotado_em IS NOT NULL", Integer.class);
    }

    private int tentativasNaOutbox() {
        return jdbc.queryForObject(
                "SELECT coalesce(max(tentativas), 0) FROM outbox_evento", Integer.class);
    }

    private int linhasNoWebhookEntrada() {
        return jdbc.queryForObject("SELECT count(*) FROM webhook_entrada", Integer.class);
    }

    private int mensagensDoLead() {
        return jdbc.queryForObject(
                """
                SELECT count(*) FROM mensagem m
                  JOIN atendimento a ON a.id = m.atendimento_id
                 WHERE a.lead_id = ?
                """,
                Integer.class,
                leadDaAna);
    }

    private UUID criarCanal() {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO canal (id, nome, tipo) VALUES (?, ?, 'WHATSAPP')", id, PREFIXO + id);
        return id;
    }

    private UUID criarCredencial(UUID canalId, String numero, boolean ativo) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO canal_credencial (id, canal_id, numero, token_ref, ativo)"
                        + " VALUES (?, ?, ?, 'secret://dev/token', ?)",
                id,
                canalId,
                numero,
                ativo);
        return id;
    }

    private UUID criarAtendimentoCom(UUID credencialId) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO atendimento (id, lead_id, canal_credencial_id, atendente_id, status)"
                        + " VALUES (?, ?, ?, ?, 'EM_ATENDIMENTO')",
                id,
                leadDaAna,
                credencialId,
                idAna);
        return id;
    }

    private void limpar() {
        jdbc.update("DELETE FROM outbox_evento");
        jdbc.update("DELETE FROM webhook_entrada");
        for (String tabela : List.of("evento_timeline", "audit_log")) {
            jdbc.update(
                    "DELETE FROM " + tabela + " WHERE lead_id IN (SELECT id FROM lead WHERE nome LIKE ?)",
                    PREFIXO + "%");
        }
        jdbc.update(
                """
                DELETE FROM mensagem WHERE atendimento_id IN (
                    SELECT a.id FROM atendimento a JOIN lead l ON l.id = a.lead_id
                     WHERE l.nome LIKE ? OR l.telefone = ?)
                """,
                PREFIXO + "%",
                TELEFONE);
        jdbc.update(
                """
                DELETE FROM atendimento WHERE lead_id IN (
                    SELECT id FROM lead WHERE nome LIKE ? OR telefone = ?)
                """,
                PREFIXO + "%",
                TELEFONE);
        jdbc.update("DELETE FROM lead WHERE nome LIKE ? OR telefone = ?", PREFIXO + "%", TELEFONE);
        jdbc.update("DELETE FROM canal_credencial WHERE token_ref = 'secret://dev/token'");
        jdbc.update("DELETE FROM canal WHERE nome LIKE ?", PREFIXO + "%");
    }
}
