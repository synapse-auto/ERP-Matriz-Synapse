package com.synapse.crm.atendimento.infrastructure.persistencia.referencia;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.synapse.crm.atendimento.application.referencia.MensagemReferenciaRepositorio;
import com.synapse.crm.atendimento.domain.mensagem.ReferenciaDeMensagem;
import com.synapse.crm.core.infrastructure.persistencia.TransacaoObrigatoria;
import com.synapse.crm.sharedkernel.persistencia.Pools;

@Repository
class MensagemReferenciaRepositorioJdbc implements MensagemReferenciaRepositorio {

    private static final String SQL_GRAVAR =
            """
            INSERT INTO mensagem_referencia (
                mensagem_id, mensagem_enviada_em, tipo,
                origem_mensagem_id, origem_enviada_em, origem_atendimento_id,
                citacao_autor, citacao_tipo, citacao_previa)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private final JdbcTemplate chat;

    MensagemReferenciaRepositorioJdbc(@Qualifier(Pools.CHAT_DATA_SOURCE) DataSource chatDataSource) {
        this.chat = new JdbcTemplate(chatDataSource);
    }

    @Override
    public void gravar(UUID mensagemId, Instant enviadoEm, ReferenciaDeMensagem referencia) {
        TransacaoObrigatoria.exigir("gravar referencia");
        chat.update(
                SQL_GRAVAR,
                mensagemId,
                Timestamp.from(enviadoEm),
                referencia.tipo().name(),
                referencia.origemMensagemId(),
                Timestamp.from(referencia.origemEnviadaEm()),
                referencia.origemAtendimentoId(),
                referencia.citacaoAutor(),
                referencia.citacaoTipo(),
                referencia.citacaoPrevia());
    }
}
