package com.synapse.crm.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

import com.synapse.crm.app.config.DataSourceConfig;
import com.synapse.crm.atendimento.infrastructure.particao.ManutencaoParticaoMensagem;
import com.synapse.crm.atendimento.infrastructure.particao.ParticaoMensagemAusenteException;

/**
 * Prova que as migrations sobem um schema correto a partir de um banco limpo.
 *
 * <p>O container do Testcontainers nasce vazio, entao o contexto subir ja prova que o Flyway rodou
 * V1..V10 do zero. O resto verifica o que uma migration errada quebraria em silencio: um ENUM
 * faltando, um indice que ninguem percebeu que sumiu, ou a regra de credencial ativa deixando de
 * ser garantida pelo banco.
 */
@SpringBootTest
class SchemaMigracoesIT extends PostgresIT {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private Flyway flyway;

    @Autowired
    private ManutencaoParticaoMensagem particoes;

    @Autowired
    @Qualifier(DataSourceConfig.GENERAL_DATA_SOURCE) private DataSource poolGeral;

    @Nested
    @DisplayName("estrutura criada pelas migrations")
    class Estrutura {

        /**
         * Lista explicita, e nao COUNT(*): contar so avisa que o numero mudou, enquanto a lista diz
         * qual tabela sumiu — e falha quando alguem remove uma e acrescenta outra.
         */
        @Test
        @DisplayName("todas as tabelas do modelo existem")
        void tabelas_bancoMigrado_existemTodas() {
            List<String> esperadas = List.of(
                    "arquivo_banco",
                    "atendimento",
                    "atendimento_leitura",
                    "audit_log",
                    "avaliacao",
                    "campanha",
                    "campanha_mensagem",
                    "campanha_mensagem_metrica",
                    "campo_customizado",
                    "canal",
                    "canal_credencial",
                    "chat_interno_conversa",
                    "chat_interno_mensagem",
                    "chat_interno_participante",
                    "atendimento_participante",
                    "pedido_entrada_atendimento",
                    "configuracao_automacao",
                    "configuracao_resumo_ia",
                    "disponibilidade_atendente_ia",
                    "etapa_atendimento",
                    "evento_timeline",
                    "feature_flag",
                    "feedback_usuario",
                    "filtro_modular",
                    "horario_trabalho",
                    "lead",
                    "lead_tag",
                    "lembrete",
                    "mensagem",
                    "mensagem_automacao_idempotencia",
                    "mensagem_recebida_idempotencia",
                    "comando_automacao_idempotencia",
                    // Rede de seguranca: recebe o que chegar fora da janela de particoes.
                    "mensagem_default",
                    "mensagem_festiva",
                    "mensagem_programada",
                    "mensagem_rapida",
                    "outbox_evento",
                    "preferencia_usuario",
                    "regra_fidelizacao",
                    "refresh_token",
                    "regra_follow_up",
                    "rotina_disponibilidade",
                    "rotina_disponibilidade_atendente",
                    "status_automacao_telemetria",
                    "tag",
                    "usuario",
                    // Fila duravel de entrada (E05): o payload cru do provedor, com a
                    // chave de idempotencia que impede reentrega virar mensagem duplicada.
                    "webhook_entrada");

            List<String> existentes = jdbc.queryForList(
                    """
                    SELECT c.relname
                      FROM pg_class c
                      JOIN pg_namespace n ON n.oid = c.relnamespace
                     WHERE n.nspname = 'public'
                       AND c.relkind IN ('r', 'p')
                       AND c.relname <> 'flyway_schema_history'
                       AND c.relname NOT LIKE 'mensagem\\_2%'
                    """,
                    String.class);

            assertThat(existentes).containsExactlyInAnyOrderElementsOf(esperadas);
        }

        @Test
        @DisplayName("todos os tipos enumerados existem")
        void enums_bancoMigrado_existemTodos() {
            List<String> esperados = List.of(
                    "contexto_filtro",
                    "dia_semana",
                    "gatilho_resumo",
                    "origem_evento",
                    "papel_usuario",
                    "remetente_tipo",
                    "resultado_etapa",
                    "status_atendimento",
                    "status_basico_lead",
                    "status_campanha",
                    "status_entrega",
                    "status_lembrete",
                    "status_msg_prog",
                    "status_presenca",
                    "tipo_conversa_chat",
                    "tipo_feedback",
                    "tipo_mensagem",
                    "tipo_rotina");

            List<String> existentes = jdbc.queryForList(
                    """
                    SELECT t.typname
                      FROM pg_type t
                      JOIN pg_namespace n ON n.oid = t.typnamespace
                     WHERE n.nspname = 'public' AND t.typtype = 'e'
                    """,
                    String.class);

            assertThat(existentes).containsExactlyInAnyOrderElementsOf(esperados);
        }

        @Test
        @DisplayName("os indices criticos existem, inclusive parciais, GIN e BRIN")
        void indices_bancoMigrado_existemOsCriticos() {
            List<String> existentes = jdbc.queryForList(
                    "SELECT indexname FROM pg_indexes WHERE schemaname = 'public'", String.class);

            assertThat(existentes)
                    .contains(
                            "idx_lead_atendente", // isolamento de agenda (RN-CRM-01)
                            "idx_canal_credencial_ativa", // unico parcial: regra de negocio
                            "idx_outbox_pendente", // parcial: publisher da outbox
                            "idx_lead_nome_trgm", // GIN trigram: busca por nome
                            "idx_filtro_criterios", // GIN jsonb: filtros modulares
                            "idx_audit_criado_em", // BRIN: auditoria append-only
                            "idx_lead_status_basico",
                            "idx_msg_prog_envio",
                            "idx_etapa_ordem",
                            "idx_etapa_unica_ganha",
                            "idx_evento_timeline_tipo_criado_em",
                            "idx_feedback_criacao",
                            "idx_feedback_tipo_criacao",
                            "idx_msg_rapida_atendente_chave",
                            "idx_mensagem_atendimento",
                            "uq_avaliacao_atendimento",
                            "idx_avaliacao_criado_em");
            assertThat(existentes)
                    .contains(
                            "uq_atendimento_participante_ativo",
                            "uq_pedido_entrada_pendente",
                            "idx_pedido_entrada_atendimento",
                            "idx_chat_interno_msg_conversa");
        }

        @Test
        @DisplayName("o Flyway migra pelo pool geral, nao pela reserva do chat")
        void flyway_contextoIniciado_usaOPoolGeral() {
            assertThat(flyway.getConfiguration().getDataSource()).isSameAs(poolGeral);
        }

        @Test
        @DisplayName("o seed de desenvolvimento fica fora das locations no perfil padrao")
        void flyway_perfilPadrao_naoEnxergaOSeed() {
            assertThat(flyway.getConfiguration().getLocations())
                    .noneMatch(local -> local.getDescriptor().contains("db/seed"));
        }
    }

    @Nested
    @DisplayName("regras de negocio garantidas pelo banco")
    class RegrasNoBanco {

        /**
         * O indice unico parcial existe para que a regra valha mesmo se o codigo errar. Se este
         * teste passar a falhar, alguem trocou o indice por uma validacao na aplicacao — que nao
         * protege contra dois processos concorrentes.
         */
        @Test
        @DisplayName("duas credenciais ativas no mesmo canal sao rejeitadas")
        void canalCredencial_segundaAtivaNoMesmoCanal_ehRejeitada() {
            UUID canalId = criarCanal();
            try {
                jdbc.update(
                        "INSERT INTO canal_credencial"
                                + " (canal_id, numero, identificador_externo, ativo)"
                                + " VALUES (?, '+5561900000001', '900000000000001', TRUE)",
                        canalId);

                assertThatThrownBy(() -> jdbc.update(
                                "INSERT INTO canal_credencial"
                                        + " (canal_id, numero, identificador_externo, ativo)"
                                        + " VALUES (?, '+5561900000002', '900000000000002', TRUE)",
                                canalId))
                        .isInstanceOf(DuplicateKeyException.class);
            } finally {
                excluirCanal(canalId);
            }
        }

        @Test
        @DisplayName("a credencial anterior convive com a nova quando desativada")
        void canalCredencial_anteriorDesativada_permiteNovaAtiva() {
            UUID canalId = criarCanal();
            try {
                jdbc.update(
                        "INSERT INTO canal_credencial (canal_id, numero, ativo, vigente_ate)"
                                + " VALUES (?, '+5561900000003', FALSE, now())",
                        canalId);
                jdbc.update(
                        "INSERT INTO canal_credencial"
                                + " (canal_id, numero, identificador_externo, ativo)"
                                + " VALUES (?, '+5561900000004', '900000000000004', TRUE)",
                        canalId);

                Integer total = jdbc.queryForObject(
                        "SELECT count(*) FROM canal_credencial WHERE canal_id = ?",
                        Integer.class,
                        canalId);

                // A antiga continua no banco: e ela que o historico referencia.
                assertThat(total).isEqualTo(2);
            } finally {
                excluirCanal(canalId);
            }
        }

        @Test
        @DisplayName("o banco rejeita uma segunda etapa GANHO")
        void etapa_segundaGanha_ehRejeitadaPeloBanco() {
            UUID primeira = UUID.randomUUID();
            UUID segunda = UUID.randomUUID();
            boolean criouPrimeira = jdbc.queryForObject(
                            "SELECT count(*) = 0 FROM etapa_atendimento WHERE resultado = 'GANHO'",
                            Boolean.class)
                    .booleanValue();
            try {
                if (criouPrimeira) {
                    jdbc.update(
                            "INSERT INTO etapa_atendimento (id, nome, ordem, resultado) VALUES (?, 'Ganha A', 120, 'GANHO')",
                            primeira);
                }
                assertThatThrownBy(() -> jdbc.update(
                                "INSERT INTO etapa_atendimento (id, nome, ordem, resultado) VALUES (?, 'Ganha B', 121, 'GANHO')",
                                segunda))
                        .isInstanceOf(DuplicateKeyException.class);
            } finally {
                jdbc.update("DELETE FROM etapa_atendimento WHERE id = ?", segunda);
                if (criouPrimeira) {
                    jdbc.update("DELETE FROM etapa_atendimento WHERE id = ?", primeira);
                }
            }
        }

        @Test
        @DisplayName("o banco rejeita uma segunda avaliacao no mesmo atendimento")
        void avaliacao_segundaNoMesmoAtendimento_ehRejeitadaPeloBanco() {
            UUID leadId = UUID.randomUUID();
            UUID atendimentoId = UUID.randomUUID();
            UUID atendenteId = jdbc.queryForObject(
                    "SELECT id FROM usuario WHERE papel = 'ATENDENTE' ORDER BY email LIMIT 1", UUID.class);
            jdbc.update("INSERT INTO lead (id, nome) VALUES (?, 'Lead avaliacao unica')", leadId);
            jdbc.update("INSERT INTO atendimento (id, lead_id, atendente_id) VALUES (?, ?, ?)",
                    atendimentoId, leadId, atendenteId);
            try {
                jdbc.update(
                        "INSERT INTO avaliacao (atendimento_id, atendente_id, nota) VALUES (?, ?, 5)",
                        atendimentoId,
                        atendenteId);
                assertThatThrownBy(() -> jdbc.update(
                                "INSERT INTO avaliacao (atendimento_id, atendente_id, nota) VALUES (?, ?, 1)",
                                atendimentoId,
                                atendenteId))
                        .isInstanceOf(DuplicateKeyException.class);
            } finally {
                jdbc.update("DELETE FROM avaliacao WHERE atendimento_id = ?", atendimentoId);
                jdbc.update("DELETE FROM atendimento WHERE id = ?", atendimentoId);
                jdbc.update("DELETE FROM lead WHERE id = ?", leadId);
            }
        }

        private UUID criarCanal() {
            UUID canalId = UUID.randomUUID();
            jdbc.update(
                    "INSERT INTO canal (id, nome, tipo) VALUES (?, ?, 'WHATSAPP')",
                    canalId,
                    "Canal " + canalId);
            return canalId;
        }

        private void excluirCanal(UUID canalId) {
            jdbc.update("DELETE FROM canal_credencial WHERE canal_id = ?", canalId);
            jdbc.update("DELETE FROM canal WHERE id = ?", canalId);
        }
    }

    @Nested
    @DisplayName("particionamento de mensagem")
    class Particionamento {

        @Test
        @DisplayName("inserir mensagem numa data coberta pela janela funciona")
        void mensagem_dataCoberta_ehInserida() {
            UUID atendimentoId = criarAtendimento();

            jdbc.update(
                    """
                    INSERT INTO mensagem (atendimento_id, remetente_tipo, tipo, conteudo, enviado_em)
                    VALUES (?, 'ATENDENTE', 'TEXTO', 'ola', ?)
                    """,
                    atendimentoId,
                    OffsetDateTime.now());

            Integer total = jdbc.queryForObject(
                    "SELECT count(*) FROM mensagem WHERE atendimento_id = ?", Integer.class, atendimentoId);
            assertThat(total).isEqualTo(1);
        }

        @Test
        @DisplayName("a janela minima esta completa depois das migrations")
        void particoes_aposMigrations_janelaMinimaCompleta() {
            assertThat(particoes.particoesFaltantes(1)).isEmpty();
            assertThat(particoes.particoesFaltantes(3)).isEmpty();
        }

        /**
         * A V5 cria mes corrente + 3. Pedir uma janela muito maior tem, necessariamente, meses sem
         * particao — e o jeito de exercitar a deteccao sem depender da data em que o teste roda.
         */
        @Test
        @DisplayName("a verificacao acusa os meses sem particao")
        void verificacao_janelaAlemDoCriado_acusaFaltantes() {
            int janelaLonga = 60;

            assertThat(particoes.particoesFaltantes(janelaLonga)).isNotEmpty();

            assertThatThrownBy(() -> particoes.verificarJanelaMinima(janelaLonga))
                    .isInstanceOf(ParticaoMensagemAusenteException.class)
                    .hasMessageContaining("garantir_particoes_mensagem")
                    .hasMessageContaining("sem particao para");
        }

        @Test
        @DisplayName("garantir a janela cria o que falta e e idempotente")
        void garantirJanela_chamadaDuasVezes_naoQuebra() {
            int janela = 6;
            particoes.garantirJanela(janela);
            assertThat(particoes.particoesFaltantes(janela)).isEmpty();

            particoes.garantirJanela(janela);
            assertThat(particoes.particoesFaltantes(janela)).isEmpty();
        }

        /**
         * O caso que a rede de seguranca existe para cobrir: chegou mensagem numa faixa sem
         * particao mensal. Sem a DEFAULT o INSERT falharia e a mensagem do cliente se perderia;
         * com ela, a linha e aceita e vira divida de manutencao.
         *
         * <p>A data e no passado de proposito. Nenhum outro teste cria particao para tras, entao
         * essa linha nao briga com a criacao de janelas futuras — que e justamente o efeito
         * colateral que a DEFAULT provoca quando ha linhas na faixa de um mes a criar.
         */
        @Test
        @DisplayName("mensagem fora da janela cai na particao DEFAULT em vez de falhar")
        void mensagem_dataForaDaJanela_caiNaParticaoDefault() {
            UUID atendimentoId = criarAtendimento();
            OffsetDateTime foraDaJanela = OffsetDateTime.now().minusYears(3);

            jdbc.update(
                    """
                    INSERT INTO mensagem (atendimento_id, remetente_tipo, tipo, conteudo, enviado_em)
                    VALUES (?, 'LEAD', 'TEXTO', 'mensagem fora da janela', ?)
                    """,
                    atendimentoId,
                    foraDaJanela);

            Integer naDefault = jdbc.queryForObject(
                    "SELECT count(*) FROM mensagem_default WHERE atendimento_id = ?",
                    Integer.class,
                    atendimentoId);
            assertThat(naDefault).isEqualTo(1);

            // E continua visivel pela tabela particionada: o atendimento nao perdeu a mensagem.
            Integer pelaTabelaPai = jdbc.queryForObject(
                    "SELECT count(*) FROM mensagem WHERE atendimento_id = ?", Integer.class, atendimentoId);
            assertThat(pelaTabelaPai).isEqualTo(1);
        }

        /**
         * Rede de seguranca sem alarme some do radar ate a limpeza ficar cara. O job diario existe
         * para que a divida seja descoberta no dia seguinte, nao no mes seguinte.
         */
        @Test
        @DisplayName("o alerta enxerga as linhas presas na DEFAULT")
        void alerta_comLinhaNaDefault_contabilizaAAnomalia() {
            UUID atendimentoId = criarAtendimento();
            jdbc.update(
                    """
                    INSERT INTO mensagem (atendimento_id, remetente_tipo, tipo, conteudo, enviado_em)
                    VALUES (?, 'LEAD', 'TEXTO', 'anomalia', ?)
                    """,
                    atendimentoId,
                    OffsetDateTime.now().minusYears(4));

            assertThat(particoes.mensagensNaParticaoDefault()).isPositive();

            // Nao lanca: o job alerta e segue, porque derrubar o agendador nao drena nada.
            particoes.alertarSobreParticaoDefault();
        }

        private UUID criarAtendimento() {
            UUID leadId = UUID.randomUUID();
            UUID atendimentoId = UUID.randomUUID();
            jdbc.update("INSERT INTO lead (id, nome) VALUES (?, 'Lead de teste')", leadId);
            jdbc.update("INSERT INTO atendimento (id, lead_id) VALUES (?, ?)", atendimentoId, leadId);
            return atendimentoId;
        }
    }
}
