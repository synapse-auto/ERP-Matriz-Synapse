package com.synapse.crm.app.campanha;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.synapse.crm.app.seguranca.ApoioRls;
import com.synapse.crm.atendimento.application.proativo.ReservarEnvioProativoUseCase;
import com.synapse.crm.atendimento.domain.canal.TemplateDoCanal;
import com.synapse.crm.campanhas.application.AtualizarRascunhoUseCase;
import com.synapse.crm.campanhas.application.CampanhasIndisponiveisException;
import com.synapse.crm.campanhas.application.ConfiguracaoDeCampanhas;
import com.synapse.crm.campanhas.application.ListarExcluidosUseCase;
import com.synapse.crm.campanhas.application.PedidoDeCampanha;
import com.synapse.crm.campanhas.application.PublicoRepositorio.ContagemDoPublico;
import com.synapse.crm.campanhas.domain.Campanha;
import com.synapse.crm.campanhas.domain.CampanhaInvalidaException;
import com.synapse.crm.campanhas.domain.FiltroDePublico;
import com.synapse.crm.campanhas.domain.MotivoDoDestinatario;
import com.synapse.crm.campanhas.domain.StatusDaCampanha;
import com.synapse.crm.campanhas.domain.TransicaoDeStatusInvalidaException;
import com.synapse.crm.sharedkernel.identidade.ContextoDeServico;
import com.synapse.crm.sharedkernel.identidade.PapelUsuario;
import com.synapse.crm.sharedkernel.persistencia.Pools;

/** Publico, validacoes de criacao, envio de teste, permissoes por papel, RLS, configuracao e auditoria. */
class CampanhaPublicoEControleIT extends CampanhaITBase {

    @Autowired
    private AtualizarRascunhoUseCase atualizarRascunho;

    @Autowired
    private ListarExcluidosUseCase excluidosUseCase;

    @Autowired
    private ReservarEnvioProativoUseCase reservarProativo;

    @Autowired
    @Qualifier(Pools.CHAT_TRANSACTION_MANAGER) private PlatformTransactionManager gerenteDoChat;

    @Autowired
    @Qualifier(Pools.CHAT_DATA_SOURCE) private DataSource chatDataSource;

    @Nested
    @DisplayName("publico: exclusoes com o motivo certo e filtros")
    class Publico {

        @Test
        @DisplayName("a previa conta total, elegiveis e cada exclusao pelo primeiro motivo que se aplica, sem materializar")
        void previa_excluiComMotivoCerto() {
            definirConfiguracaoDaAutomacao("automacao_proativa.cooldown_horas", "24");
            criarLeadsElegiveis(3);
            UUID telefoneInvalido = criarLeadsElegiveis(1).get(0);
            // O banco ja recusa telefone nao canonico (ck_lead_telefone_canonico); sem telefone e o caso invalido real.
            jdbc.update("UPDATE lead SET telefone = NULL WHERE id = ?", telefoneInvalido);
            UUID semNome = criarLeadsElegiveis(1).get(0);
            jdbc.update("UPDATE lead SET nome = ? WHERE id = ?", PREFIXO + "1234", semNome);
            UUID comOptOut = criarLeadsElegiveis(1).get(0);
            optOuts.registrar(comOptOut, "pediu para parar");
            UUID emAtendimento = criarLeadsElegiveis(1).get(0);
            jdbc.update(
                    "INSERT INTO atendimento (id, lead_id, status) VALUES (?, ?, 'EM_IA')", UUID.randomUUID(), emAtendimento);
            UUID comProativaRecente = criarLeadsElegiveis(1).get(0);
            jdbc.update(
                    """
                    INSERT INTO envio_proativo_reserva (chave, lead_id, tipo, regra_id, ocorrencia, requisicao_hash,
                                                        estado, reservado_em)
                    VALUES ('e220-recente', ?, 'FOLLOW_UP', 'regra', 'oc', repeat('b', 64), 'RESERVADO', now())
                    """,
                    comProativaRecente);

            ContagemDoPublico contagem = prever.executar(filtroDoTeste());

            assertThat(contagem.total()).isEqualTo(8);
            assertThat(contagem.elegiveis()).isEqualTo(3);
            assertThat(contagem.excluidosPorMotivo())
                    .containsEntry(MotivoDoDestinatario.TELEFONE_INVALIDO, 1L)
                    .containsEntry(MotivoDoDestinatario.SEM_NOME_UTILIZAVEL, 1L)
                    .containsEntry(MotivoDoDestinatario.OPT_OUT, 1L)
                    .containsEntry(MotivoDoDestinatario.ATENDIMENTO_ATIVO, 1L)
                    .containsEntry(MotivoDoDestinatario.RECEBEU_PROATIVA_RECENTE, 1L);
            assertThat(contar("SELECT count(*) FROM campanha_template_destinatario"))
                    .as("previa nao materializa")
                    .isZero();
        }

        @Test
        @DisplayName("a mesma regra vale ao iniciar: destinatarios nascem PENDENTE ou IGNORADO com o motivo")
        void iniciar_materializaComMotivos() {
            criarLeadsElegiveis(2);
            UUID semTelefone = criarLeadsElegiveis(1).get(0);
            jdbc.update("UPDATE lead SET telefone = NULL WHERE id = ?", semTelefone);

            Campanha campanha = criarEIniciar("Natal", 10);

            assertThat(campanha.contadores().total()).isEqualTo(3);
            assertThat(campanha.contadores().pendentes()).isEqualTo(2);
            assertThat(campanha.contadores().ignorados()).isEqualTo(1);
            assertThat(contar(
                            "SELECT count(*) FROM campanha_template_destinatario WHERE lead_id = ? AND status = 'IGNORADO'"
                                    + " AND motivo = 'TELEFONE_INVALIDO'",
                            semTelefone))
                    .isEqualTo(1);
        }

        @Test
        @DisplayName("lista dos excluidos de um motivo, para conferir antes de iniciar")
        void listaDeExcluidos() {
            UUID comOptOut = criarLeadsElegiveis(1).get(0);
            optOuts.registrar(comOptOut, null);

            var excluidos = excluidosUseCase.executar(filtroDoTeste(), MotivoDoDestinatario.OPT_OUT);

            assertThat(excluidos).extracting(e -> e.leadId()).containsExactly(comOptOut);
            assertThatThrownBy(() -> excluidosUseCase.executar(filtroDoTeste(), MotivoDoDestinatario.COOLDOWN))
                    .isInstanceOf(CampanhaInvalidaException.class);
        }

        @Test
        @DisplayName("filtros por tag, nunca conversou e data de cadastro")
        void filtros() {
            List<UUID> leads = criarLeadsElegiveis(3);
            UUID tag = UUID.randomUUID();
            jdbc.update("INSERT INTO tag (id, nome, cor) VALUES (?, ?, '#336699')", tag, PREFIXO + "vip");
            jdbc.update("INSERT INTO lead_tag (lead_id, tag_id) VALUES (?, ?)", leads.get(0), tag);
            jdbc.update("INSERT INTO lead_tag (lead_id, tag_id) VALUES (?, ?)", leads.get(1), tag);
            jdbc.update("UPDATE lead SET num_mensagens = 5 WHERE id = ?", leads.get(1));

            try {
                assertThat(prever.executar(new FiltroDePublico(List.of(tag), null, null, null, null, false, PREFIXO))
                                .total())
                        .isEqualTo(2);
                assertThat(prever.executar(new FiltroDePublico(List.of(tag), null, null, null, null, true, PREFIXO))
                                .total())
                        .as("tag + nunca conversou")
                        .isEqualTo(1);
                assertThat(prever.executar(new FiltroDePublico(
                                        List.of(), null, null, LocalDate.now().plusDays(1), null, false, PREFIXO))
                                .total())
                        .as("cadastrados a partir de amanha")
                        .isZero();
                assertThat(prever.executar(new FiltroDePublico(
                                        List.of(), null, null, LocalDate.now().minusDays(1), LocalDate.now().plusDays(1), false, PREFIXO))
                                .total())
                        .as("cadastrados entre ontem e amanha")
                        .isEqualTo(3);
            } finally {
                jdbc.update("DELETE FROM lead_tag WHERE tag_id = ?", tag);
                jdbc.update("DELETE FROM tag WHERE id = ?", tag);
            }
        }

        private FiltroDePublico filtroDoTeste() {
            return new FiltroDePublico(List.of(), null, null, null, null, false, PREFIXO);
        }
    }

    @Nested
    @DisplayName("validacoes de criacao e transicoes")
    class Validacoes {

        @Test
        @DisplayName("template com midia no cabecalho, variavel no cabecalho, botao dinamico, nao aprovado ou de autenticacao e recusado")
        void templateNaoSuportado_recusado() {
            registrarTemplate("tpl_midia", TemplateDoCanal.Status.APROVADO, TemplateDoCanal.Categoria.MARKETING,
                    new TemplateDoCanal.RecursosAlemDoCorpo(true, false, false, false));
            registrarTemplate("tpl_var_cab", TemplateDoCanal.Status.APROVADO, TemplateDoCanal.Categoria.MARKETING,
                    new TemplateDoCanal.RecursosAlemDoCorpo(false, true, false, false));
            registrarTemplate("tpl_botao", TemplateDoCanal.Status.APROVADO, TemplateDoCanal.Categoria.UTILIDADE,
                    new TemplateDoCanal.RecursosAlemDoCorpo(false, false, true, false));
            registrarTemplate("tpl_pendente", TemplateDoCanal.Status.PENDENTE, TemplateDoCanal.Categoria.MARKETING,
                    TemplateDoCanal.RecursosAlemDoCorpo.NENHUM);
            registrarTemplate("tpl_auth", TemplateDoCanal.Status.APROVADO, TemplateDoCanal.Categoria.AUTENTICACAO,
                    TemplateDoCanal.RecursosAlemDoCorpo.NENHUM);

            for (String nome : List.of("tpl_midia", "tpl_var_cab", "tpl_botao")) {
                assertThatThrownBy(() -> criar.executar(comTemplate(nome)))
                        .as(nome)
                        .isInstanceOfSatisfying(
                                CampanhaInvalidaException.class,
                                erro -> assertThat(erro.codigo()).isEqualTo("TEMPLATE_NAO_SUPORTADO"));
            }
            assertThatThrownBy(() -> criar.executar(comTemplate("tpl_pendente")))
                    .isInstanceOfSatisfying(
                            CampanhaInvalidaException.class,
                            erro -> assertThat(erro.codigo()).isEqualTo("TEMPLATE_NAO_APROVADO"));
            assertThatThrownBy(() -> criar.executar(comTemplate("tpl_auth")))
                    .isInstanceOf(CampanhaInvalidaException.class);
            assertThatThrownBy(() -> criar.executar(comTemplate("nao_existe")))
                    .isInstanceOfSatisfying(
                            CampanhaInvalidaException.class,
                            erro -> assertThat(erro.codigo()).isEqualTo("TEMPLATE_NAO_ENCONTRADO"));
            assertThat(contar("SELECT count(*) FROM campanha_template")).as("nada foi criado").isZero();
        }

        @Test
        @DisplayName("limite acima do teto e recusado ao criar, ao alterar e ao iniciar depois de o teto baixar")
        void limiteAcimaDoTeto() {
            assertThatThrownBy(() -> criar.executar(pedido("Natal", 201))).isInstanceOf(CampanhaInvalidaException.class);
            Campanha campanha = criar.executar(pedido("Natal", 100));
            assertThatThrownBy(() -> controle.alterarLimite(campanha.id(), 201, null))
                    .isInstanceOf(CampanhaInvalidaException.class);

            jdbc.update("UPDATE configuracao_automacao SET valor = '50' WHERE chave = 'campanhas.teto_diario_instancia'");
            criarLeadsElegiveis(2);

            assertThatThrownBy(() -> iniciar.executar(campanha.id()))
                    .isInstanceOfSatisfying(
                            CampanhaInvalidaException.class,
                            erro -> assertThat(erro.codigo()).isEqualTo("LIMITE_ACIMA_DO_TETO"));
            assertThat(statusDaCampanha(campanha.id())).isEqualTo("RASCUNHO");
        }

        @Test
        @DisplayName("sem destinatarios elegiveis a campanha nao inicia")
        void semDestinatarios() {
            Campanha campanha = criar.executar(pedido("Vazia", 10));

            assertThatThrownBy(() -> iniciar.executar(campanha.id()))
                    .isInstanceOfSatisfying(
                            CampanhaInvalidaException.class,
                            erro -> assertThat(erro.codigo()).isEqualTo("SEM_DESTINATARIOS"));
            assertThat(statusDaCampanha(campanha.id())).isEqualTo("RASCUNHO");
        }

        @Test
        @DisplayName("transicoes invalidas lancam: retomar rascunho, iniciar duas vezes, editar campanha iniciada")
        void transicoesInvalidas() {
            criarLeadsElegiveis(2);
            Campanha rascunho = criar.executar(pedido("Natal", 10));

            assertThatThrownBy(() -> controle.retomar(rascunho.id()))
                    .isInstanceOf(TransicaoDeStatusInvalidaException.class);
            assertThatThrownBy(() -> controle.pausar(rascunho.id()))
                    .isInstanceOf(TransicaoDeStatusInvalidaException.class);

            Campanha editada = atualizarRascunho.executar(rascunho.id(), pedido("Natal editado", 20));
            assertThat(editada.nome()).isEqualTo("Natal editado");
            assertThat(editada.limiteDiario()).isEqualTo(20);

            iniciar.executar(rascunho.id());

            assertThatThrownBy(() -> iniciar.executar(rascunho.id()))
                    .isInstanceOf(TransicaoDeStatusInvalidaException.class);
            assertThatThrownBy(() -> atualizarRascunho.executar(rascunho.id(), pedido("de novo", 5)))
                    .isInstanceOf(TransicaoDeStatusInvalidaException.class);
        }

        @Test
        @DisplayName("agendada para o futuro nao envia; chegada a hora, comeca sozinha")
        void agendamento() {
            criarLeadsElegiveis(2);
            PedidoDeCampanha base = pedido("Agendada", 10);
            Campanha campanha = criar.executar(new PedidoDeCampanha(
                    base.nome(),
                    base.templateNome(),
                    base.templateIdioma(),
                    base.mapeamento(),
                    base.filtro(),
                    base.limiteDiario(),
                    base.janela(),
                    base.ritmoPorMinuto(),
                    base.rampa(),
                    Instant.now().plusSeconds(3600)));

            Campanha iniciada = iniciar.executar(campanha.id());
            assertThat(iniciada.status()).isEqualTo(StatusDaCampanha.AGENDADA);
            rodarCiclo();
            assertThat(destinatariosCom(campanha.id(), "PENDENTE")).isEqualTo(2);

            jdbc.update("UPDATE campanha_template SET agendada_para = now() - interval '1 minute'");
            rodarCiclo();

            assertThat(statusDaCampanha(campanha.id())).isIn("EM_ANDAMENTO", "CONCLUIDA");
            assertThat(destinatariosCom(campanha.id(), "ENFILEIRADO")).isEqualTo(2);
        }

        private PedidoDeCampanha comTemplate(String template) {
            PedidoDeCampanha base = pedido("Natal", 10);
            return new PedidoDeCampanha(
                    base.nome(),
                    template,
                    base.templateIdioma(),
                    base.mapeamento(),
                    base.filtro(),
                    base.limiteDiario(),
                    base.janela(),
                    base.ritmoPorMinuto(),
                    base.rampa(),
                    base.agendadaPara());
        }

        private void registrarTemplate(
                String nome,
                TemplateDoCanal.Status status,
                TemplateDoCanal.Categoria categoria,
                TemplateDoCanal.RecursosAlemDoCorpo recursos) {
            canal.registrarTemplate(
                    new TemplateDoCanal("id-" + nome, nome, IDIOMA, categoria, status, "Ola {{1}}", 1, recursos));
            templates.descartarCache();
        }
    }

    @Nested
    @DisplayName("envio de teste")
    class Teste {

        @Test
        @DisplayName("vai para um contato cadastrado, nao conta no limite, nao ocupa a politica nem muda o dono do lead")
        void teste_naoContaNoLimite() {
            Campanha campanha = criar.executar(pedido("Natal", 10));
            UUID contato = criarLead(PREFIXO + "Contato de Teste", "5561987650001");

            var resultado = teste.executar(campanha.id(), "5561987650001", true);

            assertThat(resultado.leadId()).isEqualTo(contato);
            assertThat(resultado.corpoRenderizado()).isEqualTo("Ola E220-Contato, temos novidades!");
            assertThat(saidasNaOutbox()).isEqualTo(1);
            assertThat(contar("SELECT count(*) FROM campanha_envio_dia")).isZero();
            assertThat(contar("SELECT count(*) FROM campanha_template_dia")).isZero();
            assertThat(contar("SELECT count(*) FROM campanha_template_destinatario")).isZero();
            assertThat(contar("SELECT count(*) FROM envio_proativo_reserva WHERE tipo = 'CAMPANHA'")).isZero();
            assertThat(contar(
                            "SELECT count(*) FROM mensagem_origem_automacao WHERE tipo = 'CAMPANHA' AND regra_id = ?",
                            "teste:" + campanha.id()))
                    .isEqualTo(1);
            assertThat(contar("SELECT count(*) FROM lead WHERE id = ? AND atendente_responsavel_id IS NULL", contato))
                    .isEqualTo(1);
        }

        @Test
        @DisplayName("exige a confirmacao de autorizacao, um telefone valido e um contato ja cadastrado")
        void teste_protecoes() {
            Campanha campanha = criar.executar(pedido("Natal", 10));
            criarLead(PREFIXO + "Contato de Teste", "5561987650002");

            assertThatThrownBy(() -> teste.executar(campanha.id(), "5561987650002", false))
                    .isInstanceOfSatisfying(
                            CampanhaInvalidaException.class,
                            erro -> assertThat(erro.codigo()).isEqualTo("TESTE_SEM_AUTORIZACAO"));
            assertThatThrownBy(() -> teste.executar(campanha.id(), "5561999999999", true))
                    .isInstanceOfSatisfying(
                            CampanhaInvalidaException.class,
                            erro -> assertThat(erro.codigo()).isEqualTo("CONTATO_DE_TESTE_NAO_CADASTRADO"));
            assertThatThrownBy(() -> teste.executar(campanha.id(), "12", true))
                    .isInstanceOfSatisfying(
                            CampanhaInvalidaException.class,
                            erro -> assertThat(erro.codigo()).isEqualTo("TELEFONE_INVALIDO"));
            assertThat(saidasNaOutbox()).isZero();
        }
    }

    @Nested
    @DisplayName("permissoes, RLS, configuracao e auditoria")
    class PermissoesEConfiguracao {

        @Test
        @DisplayName("atendente nao acessa campanhas; gestor e subgestor leem mas nao disparam; so o administrador age")
        void permissoesPorPapel() {
            criarLeadsElegiveis(2);
            Campanha campanha = criarEIniciar("Natal", 10);

            ApoioRls.entrarComo(idDe("ana@dev.local"), PapelUsuario.ATENDENTE);
            assertThatThrownBy(() -> consultas.listar(0, 25)).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> prever.executar(FiltroDePublico.agendaInteira()))
                    .isInstanceOf(AccessDeniedException.class);

            for (String email : List.of("gestor@dev.local", "subgestor@dev.local")) {
                ApoioRls.entrarComo(
                        idDe(email), email.startsWith("gestor") ? PapelUsuario.GESTOR : PapelUsuario.SUBGESTOR);
                assertThat(consultas.listar(0, 25).pagina().total()).as(email).isEqualTo(1);
                assertThat(consultas.obter(campanha.id()).campanha().id()).isEqualTo(campanha.id());
                assertThatThrownBy(() -> criar.executar(pedido("X", 5))).isInstanceOf(AccessDeniedException.class);
                assertThatThrownBy(() -> iniciar.executar(campanha.id())).isInstanceOf(AccessDeniedException.class);
                assertThatThrownBy(() -> controle.pausar(campanha.id())).isInstanceOf(AccessDeniedException.class);
                assertThatThrownBy(() -> controle.cancelar(campanha.id())).isInstanceOf(AccessDeniedException.class);
                assertThatThrownBy(() -> controle.alterarLimite(campanha.id(), 5, null))
                        .isInstanceOf(AccessDeniedException.class);
                assertThatThrownBy(() -> teste.executar(campanha.id(), "5561987650003", true))
                        .isInstanceOf(AccessDeniedException.class);
                assertThatThrownBy(() -> configuracao.atualizar(
                                new ConfiguracaoDeCampanhas.Atualizacao(null, 10, null, null, null, null, null)))
                        .isInstanceOf(AccessDeniedException.class);
            }

            ApoioRls.entrarComo(adminId, PapelUsuario.ADMINISTRADOR);
            assertThat(controle.pausar(campanha.id()).status()).isEqualTo(StatusDaCampanha.PAUSADA);
        }

        @Test
        @DisplayName("opt-out: a gestao registra, so o administrador desfaz")
        void optOut_permissoes() {
            UUID lead = criarLeadsElegiveis(1).get(0);

            ApoioRls.entrarComo(idDe("gestor@dev.local"), PapelUsuario.GESTOR);
            optOuts.registrar(lead, "pediu");
            assertThat(optOuts.listar(0, 25).total()).isEqualTo(1);
            assertThatThrownBy(() -> optOuts.remover(lead)).isInstanceOf(AccessDeniedException.class);

            ApoioRls.entrarComo(adminId, PapelUsuario.ADMINISTRADOR);
            assertThat(optOuts.remover(lead)).isTrue();
            assertThat(optOuts.remover(lead)).isFalse();
        }

        @Test
        @DisplayName("RLS: o banco devolve zero linhas de campanha a um atendente, mesmo consultando direto")
        void rls_atendenteNaoVeCampanha() {
            criarLeadsElegiveis(2);
            criarEIniciar("Natal", 10);
            JdbcTemplate noPoolDoChat = new JdbcTemplate(chatDataSource);

            int tabelaPeloAtendente = contarComo(
                    noPoolDoChat, idDe("ana@dev.local"), PapelUsuario.ATENDENTE, "campanha_template");
            int destinatariosPeloAtendente = contarComo(
                    noPoolDoChat, idDe("ana@dev.local"), PapelUsuario.ATENDENTE, "campanha_template_destinatario");
            int tabelaPelaGestao = contarComo(
                    noPoolDoChat, idDe("gestor@dev.local"), PapelUsuario.GESTOR, "campanha_template");

            assertThat(tabelaPeloAtendente).isZero();
            assertThat(destinatariosPeloAtendente).isZero();
            assertThat(tabelaPelaGestao).isEqualTo(1);
        }

        @Test
        @DisplayName("configuracao: altera dentro da faixa, recusa fora dela e desfaz tudo se o limite padrao passa do teto")
        void configuracao() {
            var atual = configuracao.obter();
            assertThat(atual.tetoDiarioDaInstancia()).isEqualTo(200);

            var nova = configuracao.atualizar(new ConfiguracaoDeCampanhas.Atualizacao(null, 300, 150, 1000, 25, 40, 10));
            assertThat(nova.tetoDiarioDaInstancia()).isEqualTo(300);
            assertThat(nova.limiteDiarioPadrao()).isEqualTo(150);
            assertThat(nova.limiteMetaInformado()).isEqualTo(1000);
            assertThat(nova.politicaDePausa().limiarDeFalhaPorCento()).isEqualTo(25);

            assertThatThrownBy(() -> configuracao.atualizar(
                            new ConfiguracaoDeCampanhas.Atualizacao(null, 0, null, null, null, null, null)))
                    .isInstanceOfSatisfying(
                            CampanhaInvalidaException.class,
                            erro -> assertThat(erro.codigo()).isEqualTo("CONFIGURACAO_FORA_DA_FAIXA"));
            assertThatThrownBy(() -> configuracao.atualizar(
                            new ConfiguracaoDeCampanhas.Atualizacao(null, 50, 60, null, null, null, null)))
                    .isInstanceOfSatisfying(
                            CampanhaInvalidaException.class,
                            erro -> assertThat(erro.codigo()).isEqualTo("LIMITE_PADRAO_ACIMA_DO_TETO"));
            assertThat(configuracao.obter().tetoDiarioDaInstancia()).as("rollback do conjunto").isEqualTo(300);
        }

        @Test
        @DisplayName("as acoes do administrador ficam em audit_log com o ator e a campanha")
        void auditoria() {
            criarLeadsElegiveis(2);
            Campanha campanha = criarEIniciar("Natal", 10);

            controle.pausar(campanha.id());
            controle.retomar(campanha.id());

            for (String acao : List.of("INICIAR_CAMPANHA", "PAUSAR_CAMPANHA", "RETOMAR_CAMPANHA")) {
                esperar().untilAsserted(() -> assertThat(contar(
                                "SELECT count(*) FROM audit_log WHERE acao = ? AND entidade_tipo = 'CAMPANHA'"
                                        + " AND ator_id = ? AND entidade_id = ?",
                                acao,
                                adminId,
                                campanha.id()))
                        .as(acao)
                        .isPositive());
            }
            esperar().untilAsserted(() -> assertThat(contar(
                            "SELECT count(*) FROM audit_log WHERE acao = 'CRIAR_CAMPANHA' AND ator_id = ?", adminId))
                    .isPositive());
        }

        @Test
        @DisplayName("sem a funcionalidade habilitada as campanhas nao existem")
        void funcionalidadeDesligada() {
            jdbc.update("UPDATE feature_flag SET habilitado = FALSE WHERE chave = 'campanhas'");

            assertThatThrownBy(() -> consultas.listar(0, 25)).isInstanceOf(CampanhasIndisponiveisException.class);
            assertThatThrownBy(() -> criar.executar(pedido("X", 5))).isInstanceOf(CampanhasIndisponiveisException.class);
        }

        @Test
        @DisplayName("contrato da Automacao: o n8n nao pode declarar o tipo CAMPANHA na reserva proativa")
        void n8nNaoDeclaraCampanha() {
            UUID lead = criarLeadsElegiveis(1).get(0);

            assertThatThrownBy(() -> ContextoDeServico.executarComo(
                            "teste-e220",
                            () -> reservarProativo.reservar(
                                    lead,
                                    new ReservarEnvioProativoUseCase.Pedido("CAMPANHA", null, "oc", "chave-e220", null))))
                    .hasMessageContaining("tipo desconhecido");
        }

        private UUID idDe(String email) {
            return jdbc.queryForObject("SELECT id FROM usuario WHERE email = ?", UUID.class, email);
        }

        /** Conta linhas como um papel, numa transacao do pool do chat (e la que o contexto de RLS e aplicado). */
        private int contarComo(JdbcTemplate pool, UUID usuario, PapelUsuario papel, String tabela) {
            ApoioRls.entrarComo(usuario, papel);
            try {
                Integer total = new TransactionTemplate(gerenteDoChat)
                        .execute(status -> pool.queryForObject("SELECT count(*) FROM " + tabela, Integer.class));
                return total == null ? 0 : total;
            } finally {
                ApoioRls.entrarComo(adminId, PapelUsuario.ADMINISTRADOR);
            }
        }
    }
}
