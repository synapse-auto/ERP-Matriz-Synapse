package com.synapse.crm.app.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import com.synapse.crm.app.PostgresIT;
import com.synapse.crm.core.application.lead.LeadRepositorio;
import com.synapse.crm.sharedkernel.identidade.ContextoDeServico;

/**
 * Contadores denormalizados do lead (RF-CRM-71).
 *
 * <p>O que precisa ser verdade: o incremento acontece <b>na transacao de quem chama</b>. Quando a
 * E04 criar o atendimento, ou os dois acontecem ou nenhum — um contador que sobrevive ao rollback
 * da criacao seria pior que nao ter contador, porque mentiria em silencio.
 */
@SpringBootTest
class ContadoresDoLeadIT extends PostgresIT {

    @Autowired
    private LeadRepositorio leads;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TransactionTemplate transacao;

    private UUID leadId;

    @BeforeEach
    void criarLead() {
        leadId = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO lead (id, nome, status_basico) VALUES (?, 'Lead de contador', 'IA')",
                leadId);
    }

    @Test
    @DisplayName("o incremento persiste quando a transacao confirma")
    void somar_transacaoConfirmada_persiste() {
        comoServico(() -> {
            leads.somarAtendimentos(leadId, 1);
            leads.somarMensagens(leadId, 3);
        });

        assertThat(contador("num_atendimentos")).isEqualTo(1);
        assertThat(contador("num_mensagens")).isEqualTo(3);
    }

    /**
     * O ponto da etapa: o contador anda junto com o que o causou. Se a transacao que criaria o
     * atendimento falhar, o lead nao pode ficar dizendo que teve um atendimento a mais.
     */
    @Test
    @DisplayName("o incremento desaparece quando a transacao falha")
    void somar_transacaoRevertida_naoPersiste() {
        assertThatThrownBy(() -> comoServico(() -> {
                    leads.somarAtendimentos(leadId, 1);
                    throw new IllegalStateException("falha depois do incremento");
                }))
                .isInstanceOf(IllegalStateException.class);

        assertThat(contador("num_atendimentos")).isZero();
    }

    /** Incremento relativo: duas somas concorrentes somam duas, nao uma. */
    @Test
    @DisplayName("somas sucessivas acumulam")
    void somar_chamadasSucessivas_acumulam() {
        comoServico(() -> leads.somarMensagens(leadId, 2));
        comoServico(() -> leads.somarMensagens(leadId, 5));

        assertThat(contador("num_mensagens")).isEqualTo(7);
    }

    private void comoServico(Runnable acao) {
        ContextoDeServico.executarComo("teste-de-contadores", () -> transacao.executeWithoutResult(
                status -> acao.run()));
    }

    private int contador(String coluna) {
        return jdbc.queryForObject("SELECT " + coluna + " FROM lead WHERE id = ?", Integer.class, leadId);
    }
}
