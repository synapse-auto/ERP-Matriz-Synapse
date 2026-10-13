package com.synapse.crm.app.atendimento;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

import com.synapse.crm.app.canal.CanalFake;
import com.synapse.crm.atendimento.infrastructure.webhook.ProcessadorDeWebhookEntrada;

/** Usa o ACL de teste do projeto; HTTP de entrada e job real, sem chamar helper interno de recebimento. */
@TestPropertySource(properties = "synapse.canal.whatsapp.provedor=fake")
class FinalizacaoEmMassaRecebimentoIT extends FinalizacaoEmMassaITBase {

    @Autowired ProcessadorDeWebhookEntrada processador;

    @AfterEach
    void limparEntradasProprias() {
        jdbc.update("DELETE FROM webhook_entrada WHERE id_externo LIKE 'FMASSA-RX-%'");
    }

    @Test
    void webhookDepoisDoPedidoRetiraConversaAtivaDoPeriodoAntigoSemPerderMensagem() {
        UUID dono = atendente("RecebimentoAtividade");
        UUID id = atendimentoAberto(dono, noDia(hoje().minusDays(1), LocalTime.of(10, 0)));
        prepararTelefone(id, "5561988807991");
        UUID operacao = idDaOperacao(criar(tokenDe(dono),
                pedido(List.of(dono), hoje().minusDays(1), hoje().minusDays(1)), chaveNova()));
        String entrada = "FMASSA-RX-" + UUID.randomUUID();
        receber(entrada, "5561988807991");
        processarAteConcluir(operacao);

        assertThat(statusDoAtendimento(id)).isEqualTo("EM_ATENDIMENTO");
        assertThat(jdbc.queryForObject("SELECT motivo FROM finalizacao_em_massa_item WHERE operacao_id=? AND atendimento_id=?",
                String.class, operacao, id)).isEqualTo("ATIVIDADE_POSTERIOR");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM mensagem WHERE atendimento_id=?", Integer.class, id)).isEqualTo(1);
        receber(entrada, "5561988807991");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM mensagem WHERE atendimento_id=?", Integer.class, id)).isEqualTo(1);
    }

    @Test
    void mensagemPosteriorAoLoteAbreOutroCicloSemDesfazerFinalizacaoEHistorico() {
        UUID dono = atendente("RecebimentoReabertura");
        UUID antigo = atendimentoAberto(dono, noDia(hoje(), LocalTime.of(8, 0)));
        prepararTelefone(antigo, "5561988807992");
        UUID lead = jdbc.queryForObject("SELECT lead_id FROM atendimento WHERE id=?", UUID.class, antigo);
        UUID operacao = idDaOperacao(criar(tokenDe(dono), pedido(List.of(dono), hoje(), hoje()), chaveNova()));
        processarAteConcluir(operacao);
        assertThat(statusDoAtendimento(antigo)).isEqualTo("FINALIZADO");
        assertThat(jdbc.queryForObject("SELECT atendente_responsavel_id FROM lead WHERE id=?", UUID.class, lead)).isNull();
        receber("FMASSA-RX-" + UUID.randomUUID(), "5561988807992");

        assertThat(statusDoAtendimento(antigo)).isEqualTo("FINALIZADO");
        assertThat(jdbc.queryForObject("SELECT atendente_id FROM atendimento WHERE id=?", UUID.class, antigo)).isEqualTo(dono);
        var novos = jdbc.queryForList("SELECT id FROM atendimento WHERE lead_id=? AND status<>'FINALIZADO'", UUID.class, lead);
        assertThat(novos).hasSize(1).doesNotContain(antigo);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM mensagem WHERE atendimento_id=?", Integer.class, novos.getFirst())).isEqualTo(1);
        assertThat(itensComStatus(operacao, "FINALIZADO")).isEqualTo(1);
    }

    private void prepararTelefone(UUID atendimento, String telefone) {
        jdbc.update("UPDATE lead SET telefone=? WHERE id=(SELECT lead_id FROM atendimento WHERE id=?)", telefone, atendimento);
    }

    private void receber(String entrada, String telefone) {
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Hub-Signature-256", CanalFake.ASSINATURA_VALIDA);
        assertThat(http.postForEntity("/webhook/canal", new HttpEntity<>(
                Map.of("id", entrada, "de", telefone, "nome", "Cliente sintético", "texto", "Mensagem de teste"), headers), String.class)
                .getStatusCode().is2xxSuccessful()).isTrue();
        processador.processarPendentes();
        assertThat(jdbc.queryForObject("SELECT processado_em IS NOT NULL FROM webhook_entrada WHERE id_externo=?",
                Boolean.class, entrada)).isTrue();
    }
}
