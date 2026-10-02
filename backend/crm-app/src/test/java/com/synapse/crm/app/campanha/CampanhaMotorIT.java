package com.synapse.crm.app.campanha;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.synapse.crm.atendimento.domain.canal.TemplateDoCanal;
import com.synapse.crm.campanhas.application.CampanhaRepositorio;
import com.synapse.crm.campanhas.application.ProcessarDestinatarioDeCampanhaUseCase;
import com.synapse.crm.campanhas.application.TransacoesDeCampanha;
import com.synapse.crm.campanhas.domain.Campanha;
import com.synapse.crm.sharedkernel.identidade.ContextoDeServico;

/**
 * O motor de envio de campanhas ponta a ponta, em Postgres real. Cada teste prova uma promessa do prompt E220
 * de um jeito que um defeito a quebraria: limite, atomicidade, concorrencia, reinicio, conferencia, pausa.
 */
class CampanhaMotorIT extends CampanhaITBase {

    @Autowired
    private CampanhaRepositorio repositorio;

    @Autowired
    private TransacoesDeCampanha transacoes;

    @Nested
    @DisplayName("limite diario e atomicidade do envio")
    class LimiteEAtomicidade {

        @Test
        @DisplayName("envia ate o limite do dia, nao troca o dono do lead e nao deixa reserva, mensagem ou outbox orfas")
        void envia_ateOLimite_semDonoENemOrfaos() {
            criarLeadsElegiveis(8);
            Campanha campanha = criarEIniciar("Natal", 5);
            assertThat(campanha.contadores().pendentes()).isEqualTo(8);

            rodarCiclo();
            rodarCiclo();

            UUID id = campanha.id();
            assertThat(destinatariosCom(id, "ENFILEIRADO")).isEqualTo(5);
            assertThat(destinatariosCom(id, "PENDENTE")).isEqualTo(3);
            Campanha.Contadores contadores = consultas.obter(id).campanha().contadores();
            assertThat(contadores.enfileirados()).isEqualTo(5);
            assertThat(contadores.pendentes()).isEqualTo(3);
            // Uma reserva E219, uma mensagem, um atendimento finalizado e uma saida na outbox por enviado.
            // Se o estouro do limite nao desfizesse a transacao, haveria 6 de cada.
            assertThat(contar(
                            "SELECT count(*) FROM envio_proativo_reserva WHERE tipo = 'CAMPANHA' AND regra_id = ?",
                            id.toString()))
                    .isEqualTo(5);
            assertThat(saidasNaOutbox()).isEqualTo(5);
            assertThat(contar("SELECT enfileiradas FROM campanha_envio_dia")).isEqualTo(5);
            assertThat(contar("SELECT enfileiradas FROM campanha_template_dia WHERE campanha_id = ?", id))
                    .isEqualTo(5);
            assertThat(contar(
                            """
                            SELECT count(*) FROM mensagem m
                              JOIN atendimento a ON a.id = m.atendimento_id
                              JOIN lead l ON l.id = a.lead_id
                             WHERE l.nome LIKE 'E220-%' AND a.status = 'FINALIZADO' AND a.atendente_id IS NULL
                            """))
                    .isEqualTo(5);
        }

        @Test
        @DisplayName("RN-CRM-06: a campanha nao atribui dono ao lead nem abre atendimento em andamento")
        void naoTransfereOLeadNemAbreAtendimento() {
            criarLeadsElegiveis(4);
            criarEIniciar("Natal", 10);

            rodarCiclo();

            assertThat(contar("SELECT count(*) FROM lead WHERE nome LIKE 'E220-%' AND atendente_responsavel_id IS NOT NULL"))
                    .isZero();
            assertThat(contar(
                            """
                            SELECT count(*) FROM atendimento a JOIN lead l ON l.id = a.lead_id
                             WHERE l.nome LIKE 'E220-%' AND a.status <> 'FINALIZADO'
                            """))
                    .isZero();
            assertThat(contar("SELECT count(*) FROM lead WHERE nome LIKE 'E220-%' AND status_basico <> 'IA'")).isZero();
        }

        @Test
        @DisplayName("limite alterado em campanha em andamento vale no ciclo seguinte e a campanha conclui sozinha")
        void limiteAlterado_valeNoProximoCiclo() {
            criarLeadsElegiveis(8);
            Campanha campanha = criarEIniciar("Natal", 5);
            rodarCiclo();
            assertThat(destinatariosCom(campanha.id(), "ENFILEIRADO")).isEqualTo(5);

            controle.alterarLimite(campanha.id(), 8, null);
            rodarCiclo();

            assertThat(destinatariosCom(campanha.id(), "ENFILEIRADO")).isEqualTo(8);
            assertThat(destinatariosCom(campanha.id(), "PENDENTE")).isZero();
            assertThat(statusDaCampanha(campanha.id())).isEqualTo("CONCLUIDA");
        }

        @Test
        @DisplayName("o teto da instancia nao e ultrapassado somando campanhas")
        void tetoDaInstancia_somaDeTodasAsCampanhas() {
            jdbc.update("UPDATE configuracao_automacao SET valor = '6' WHERE chave = 'campanhas.teto_diario_instancia'");
            criarLeadsElegiveis(10);
            Campanha a = criarEIniciar("A", 5);
            Campanha b = criarEIniciar("B", 5);

            rodarCiclo();
            rodarCiclo();

            assertThat(contar("SELECT enfileiradas FROM campanha_envio_dia")).isEqualTo(6);
            assertThat(saidasNaOutbox()).isEqualTo(6);
            assertThat(destinatariosCom(a.id(), "ENFILEIRADO") + destinatariosCom(b.id(), "ENFILEIRADO")).isEqualTo(6);
        }

        @Test
        @DisplayName("a vaga do dia vale por dia: o dia seguinte comeca do zero e o teto da instancia segura")
        void vagaDoDia_viradaDoDia() {
            Campanha campanha = criar.executar(pedido("Natal", 5));
            LocalDate hoje = LocalDate.of(2026, 10, 5);

            assertThat(vaga(campanha.id(), hoje, 2, 100)).isTrue();
            assertThat(vaga(campanha.id(), hoje, 2, 100)).isTrue();
            assertThat(vaga(campanha.id(), hoje, 2, 100)).as("terceira no mesmo dia, limite 2").isFalse();
            assertThat(vaga(campanha.id(), hoje.plusDays(1), 2, 100)).as("dia seguinte").isTrue();
            assertThat(vaga(campanha.id(), hoje.plusDays(2), 5, 1))
                    .as("o teto da instancia (1) para um dia que a campanha ainda tem folga")
                    .isTrue();
            assertThat(vaga(campanha.id(), hoje.plusDays(2), 5, 1)).isFalse();
        }

        private boolean vaga(UUID campanhaId, LocalDate dia, int limiteDaCampanha, int teto) {
            return transacoes.noChat(() -> repositorio.reservarVagaDoDia(campanhaId, dia, limiteDaCampanha, teto));
        }
    }

    @Nested
    @DisplayName("concorrencia e reinicio")
    class ConcorrenciaEReinicio {

        @Test
        @DisplayName("quatro threads processando a mesma campanha nunca duplicam destinatario, reserva, mensagem ou saida")
        void quatroThreads_semDuplicar() throws Exception {
            criarLeadsElegiveis(30);
            Campanha campanha = criarEIniciar("Natal", 200);
            ProcessarDestinatarioDeCampanhaUseCase.Entrada entrada = new ProcessarDestinatarioDeCampanhaUseCase.Entrada(
                    campanha.id(), campanha.template(), campanha.mapeamento(), LocalDate.now(), 200, 200);

            ExecutorService pool = Executors.newFixedThreadPool(4);
            try {
                List<Future<?>> tarefas = new ArrayList<>();
                for (int thread = 0; thread < 4; thread++) {
                    tarefas.add(pool.submit(() -> ContextoDeServico.executarComo("teste-e220", () -> {
                        for (int i = 0; i < 15; i++) {
                            processador.executar(entrada);
                        }
                    })));
                }
                for (Future<?> tarefa : tarefas) {
                    tarefa.get(60, TimeUnit.SECONDS);
                }
            } finally {
                pool.shutdownNow();
            }

            UUID id = campanha.id();
            assertThat(destinatariosCom(id, "ENFILEIRADO")).isEqualTo(30);
            assertThat(contar(
                            "SELECT count(DISTINCT mensagem_id) FROM campanha_template_destinatario WHERE campanha_id = ?", id))
                    .isEqualTo(30);
            assertThat(saidasNaOutbox()).isEqualTo(30);
            assertThat(contar(
                            """
                            SELECT count(*) FROM (SELECT lead_id FROM envio_proativo_reserva
                                                   WHERE tipo = 'CAMPANHA' GROUP BY lead_id HAVING count(*) > 1) x
                            """))
                    .isZero();
            Campanha.Contadores contadores = consultas.obter(id).campanha().contadores();
            assertThat(contadores.enfileirados()).isEqualTo(30);
            assertThat(contadores.pendentes()).isZero();
        }

        @Test
        @DisplayName("dois ciclos simultaneos pelo agendador: o lease deixa um so trabalhar na campanha")
        void doisCiclosSimultaneos_porLease() throws Exception {
            criarLeadsElegiveis(20);
            Campanha campanha = criarEIniciar("Natal", 200);
            jdbc.update("UPDATE campanha_template SET ultimo_ciclo_em = now() - interval '2 minutes'");

            ExecutorService pool = Executors.newFixedThreadPool(2);
            try {
                Future<?> primeiro = pool.submit(agendador::executarCiclo);
                Future<?> segundo = pool.submit(agendador::executarCiclo);
                primeiro.get(60, TimeUnit.SECONDS);
                segundo.get(60, TimeUnit.SECONDS);
            } finally {
                pool.shutdownNow();
            }

            assertThat(destinatariosCom(campanha.id(), "ENFILEIRADO")).isEqualTo(20);
            assertThat(saidasNaOutbox()).isEqualTo(20);
            assertThat(contar("SELECT count(DISTINCT mensagem_id) FROM campanha_template_destinatario"))
                    .isEqualTo(20);
        }

        @Test
        @DisplayName("lease de um worker vivo bloqueia o ciclo; expirado, a campanha retoma sem reenviar ninguem")
        void reinicioNoMeio_retomaSemReenviar() {
            criarLeadsElegiveis(6);
            Campanha campanha = criarEIniciar("Natal", 200);
            ProcessarDestinatarioDeCampanhaUseCase.Entrada entrada = new ProcessarDestinatarioDeCampanhaUseCase.Entrada(
                    campanha.id(), campanha.template(), campanha.mapeamento(), LocalDate.now(), 200, 200);
            ContextoDeServico.executarComo("teste-e220", () -> {
                for (int i = 0; i < 3; i++) {
                    processador.executar(entrada);
                }
            });
            assertThat(destinatariosCom(campanha.id(), "ENFILEIRADO")).isEqualTo(3);

            // Um worker que morreu no meio deixou o lease no futuro: ninguem mais mexe na campanha.
            jdbc.update("UPDATE campanha_template SET lease_ate = now() + interval '1 hour'");
            rodarCiclo();
            assertThat(destinatariosCom(campanha.id(), "ENFILEIRADO")).as("lease vivo").isEqualTo(3);

            // O lease expirou: o ciclo seguinte continua de onde parou.
            jdbc.update("UPDATE campanha_template SET lease_ate = now() - interval '1 second'");
            rodarCiclo();

            assertThat(destinatariosCom(campanha.id(), "ENFILEIRADO")).isEqualTo(6);
            assertThat(saidasNaOutbox()).isEqualTo(6);
            assertThat(contar("SELECT count(DISTINCT mensagem_id) FROM campanha_template_destinatario")).isEqualTo(6);
        }
    }

    @Nested
    @DisplayName("estado ambiguo: conferencia manual, nunca reenvio")
    class Conferencia {

        @Test
        @DisplayName("despacho sem resultado (E209) vira FALHA com conferencia e a campanha nunca reenvia")
        void despachoSemResultado_vaiParaConferencia() {
            criarLeadsElegiveis(1);
            Campanha campanha = criarEIniciar("Natal", 10);
            rodarCiclo();
            UUID id = campanha.id();
            UUID destinatario = destinatarioUnico(id);
            jdbc.update(
                    "UPDATE outbox_evento SET despachado_em = now(), proxima_tentativa_em = now() - interval '1 second'"
                            + " WHERE tipo = 'canal.mensagem.enviar'");

            outbox.publicarPendentes();

            esperar().untilAsserted(() -> {
                assertThat(statusDoDestinatario(destinatario)).isEqualTo("FALHA");
                assertThat(contar(
                                "SELECT count(*) FROM campanha_template_destinatario WHERE id = ? AND conferencia_em IS NOT NULL"
                                        + " AND motivo = 'ENVIO_NAO_CONFIRMADO'",
                                destinatario))
                        .isEqualTo(1);
            });
            Campanha.Contadores contadores = consultas.obter(id).campanha().contadores();
            assertThat(contadores.falhas()).isEqualTo(1);
            assertThat(contadores.conferencia()).isEqualTo(1);
            assertThat(canal.enviados()).as("nada saiu para o provedor").isEmpty();
            assertThat(consultas.conferencia(id, 0, 25).itens()).hasSize(1);

            rodarCiclo();
            rodarCiclo();
            outbox.publicarPendentes();

            assertThat(canal.enviados()).as("nunca reenviado").isEmpty();
            assertThat(saidasNaOutbox()).isEqualTo(1);

            assertThat(controle.resolverConferencia(id, destinatario)).isTrue();
            assertThat(consultas.obter(id).campanha().contadores().conferencia()).isZero();
            assertThat(consultas.conferencia(id, 0, 25).itens()).isEmpty();
        }

        @Test
        @DisplayName("enfileirado ha mais que o prazo sem confirmacao vai para a conferencia e sai dela quando o envio se confirma")
        void enfileiradoParado_vaiParaConferencia_eSaiQuandoConfirma() {
            criarLeadsElegiveis(1);
            Campanha campanha = criarEIniciar("Natal", 10);
            rodarCiclo();
            UUID id = campanha.id();
            UUID destinatario = destinatarioUnico(id);
            jdbc.update(
                    "UPDATE campanha_template_destinatario SET enfileirado_em = now() - interval '2 hours' WHERE id = ?",
                    destinatario);

            rodarCiclo();

            assertThat(statusDoDestinatario(destinatario)).as("segue enfileirado, nunca reenviado").isEqualTo("ENFILEIRADO");
            assertThat(consultas.obter(id).campanha().contadores().conferencia()).isEqualTo(1);
            assertThat(consultas.conferencia(id, 0, 25).itens()).hasSize(1);
            assertThat(saidasNaOutbox()).isEqualTo(1);

            outbox.publicarPendentes();

            esperar().untilAsserted(() -> assertThat(statusDoDestinatario(destinatario)).isEqualTo("ENVIADO"));
            Campanha.Contadores contadores = consultas.obter(id).campanha().contadores();
            assertThat(contadores.conferencia()).as("desfecho conhecido sai da conferencia").isZero();
            assertThat(contadores.enviados()).isEqualTo(1);
            assertThat(canal.enviados()).hasSize(1);
        }

        private UUID destinatarioUnico(UUID campanhaId) {
            return jdbc.queryForObject(
                    "SELECT id FROM campanha_template_destinatario WHERE campanha_id = ? AND status = 'ENFILEIRADO'",
                    UUID.class,
                    campanhaId);
        }
    }

    @Nested
    @DisplayName("funil de entrega")
    class Funil {

        @Test
        @DisplayName("a entrega avanca o funil, pula degraus quando a Meta pula, e e idempotente e monotona")
        void entrega_avancaIdempotenteEMonotona() {
            criarLeadsElegiveis(1);
            Campanha campanha = criarEIniciar("Natal", 10);
            rodarCiclo();
            UUID id = campanha.id();
            UUID mensagemId = mensagemUnica(id);

            outbox.publicarPendentes();
            // Awaitility roda em outra thread, sem usuario autenticado: a espera le o contador direto do banco.
            esperar().untilAsserted(() ->
                    assertThat(contar("SELECT qtd_enviados FROM campanha_template WHERE id = ?", id)).isEqualTo(1));

            entregas.executar(mensagemId, "LIDO", Instant.now());

            Campanha.Contadores contadores = consultas.obter(id).campanha().contadores();
            assertThat(contadores.entregues()).as("pulou ENTREGUE: o degrau conta").isEqualTo(1);
            assertThat(contadores.lidos()).isEqualTo(1);
            entregas.executar(mensagemId, "LIDO", Instant.now());
            entregas.executar(mensagemId, "ENTREGUE", Instant.now());
            entregas.executar(mensagemId, "ENVIADO", Instant.now());
            Campanha.Contadores depois = consultas.obter(id).campanha().contadores();
            assertThat(depois.enviados()).isEqualTo(1);
            assertThat(depois.entregues()).isEqualTo(1);
            assertThat(depois.lidos()).isEqualTo(1);
            assertThat(contar(
                            "SELECT count(*) FROM campanha_template_destinatario WHERE campanha_id = ?"
                                    + " AND enviado_em IS NOT NULL AND entregue_em IS NOT NULL AND lido_em IS NOT NULL",
                            id))
                    .isEqualTo(1);
        }

        @Test
        @DisplayName("mensagem que nao e de campanha e ignorada, sem erro")
        void mensagemQueNaoEDeCampanha_ignorada() {
            entregas.executar(UUID.randomUUID(), "ENTREGUE", Instant.now());
        }
    }

    @Nested
    @DisplayName("pausa automatica e interruptores")
    class Pausa {

        @Test
        @DisplayName("taxa de falha acima do limiar pausa a campanha, e o ciclo seguinte nao envia mais")
        void taxaDeFalha_pausa() {
            jdbc.update("UPDATE configuracao_automacao SET valor = '5' WHERE chave = 'campanhas.pausa.minimo_amostra'");
            jdbc.update("UPDATE configuracao_automacao SET valor = '10' WHERE chave = 'campanhas.pausa.janela_envios'");
            criarLeadsElegiveis(12);
            Campanha campanha = criarEIniciar("Natal", 6);
            rodarCiclo();
            UUID id = campanha.id();
            assertThat(destinatariosCom(id, "ENFILEIRADO")).isEqualTo(6);

            for (UUID mensagemId : mensagensDe(id)) {
                falhar(mensagemId, 131026);
            }

            assertThat(statusDaCampanha(id)).isEqualTo("PAUSADA_AUTOMATICAMENTE");
            assertThat(consultas.obter(id).campanha().motivoDePausa()).startsWith("TAXA_DE_FALHA:");
            controle.alterarLimite(id, 12, null);
            rodarCiclo();
            assertThat(destinatariosCom(id, "PENDENTE")).as("pausada nao envia").isEqualTo(6);
        }

        @Test
        @DisplayName("erro de limite da Meta (131048) pausa na hora, com o codigo no motivo")
        void erroDeLimiteDaMeta_pausaDeImediato() {
            // Mais leads que o limite: a campanha segue EM_ANDAMENTO. Campanha ja concluida nao tem o que pausar.
            criarLeadsElegiveis(4);
            Campanha campanha = criarEIniciar("Natal", 2);
            rodarCiclo();

            falhar(mensagensDe(campanha.id()).get(0), 131048);

            assertThat(statusDaCampanha(campanha.id())).isEqualTo("PAUSADA_AUTOMATICAMENTE");
            assertThat(consultas.obter(campanha.id()).campanha().motivoDePausa()).isEqualTo("ERRO_DA_META:131048");
        }

        @Test
        @DisplayName("erro por destinatario (131026) sozinho nao pausa: so a taxa conta")
        void erroPorDestinatario_naoPausaSozinho() {
            criarLeadsElegiveis(3);
            Campanha campanha = criarEIniciar("Natal", 10);
            rodarCiclo();

            falhar(mensagensDe(campanha.id()).get(0), 131026);

            assertThat(statusDaCampanha(campanha.id())).isNotEqualTo("PAUSADA_AUTOMATICAMENTE");
        }

        @Test
        @DisplayName("template pausado, rejeitado ou removido pausa a campanha com o motivo")
        void templateIndisponivel_pausa() {
            for (TemplateDoCanal.Status status :
                    List.of(TemplateDoCanal.Status.PAUSADO, TemplateDoCanal.Status.REJEITADO)) {
                jdbc.update("DELETE FROM campanha_template");
                criarLeadsElegiveis(2);
                Campanha campanha = criarEIniciar("Natal " + status, 10);
                canal.trocarStatusDoTemplate(TEMPLATE, status);
                templates.descartarCache();

                rodarCiclo();

                assertThat(statusDaCampanha(campanha.id())).isEqualTo("PAUSADA_AUTOMATICAMENTE");
                assertThat(consultas.obter(campanha.id()).campanha().motivoDePausa())
                        .isEqualTo("TEMPLATE_INDISPONIVEL:" + status);
                assertThat(destinatariosCom(campanha.id(), "ENFILEIRADO")).isZero();
                registrarTemplateAprovado(TEMPLATE);
            }
            jdbc.update("DELETE FROM campanha_template");
            criarLeadsElegiveis(2);
            Campanha campanha = criarEIniciar("Natal removido", 10);
            canal.removerTemplate(TEMPLATE);
            templates.descartarCache();

            rodarCiclo();

            assertThat(consultas.obter(campanha.id()).campanha().motivoDePausa()).isEqualTo("TEMPLATE_INDISPONIVEL:REMOVIDO");
        }

        @Test
        @DisplayName("falha ao consultar o provedor NAO pausa: so nao envia agora, e retoma quando volta")
        void provedorForaDoAr_naoPausa() {
            criarLeadsElegiveis(3);
            Campanha campanha = criarEIniciar("Natal", 10);
            canal.quebrarListagemDeTemplates();
            templates.descartarCache();

            rodarCiclo();

            assertThat(statusDaCampanha(campanha.id())).isEqualTo("EM_ANDAMENTO");
            assertThat(destinatariosCom(campanha.id(), "ENFILEIRADO")).isZero();

            canal.consertarListagemDeTemplates();
            templates.descartarCache();
            rodarCiclo();

            assertThat(destinatariosCom(campanha.id(), "ENFILEIRADO")).isEqualTo(3);
        }

        @Test
        @DisplayName("interruptor global e o da campanha param o envio sem perder nada, e religar continua")
        void interruptores_paramESeguem() {
            criarLeadsElegiveis(4);
            Campanha campanha = criarEIniciar("Natal", 10);

            jdbc.update("UPDATE configuracao_automacao SET valor = 'false' WHERE chave = 'campanhas.envio_habilitado'");
            rodarCiclo();
            assertThat(destinatariosCom(campanha.id(), "ENFILEIRADO")).as("global desligado").isZero();
            jdbc.update("UPDATE configuracao_automacao SET valor = 'true' WHERE chave = 'campanhas.envio_habilitado'");

            controle.alterarInterruptor(campanha.id(), true);
            rodarCiclo();
            assertThat(destinatariosCom(campanha.id(), "ENFILEIRADO")).as("campanha desligada").isZero();
            assertThat(destinatariosCom(campanha.id(), "PENDENTE")).as("nada se perdeu").isEqualTo(4);

            controle.alterarInterruptor(campanha.id(), false);
            rodarCiclo();
            assertThat(destinatariosCom(campanha.id(), "ENFILEIRADO")).isEqualTo(4);
        }

        @Test
        @DisplayName("pausa manual para o envio e retomar continua; cancelar encerra e nunca envia o que falta")
        void pausarRetomarCancelar() {
            criarLeadsElegiveis(4);
            Campanha campanha = criarEIniciar("Natal", 2);
            rodarCiclo();
            assertThat(destinatariosCom(campanha.id(), "ENFILEIRADO")).isEqualTo(2);

            controle.pausar(campanha.id());
            controle.alterarLimite(campanha.id(), 4, null);
            rodarCiclo();
            assertThat(destinatariosCom(campanha.id(), "ENFILEIRADO")).as("pausada").isEqualTo(2);

            controle.retomar(campanha.id());
            rodarCiclo();
            assertThat(destinatariosCom(campanha.id(), "ENFILEIRADO")).isEqualTo(4);

            Campanha outra = criar.executar(pedido("Outra", 10));
            iniciar.executar(outra.id());
            controle.cancelar(outra.id());
            rodarCiclo();
            assertThat(statusDaCampanha(outra.id())).isEqualTo("CANCELADA");
            assertThat(destinatariosCom(outra.id(), "ENFILEIRADO")).isZero();
        }

        @Test
        @DisplayName("opt-out registrado depois de iniciar e respeitado no envio")
        void optOut_respeitadoNoEnvio() {
            List<UUID> leads = criarLeadsElegiveis(3);
            Campanha campanha = criarEIniciar("Natal", 10);

            optOuts.registrar(leads.get(0), "pediu para parar");
            rodarCiclo();

            assertThat(destinatariosCom(campanha.id(), "ENFILEIRADO")).isEqualTo(2);
            assertThat(contar(
                            "SELECT count(*) FROM campanha_template_destinatario WHERE lead_id = ? AND status = 'IGNORADO'"
                                    + " AND motivo = 'OPT_OUT_NO_ENVIO'",
                            leads.get(0)))
                    .isEqualTo(1);
            assertThat(consultas.obter(campanha.id()).campanha().contadores().ignorados()).isEqualTo(1);
        }

        @Test
        @DisplayName("cooldown da politica proativa (E219) bloqueia o envio e o destinatario sai com o motivo")
        void cooldownDaE219_bloqueia() {
            definirConfiguracaoDaAutomacao("automacao_proativa.cooldown_horas", "24");
            List<UUID> leads = criarLeadsElegiveis(2);
            Campanha campanha = criarEIniciar("Natal", 10);
            jdbc.update(
                    """
                    INSERT INTO envio_proativo_reserva (chave, lead_id, tipo, regra_id, ocorrencia, requisicao_hash,
                                                        estado, reservado_em)
                    VALUES ('e220-cooldown', ?, 'CAMPANHA', 'outra-campanha', 'outra-campanha', repeat('a', 64),
                            'RESERVADO', now())
                    """,
                    leads.get(0));

            rodarCiclo();

            assertThat(destinatariosCom(campanha.id(), "ENFILEIRADO")).isEqualTo(1);
            assertThat(contar(
                            "SELECT count(*) FROM campanha_template_destinatario WHERE lead_id = ? AND campanha_id = ?"
                                    + " AND status = 'IGNORADO' AND motivo = 'COOLDOWN'",
                            leads.get(0),
                            campanha.id()))
                    .isEqualTo(1);
        }

        @Test
        @DisplayName("politica proativa desligada na instancia NAO consome os destinatarios: a campanha espera")
        void politicaDesligada_naoConsomeDestinatarios() {
            criarLeadsElegiveis(3);
            Campanha campanha = criarEIniciar("Natal", 10);
            definirConfiguracaoDaAutomacao("automacao_proativa.campanha.habilitada", "false");

            rodarCiclo();

            assertThat(destinatariosCom(campanha.id(), "PENDENTE")).as("nenhum virou IGNORADO").isEqualTo(3);
            definirConfiguracaoDaAutomacao("automacao_proativa.campanha.habilitada", "true");
            rodarCiclo();
            assertThat(destinatariosCom(campanha.id(), "ENFILEIRADO")).isEqualTo(3);
        }

        private void falhar(UUID mensagemId, int codigo) {
            jdbc.update(
                    "UPDATE mensagem SET status_entrega = 'FALHOU',"
                            + " erro_entrega = jsonb_build_object('codigo', ?::int, 'titulo', 'Falha simulada') WHERE id = ?",
                    codigo,
                    mensagemId);
            entregas.executar(mensagemId, "FALHOU", Instant.now());
        }
    }

    private String statusDoDestinatario(UUID destinatarioId) {
        return jdbc.queryForObject(
                "SELECT status FROM campanha_template_destinatario WHERE id = ?", String.class, destinatarioId);
    }

    private UUID mensagemUnica(UUID campanhaId) {
        return mensagensDe(campanhaId).get(0);
    }

    private List<UUID> mensagensDe(UUID campanhaId) {
        return jdbc.queryForList(
                "SELECT mensagem_id FROM campanha_template_destinatario WHERE campanha_id = ?"
                        + " AND mensagem_id IS NOT NULL ORDER BY enfileirado_em",
                UUID.class,
                campanhaId);
    }
}
