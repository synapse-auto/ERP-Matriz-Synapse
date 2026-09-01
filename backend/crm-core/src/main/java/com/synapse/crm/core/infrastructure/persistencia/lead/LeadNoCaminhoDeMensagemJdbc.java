package com.synapse.crm.core.infrastructure.persistencia.lead;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.synapse.crm.core.application.lead.LeadNoCaminhoDeMensagem;
import com.synapse.crm.core.domain.lead.StatusBasicoLead;
import com.synapse.crm.core.domain.lead.TelefoneCanonico;
import com.synapse.crm.core.infrastructure.persistencia.TransacaoObrigatoria;
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

    /**
     * So mensagem do cliente. {@code GREATEST} pelos mesmos motivos de {@link #SQL_INTERACAO}:
     * reentrega fora de ordem nao pode empurrar a janela para o passado.
     */
    private static final String SQL_MENSAGEM_DO_LEAD =
            """
            UPDATE lead
               SET ultima_mensagem_do_lead_em =
                   GREATEST(COALESCE(ultima_mensagem_do_lead_em, ?), ?)
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

    private static final String SQL_CONTATO =
            "SELECT telefone, ultima_mensagem_do_lead_em FROM lead WHERE id = ?";

    private static final String SQL_NOME = "SELECT nome FROM lead WHERE id = ?";

    private static final String SQL_POR_TELEFONE =
            "SELECT id FROM lead WHERE telefone = ? ORDER BY criado_em LIMIT 1";

    // Nasce sem responsavel e em IA: grupo "Potenciais", visivel a todos ate alguem
    // responder e assumir pela RN-CRM-06.
    private static final String SQL_CRIAR_POR_TELEFONE =
            "INSERT INTO lead (id, nome, telefone, status_basico) VALUES (?, ?, ?, 'IA')";

    private static final String SQL_CRIAR_PARA_ATENDENTE =
            """
            INSERT INTO lead (id, nome, telefone, status_basico, atendente_responsavel_id, canal_origem_id)
            VALUES (?, ?, ?, 'EM_ATENDIMENTO', ?, ?)
            """;

    private final JdbcTemplate chat;
    private final TelefoneCanonico telefoneCanonico;

    LeadNoCaminhoDeMensagemJdbc(
            @Qualifier(Pools.CHAT_DATA_SOURCE) DataSource chatDataSource,
            TelefoneCanonico telefoneCanonico) {
        this.chat = new JdbcTemplate(chatDataSource);
        this.telefoneCanonico = telefoneCanonico;
    }

    @Override
    public void registrarInteracao(
            UUID leadId, Instant quando, int atendimentosASomar, int mensagensASomar) {
        TransacaoObrigatoria.exigir("registrarInteracao");
        Timestamp instante = Timestamp.from(quando);
        chat.update(SQL_INTERACAO, atendimentosASomar, mensagensASomar, instante, instante, leadId);
    }

    @Override
    public void registrarMensagemDoLead(UUID leadId, Instant quando) {
        TransacaoObrigatoria.exigir("registrarMensagemDoLead");
        Timestamp instante = Timestamp.from(quando);
        chat.update(SQL_MENSAGEM_DO_LEAD, instante, instante, leadId);
    }

    @Override
    public Transferencia transferirPara(UUID leadId, UUID novoAtendenteId) {
        TransacaoObrigatoria.exigir("transferirPara");
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
        TransacaoObrigatoria.exigir("marcarStatus");
        chat.update(SQL_STATUS, status.name(), leadId);
    }

    @Override
    public boolean bloquearParaAtendimento(UUID leadId) {
        TransacaoObrigatoria.exigir("bloquearParaAtendimento");
        return !chat.queryForList("SELECT id FROM lead WHERE id = ? FOR UPDATE", UUID.class, leadId)
                .isEmpty();
    }

    @Override
    public boolean alcancavel(UUID leadId) {
        TransacaoObrigatoria.exigir("alcancavel");
        return !chat.queryForList(SQL_ALCANCAVEL, Integer.class, leadId).isEmpty();
    }

    @Override
    public UUID resolverPorTelefone(String telefone, String nomeSugerido) {
        TransacaoObrigatoria.exigir("resolverPorTelefone");
        String telefoneCanonico = this.telefoneCanonico.normalizar(telefone);
        if (telefoneCanonico == null) {
            throw new IllegalArgumentException("telefone e obrigatorio para resolver o lead");
        }

        List<UUID> existentes = chat.query(
                SQL_POR_TELEFONE,
                (linha, indice) -> linha.getObject(1, UUID.class),
                telefoneCanonico);
        if (!existentes.isEmpty()) {
            return existentes.get(0);
        }

        UUID novo = UUID.randomUUID();
        String nome =
                (nomeSugerido == null || nomeSugerido.isBlank())
                        ? telefoneCanonico
                        : nomeSugerido;
        chat.update(SQL_CRIAR_POR_TELEFONE, novo, nome, telefoneCanonico);
        return novo;
    }

    @Override
    public Optional<UUID> visivelPorTelefone(String telefone) {
        TransacaoObrigatoria.exigir("visivelPorTelefone");
        String telefoneCanonico = this.telefoneCanonico.normalizar(telefone);
        if (telefoneCanonico == null) {
            throw new IllegalArgumentException("telefone e obrigatorio para resolver o lead");
        }
        return chat
                .query(
                        SQL_POR_TELEFONE,
                        (linha, indice) -> linha.getObject(1, UUID.class),
                        telefoneCanonico)
                .stream()
                .findFirst();
    }

    @Override
    public Optional<UUID> criarParaAtendente(
            String nome, String telefone, UUID atendenteId, UUID canalOrigemId) {
        TransacaoObrigatoria.exigir("criarParaAtendente");
        String telefoneCanonico = this.telefoneCanonico.normalizar(telefone);
        if (telefoneCanonico == null) {
            throw new IllegalArgumentException("telefone e obrigatorio para resolver o lead");
        }
        UUID novo = UUID.randomUUID();
        try {
            chat.update(SQL_CRIAR_PARA_ATENDENTE, novo, nome, telefoneCanonico, atendenteId, canalOrigemId);
            return Optional.of(novo);
        } catch (DuplicateKeyException e) {
            return Optional.empty();
        }
    }

    /**
     * Telefone e janela, numa consulta so.
     *
     * <p>A janela le {@code ultima_mensagem_do_lead_em} (E121), nao {@code ultima_interacao_em}.
     * Aquele segundo campo avanca tambem em saida e alimenta {@code semRetornoDias}; usa-lo aqui
     * deixaria a janela aberta enquanto a equipe falasse.
     */
    @Override
    public Optional<ContatoParaEnvio> contatoParaEnvio(UUID leadId) {
        TransacaoObrigatoria.exigir("contatoParaEnvio");
        return chat
                .query(
                        SQL_CONTATO,
                        (linha, indice) -> new ContatoParaEnvio(
                                linha.getString("telefone"),
                                Optional.ofNullable(linha.getTimestamp("ultima_mensagem_do_lead_em"))
                                        .map(Timestamp::toInstant)),
                        leadId)
                .stream()
                .findFirst();
    }

    @Override
    public Optional<String> nomeParaTempoReal(UUID leadId) {
        TransacaoObrigatoria.exigir("nomeParaTempoReal");
        return chat.query(
                        SQL_NOME,
                        (linha, indice) -> linha.getString("nome"),
                        leadId)
                .stream()
                .findFirst();
    }
}
