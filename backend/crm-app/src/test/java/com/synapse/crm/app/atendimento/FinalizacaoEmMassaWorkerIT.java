package com.synapse.crm.app.atendimento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import com.synapse.crm.atendimento.application.finalizacaomassa.EntregadorDeAvisoDeFinalizacao;

/**
 * O que o worker faz com a operacao ja criada: reconferencia no momento de executar, falha parcial, retomada apos
 * queda, avisos so para quem foi afetado e sem duplicidade. O worker e os avisos rodam pelos mesmos metodos que o
 * {@code @Scheduled} chama em producao; o teste decide quando.
 */
class FinalizacaoEmMassaWorkerIT extends FinalizacaoEmMassaITBase {

    @MockitoSpyBean EntregadorDeAvisoDeFinalizacao entregador;

    // --- periodo sobre a ultima atividade ------------------------------------------------------------------

    @Test
    void periodoVaiSobreAUltimaMensagemEntaoAberturaAntigaComConversaRecenteFicaDeFora() {
        UUID clayton = atendente("Clayton");
        UUID a = atendimentoAberto(clayton, noDia(hoje().minusDays(1), LocalTime.of(10, 0)));
        mensagem(a, clayton, noDia(hoje(), LocalTime.of(8, 0)));
        String token = tokenGestor();
        LocalDate ontem = hoje().minusDays(1);

        ResponseEntity<String> pedidoDeOntem = criar(token, pedido(List.of(clayton), ontem, ontem), chaveNova());
        assertThat(pedidoDeOntem.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(codigo(pedidoDeOntem)).isEqualTo("SEM_ATENDIMENTOS");

        UUID operacao = idDaOperacao(criar(token, pedido(List.of(clayton), hoje(), hoje()), chaveNova()));
        processarAteConcluir(operacao);
        assertThat(statusDoAtendimento(a)).isEqualTo("FINALIZADO");
    }

    // --- reconferencia na execucao -------------------------------------------------------------------------

    @Test
    void oQueMudouDepoisDaConfirmacaoEIgnoradoComMotivoENaoFinalizado() {
        UUID clayton = atendente("Clayton");
        UUID nayara = atendente("Nayara");
        LocalDate dia = hoje().minusDays(1);
        UUID normal = atendimentoAberto(clayton, noDia(dia, LocalTime.of(10, 0)));
        UUID transferido = atendimentoAberto(clayton, noDia(dia, LocalTime.of(10, 5)));
        UUID comAtividadeNova = atendimentoAberto(clayton, noDia(dia, LocalTime.of(10, 10)));
        UUID jaFinalizado = atendimentoAberto(clayton, noDia(dia, LocalTime.of(10, 15)));
        String token = tokenGestor();
        UUID operacao = idDaOperacao(criar(token, pedido(List.of(clayton), dia, dia), chaveNova()));

        // Entre a confirmacao e a execucao o mundo anda.
        jdbc.update("UPDATE atendimento SET atendente_id = ? WHERE id = ?", nayara, transferido);
        mensagem(comAtividadeNova, clayton, noDia(hoje(), LocalTime.of(9, 0)));
        finalizarPorFora(jaFinalizado);
        processarAteConcluir(operacao);

        assertThat(statusDoAtendimento(normal)).isEqualTo("FINALIZADO");
        assertThat(statusDoAtendimento(transferido)).isEqualTo("EM_ATENDIMENTO");
        assertThat(statusDoAtendimento(comAtividadeNova)).isEqualTo("EM_ATENDIMENTO");
        assertThat(motivoDoItem(operacao, transferido)).isEqualTo("TRANSFERIDO");
        assertThat(motivoDoItem(operacao, comAtividadeNova)).isEqualTo("ATIVIDADE_POSTERIOR");
        assertThat(motivoDoItem(operacao, jaFinalizado)).isEqualTo("JA_FINALIZADO");
        JsonNode status = ler(consultar(token, operacao));
        assertThat(status.path("encontrados").asInt()).isEqualTo(4);
        assertThat(status.path("finalizados").asInt()).isEqualTo(1);
        assertThat(status.path("ignorados").asInt()).isEqualTo(3);
        assertThat(status.path("falhas").asInt()).isZero();
    }

    // --- falha parcial -------------------------------------------------------------------------------------

    @Test
    void falhaEmUmItemNaoDesfazOsDemaisEFicaRegistradaComMotivo() {
        UUID clayton = atendente("Clayton");
        UUID a = atendimentoAberto(clayton, noDia(hoje(), LocalTime.of(9, 0)));
        UUID quebra = atendimentoAberto(clayton, noDia(hoje(), LocalTime.of(10, 0)));
        UUID c = atendimentoAberto(clayton, noDia(hoje(), LocalTime.of(11, 0)));
        String token = tokenGestor();
        UUID operacao = idDaOperacao(criar(token, pedido(List.of(clayton), hoje(), hoje()), chaveNova()));

        jdbc.execute("CREATE OR REPLACE FUNCTION fmassa_bloqueia() RETURNS trigger AS $$ BEGIN"
                + " RAISE EXCEPTION 'falha simulada pelo teste'; END $$ LANGUAGE plpgsql");
        jdbc.execute("CREATE TRIGGER fmassa_falha BEFORE UPDATE ON atendimento FOR EACH ROW WHEN (NEW.id = '"
                + quebra
                + "'::uuid AND NEW.status = 'FINALIZADO') EXECUTE FUNCTION fmassa_bloqueia()");
        try {
            processarAteConcluir(operacao);
        } finally {
            jdbc.execute("DROP TRIGGER IF EXISTS fmassa_falha ON atendimento");
            jdbc.execute("DROP FUNCTION IF EXISTS fmassa_bloqueia()");
        }

        assertThat(statusDoAtendimento(a)).isEqualTo("FINALIZADO");
        assertThat(statusDoAtendimento(c)).isEqualTo("FINALIZADO");
        assertThat(statusDoAtendimento(quebra)).isEqualTo("EM_ATENDIMENTO");
        assertThat(motivoDoItem(operacao, quebra)).isEqualTo("ERRO_INESPERADO");
        assertThat(itensComStatus(operacao, "FALHA")).isEqualTo(1);
        JsonNode status = ler(consultar(token, operacao));
        assertThat(status.path("status").asText()).isEqualTo("CONCLUIDA");
        assertThat(status.path("finalizados").asInt()).isEqualTo(2);
        assertThat(status.path("falhas").asInt()).isEqualTo(1);

        // O erro cru do banco nunca sai pela API.
        String corpoDosItens =
                chamar(token, HttpMethod.GET, BASE + "/" + operacao + "/itens?status=FALHA", null, null).getBody();
        assertThat(corpoDosItens).doesNotContain("falha simulada pelo teste");
    }

    // --- retomada, lote e nao bloqueio ----------------------------------------------------------------------

    @Test
    void operacaoGrandeAndaEmLotesEONaoBloqueiaOPainelDeAtendimentos() {
        UUID clayton = atendente("Clayton");
        for (int i = 0; i < 7; i++) {
            atendimentoAberto(clayton, noDia(hoje(), LocalTime.of(8, i)));
        }
        definirParametro("atendimento.finalizacao_em_massa.lote", 2);
        String token = tokenGestor();
        UUID operacao = idDaOperacao(criar(token, pedido(List.of(clayton), hoje(), hoje()), chaveNova()));

        agendador.processarOperacao();

        JsonNode meio = ler(consultar(token, operacao));
        assertThat(meio.path("status").asText()).isEqualTo("EM_ANDAMENTO");
        assertThat(meio.path("finalizados").asInt()).isBetween(1, 2);
        assertThat(meio.path("percentual").asInt()).isBetween(1, 99);
        // Com a operacao no meio, o painel responde normalmente.
        assertThat(chamar(token, HttpMethod.GET, "/api/v1/atendimentos/contagem", null, null).getStatusCode())
                .isEqualTo(HttpStatus.OK);

        processarAteConcluir(operacao);
        assertThat(ler(consultar(token, operacao)).path("finalizados").asInt()).isEqualTo(7);
    }

    @Test
    void aposQuedaDoWorkerALeaseVenceEOutroAssumeSemFinalizarDuasVezes() {
        UUID clayton = atendente("Clayton");
        for (int i = 0; i < 5; i++) {
            atendimentoAberto(clayton, noDia(hoje(), LocalTime.of(8, i)));
        }
        definirParametro("atendimento.finalizacao_em_massa.lote", 2);
        String token = tokenGestor();
        UUID operacao = idDaOperacao(criar(token, pedido(List.of(clayton), hoje(), hoje()), chaveNova()));
        agendador.processarOperacao();
        // O no que reivindicou "morreu": a lease fica vencida e o status continua EM_ANDAMENTO.
        jdbc.update("UPDATE finalizacao_em_massa SET lease_ate = now() - interval '1 minute' WHERE id = ?", operacao);

        processarAteConcluir(operacao);

        assertThat(itensComStatus(operacao, "FINALIZADO")).isEqualTo(5);
        assertThat(itensComStatus(operacao, "PENDENTE")).isZero();
        assertThat(ler(consultar(token, operacao)).path("finalizados").asInt()).isEqualTo(5);
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM atendimento WHERE status = 'FINALIZADO'::status_atendimento"
                                + " AND lead_id IN (SELECT id FROM lead WHERE nome LIKE ?)",
                        Integer.class,
                        PREFIXO + "%"))
                .isEqualTo(5);
    }

    @Test
    void rodarOWorkerDepoisDeConcluirNaoMudaNada() {
        UUID clayton = atendente("Clayton");
        atendimentoAberto(clayton, noDia(hoje(), LocalTime.of(10, 0)));
        String token = tokenGestor();
        UUID operacao = idDaOperacao(criar(token, pedido(List.of(clayton), hoje(), hoje()), chaveNova()));
        processarAteConcluir(operacao);
        JsonNode antes = ler(consultar(token, operacao));

        agendador.processarOperacao();
        agendador.processarOperacao();

        assertThat(ler(consultar(token, operacao))).isEqualTo(antes);
        assertThat(contarAvisos(operacao)).isEqualTo(1);
    }

    // --- avisos ---------------------------------------------------------------------------------------------

    @Test
    void soQuemTeveAtendimentoFinalizadoRecebeAvisoEUmaVezSo() {
        UUID clayton = atendente("Clayton");
        UUID nayara = atendente("Nayara");
        UUID semResultado = atendente("Carlos");
        atendimentoAberto(clayton, noDia(hoje(), LocalTime.of(9, 0)));
        atendimentoAberto(clayton, noDia(hoje(), LocalTime.of(10, 0)));
        atendimentoAberto(nayara, noDia(hoje(), LocalTime.of(11, 0)));
        String token = tokenGestor();
        UUID gestor = usuario("gestor@dev.local");
        UUID operacao =
                idDaOperacao(criar(token, pedido(List.of(clayton, nayara, semResultado), hoje(), hoje()), chaveNova()));
        processarAteConcluir(operacao);

        assertThat(usuariosAvisados(operacao)).containsExactlyInAnyOrder(clayton, nayara);
        assertThat(usuariosAvisados(operacao)).doesNotContain(semResultado, gestor);

        agendador.publicarAvisos();
        agendador.publicarAvisos();

        verify(entregador, times(2)).entregar(any());
        verify(entregador).entregar(argThat(a -> a.usuarioId().equals(clayton)
                && a.finalizadosDoUsuario() == 2
                && a.totalFinalizados() == 3
                && !a.parcial()
                && a.afetados().size() == 2));
        verify(entregador).entregar(argThat(a -> a.usuarioId().equals(nayara) && a.finalizadosDoUsuario() == 1));
        assertThat(estadoDoAviso(operacao, clayton)).isEqualTo("ENVIADO");
    }

    @Test
    void operacaoSemNinguemAfetadoNaoGeraAviso() {
        UUID clayton = atendente("Clayton");
        UUID a = atendimentoAberto(clayton, noDia(hoje(), LocalTime.of(10, 0)));
        String token = tokenGestor();
        UUID operacao = idDaOperacao(criar(token, pedido(List.of(clayton), hoje(), hoje()), chaveNova()));
        finalizarPorFora(a);

        processarAteConcluir(operacao);
        agendador.publicarAvisos();

        assertThat(contarAvisos(operacao)).isZero();
        verifyNoInteractions(entregador);
        assertThat(ler(consultar(token, operacao)).path("ignorados").asInt()).isEqualTo(1);
    }

    @Test
    void falhaNaEntregaReagendaEAoVoltarEntregaUmaUnicaVez() {
        UUID clayton = atendente("Clayton");
        atendimentoAberto(clayton, noDia(hoje(), LocalTime.of(10, 0)));
        String token = tokenGestor();
        UUID operacao = idDaOperacao(criar(token, pedido(List.of(clayton), hoje(), hoje()), chaveNova()));
        processarAteConcluir(operacao);
        doThrow(new IllegalStateException("redis fora")).doCallRealMethod().when(entregador).entregar(any());

        agendador.publicarAvisos();

        assertThat(estadoDoAviso(operacao, clayton)).isEqualTo("PENDENTE");
        assertThat(tentativasDoAviso(operacao, clayton)).isEqualTo(1);
        // Ainda no backoff: outra rodada nao insiste.
        agendador.publicarAvisos();
        verify(entregador, times(1)).entregar(any());

        jdbc.update("UPDATE finalizacao_em_massa_aviso SET tentar_apos = now() - interval '1 second'");
        agendador.publicarAvisos();
        agendador.publicarAvisos();

        verify(entregador, times(2)).entregar(any());
        assertThat(estadoDoAviso(operacao, clayton)).isEqualTo("ENVIADO");
    }

    @Test
    void depoisDoMaximoDeTentativasOAvisoEsgotaEPara() {
        UUID clayton = atendente("Clayton");
        atendimentoAberto(clayton, noDia(hoje(), LocalTime.of(10, 0)));
        String token = tokenGestor();
        UUID operacao = idDaOperacao(criar(token, pedido(List.of(clayton), hoje(), hoje()), chaveNova()));
        processarAteConcluir(operacao);
        doThrow(new IllegalStateException("redis fora")).when(entregador).entregar(any());

        List<String> estados = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            jdbc.update("UPDATE finalizacao_em_massa_aviso SET tentar_apos = now() - interval '1 second'");
            agendador.publicarAvisos();
            estados.add(estadoDoAviso(operacao, clayton));
        }

        assertThat(estados).contains("ESGOTADO");
        assertThat(estadoDoAviso(operacao, clayton)).isEqualTo("ESGOTADO");
        // Esgotado nao e reenviado: o total de tentativas para no maximo.
        verify(entregador, times(5)).entregar(any());
    }

    // --- auditoria e preservacao ----------------------------------------------------------------------------

    @Test
    void auditaQuemPediuEOResultadoENaoApagaLeadsMensagensNemHistorico() {
        UUID clayton = atendente("Clayton");
        UUID a = atendimentoAberto(clayton, noDia(hoje(), LocalTime.of(10, 0)));
        mensagem(a, clayton, noDia(hoje(), LocalTime.of(10, 30)));
        int mensagensAntes = mensagensDeTeste();
        int leadsAntes = leadsDeTeste();
        String token = tokenGestor();
        UUID gestor = usuario("gestor@dev.local");
        UUID operacao = idDaOperacao(criar(token, pedido(List.of(clayton), hoje(), hoje()), chaveNova()));
        processarAteConcluir(operacao);

        assertThat(jdbc.queryForObject("SELECT solicitante_id FROM finalizacao_em_massa WHERE id = ?", UUID.class, operacao))
                .isEqualTo(gestor);
        assertThat(acoesAuditadas())
                .containsExactlyInAnyOrder("FINALIZAR_ATENDIMENTOS_EM_MASSA", "CONCLUIR_FINALIZACAO_EM_MASSA");
        assertThat(mensagensDeTeste()).isEqualTo(mensagensAntes);
        assertThat(leadsDeTeste()).isEqualTo(leadsAntes);
        assertThat(statusDoAtendimento(a)).isEqualTo("FINALIZADO");
    }

    // --- apoio ----------------------------------------------------------------------------------------------

    private void finalizarPorFora(UUID atendimento) {
        jdbc.update(
                "UPDATE atendimento SET status = 'FINALIZADO'::status_atendimento, finalizado_em = now() WHERE id = ?",
                atendimento);
    }

    private String motivoDoItem(UUID operacao, UUID atendimento) {
        return jdbc.queryForObject(
                "SELECT motivo FROM finalizacao_em_massa_item WHERE operacao_id = ? AND atendimento_id = ?",
                String.class,
                operacao,
                atendimento);
    }

    private int contarAvisos(UUID operacao) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM finalizacao_em_massa_aviso WHERE operacao_id = ?", Integer.class, operacao);
    }

    private List<UUID> usuariosAvisados(UUID operacao) {
        return jdbc.queryForList(
                "SELECT usuario_id FROM finalizacao_em_massa_aviso WHERE operacao_id = ?", UUID.class, operacao);
    }

    private String estadoDoAviso(UUID operacao, UUID usuario) {
        return jdbc.queryForObject(
                "SELECT estado FROM finalizacao_em_massa_aviso WHERE operacao_id = ? AND usuario_id = ?",
                String.class,
                operacao,
                usuario);
    }

    private int tentativasDoAviso(UUID operacao, UUID usuario) {
        return jdbc.queryForObject(
                "SELECT tentativas FROM finalizacao_em_massa_aviso WHERE operacao_id = ? AND usuario_id = ?",
                Integer.class,
                operacao,
                usuario);
    }

    private int leadsDeTeste() {
        return jdbc.queryForObject("SELECT count(*) FROM lead WHERE nome LIKE ?", Integer.class, PREFIXO + "%");
    }

    private int mensagensDeTeste() {
        return jdbc.queryForObject(
                "SELECT count(*) FROM mensagem WHERE atendimento_id IN (SELECT a.id FROM atendimento a"
                        + " JOIN lead l ON l.id = a.lead_id WHERE l.nome LIKE ?)",
                Integer.class,
                PREFIXO + "%");
    }

    private List<String> acoesAuditadas() {
        return jdbc.queryForList(
                "SELECT acao FROM audit_log WHERE entidade_tipo = 'FINALIZACAO_EM_MASSA'", String.class);
    }
}
