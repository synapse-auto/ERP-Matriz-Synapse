package com.synapse.crm.core.infrastructure.persistencia.lead;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.synapse.crm.core.application.lead.LeadNoCaminhoDeMensagem;
import com.synapse.crm.core.domain.lead.StatusBasicoLead;
import com.synapse.crm.sharedkernel.persistencia.Pools;

/**
 * Escritas em {@code lead} feitas pelo caminho critico, na conexao do chat.
 *
 * <p>JDBC e nao JPA, de proposito e por dois motivos. O primeiro e transacional: o
 * {@code JdbcTemplate} construido sobre o {@code chatDataSource} pega, dentro de uma transacao do
 * {@code chatTransactionManager}, <b>a mesma conexao</b> que grava a mensagem — que e a unica forma
 * de contador e mensagem serem atomicos. O segundo e de custo: um {@code UPDATE} de tres colunas nao
 * precisa carregar a entidade inteira para o contexto de persistencia no caminho que nao pode ficar
 * lento.
 *
 * <p>Nenhuma consulta aqui filtra por dono no {@code WHERE}. Nao precisa: a politica RLS de
 * {@code lead} ja recorta, e o aplicador de contexto publica {@code app.usuario_id} e
 * {@code app.papel} no inicio da transacao — inclusive nas do pool do chat. Um atendente que tente
 * alcancar lead de colega simplesmente atualiza zero linhas.
 */
@Repository
class LeadNoCaminhoDeMensagemJdbc implements LeadNoCaminhoDeMensagem {

    /**
     * {@code GREATEST} para o instante nunca andar para tras.
     *
     * <p>Mensagem chega fora de ordem — reentrega do provedor, replay de webhook, relogio do
     * remetente. Sem isso, uma mensagem antiga reprocessada empurraria {@code ultima_interacao_em}
     * para o passado e o lead reapareceria no filtro de reativacao, que e exatamente a mentira que a
     * coluna existe para acabar.
     */
    private static final String SQL_INTERACAO =
            """
            UPDATE lead
               SET num_atendimentos    = num_atendimentos + ?,
                   num_mensagens       = num_mensagens + ?,
                   ultima_interacao_em = GREATEST(COALESCE(ultima_interacao_em, ?), ?)
             WHERE id = ?
            """;

    // FOR UPDATE porque o dono lido aqui vira o "de quem para quem" da timeline e a
    // condicao da transferencia logo abaixo. Sem o lock, duas mensagens simultaneas no
    // mesmo lead registrariam dois donos anteriores diferentes para uma transferencia so.
    private static final String SQL_DONO_ATUAL =
            "SELECT atendente_responsavel_id FROM lead WHERE id = ? FOR UPDATE";

    private static final String SQL_TRANSFERIR =
            """
            UPDATE lead
               SET atendente_responsavel_id = ?,
                   status_basico            = 'EM_ATENDIMENTO'
             WHERE id = ?
            """;

    private static final String SQL_STATUS =
            "UPDATE lead SET status_basico = ?::status_basico_lead WHERE id = ?";

    private static final String SQL_ALCANCAVEL = "SELECT 1 FROM lead WHERE id = ?";

    private final JdbcTemplate chat;

    LeadNoCaminhoDeMensagemJdbc(@Qualifier(Pools.CHAT_DATA_SOURCE) DataSource chatDataSource) {
        this.chat = new JdbcTemplate(chatDataSource);
    }

    @Override
    public void registrarInteracao(
            UUID leadId, Instant quando, int atendimentosASomar, int mensagensASomar) {
        Timestamp instante = Timestamp.from(quando);
        chat.update(SQL_INTERACAO, atendimentosASomar, mensagensASomar, instante, instante, leadId);
    }

    @Override
    public Transferencia transferirPara(UUID leadId, UUID novoAtendenteId) {
        List<UUID> donos =
                chat.query(SQL_DONO_ATUAL, (linha, indice) -> linha.getObject(1, UUID.class), leadId);

        // Zero linhas nao significa "lead inexistente": significa "nao alcancavel por
        // quem pediu". Os dois casos respondem igual de proposito — distinguir contaria
        // ao atendente que o lead existe e esta com um colega.
        if (donos.isEmpty()) {
            return Transferencia.naoAlcancado();
        }

        UUID donoAnterior = donos.get(0);
        if (novoAtendenteId.equals(donoAnterior)) {
            // Ja e dele. Evita UPDATE inutil e evento de transferencia sem transferencia.
            return Transferencia.de(donoAnterior);
        }

        chat.update(SQL_TRANSFERIR, novoAtendenteId, leadId);
        return Transferencia.de(donoAnterior);
    }

    @Override
    public void marcarStatus(UUID leadId, StatusBasicoLead status) {
        chat.update(SQL_STATUS, status.name(), leadId);
    }

    @Override
    public boolean alcancavel(UUID leadId) {
        return !chat.queryForList(SQL_ALCANCAVEL, Integer.class, leadId).isEmpty();
    }
}
