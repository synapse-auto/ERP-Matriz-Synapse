package com.synapse.crm.atendimento.infrastructure.persistencia;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.synapse.crm.atendimento.application.MensagemRepositorio;
import com.synapse.crm.atendimento.domain.mensagem.Mensagem;
import com.synapse.crm.atendimento.domain.mensagem.StatusEntrega;
import com.synapse.crm.core.infrastructure.persistencia.TransacaoObrigatoria;
import com.synapse.crm.sharedkernel.persistencia.Pools;

/**
 * Adaptador de mensagem sobre o pool do chat. O ponto mais quente do sistema.
 *
 * <p><b>Nada aqui sabe que a tabela e particionada.</b> O {@code INSERT} nomeia a tabela pai e
 * informa {@code enviado_em}; o Postgres decide a particao. Escolher a particao na aplicacao — pelo
 * nome do mes, por exemplo — faria uma falha do job de particionamento virar erro de escrita no
 * caminho critico, que e precisamente o que a particao {@code DEFAULT} da V5 existe para evitar.
 * Aqui a mensagem do cliente nao se perde por causa de janela de particao.
 *
 * <p>Um {@code INSERT} e nada mais: sem validacao de midia, sem chamada externa, sem resumo por IA.
 * Tudo isso e reacao {@code AFTER_COMMIT} ou vai para fila.
 */
@Repository
class MensagemRepositorioJdbc implements MensagemRepositorio {

    private static final String SQL_REGISTRAR =
            """
            INSERT INTO mensagem (id, atendimento_id, remetente_tipo, remetente_id, tipo,
                                  conteudo, midia_url, midia_metadados, opcoes, status_entrega, enviado_em)
                 VALUES (?, ?, ?::remetente_tipo, ?, ?::tipo_mensagem, ?, ?, ?::jsonb, ?::jsonb,
                         ?::status_entrega, ?)
            """;

    // enviado_em no WHERE porque e a chave de particao: sem ela o PostgreSQL
    // varreria todas as particoes para achar uma unica linha.
    private static final String SQL_STATUS_ENTREGA =
            "UPDATE mensagem SET status_entrega = ?::status_entrega WHERE id = ? AND enviado_em = ?";

    /**
     * A monotonia e a mesma de {@link StatusEntrega#ehPosteriorA(StatusEntrega)}, no SQL, para duas
     * entregas concorrentes (read antes de delivered) nao se atropelarem entre o SELECT e o UPDATE.
     * O JOIN em atendimento e o que faz a RLS negar a escrita sem contexto de servico.
     */
    private static final String SQL_APLICAR_STATUS_PROVEDOR =
            """
            UPDATE mensagem m
               SET status_entrega = ?::status_entrega,
                   erro_entrega = CASE
                       WHEN ?::status_entrega = 'FALHOU'
                       THEN jsonb_strip_nulls(jsonb_build_object('codigo', ?::integer, 'titulo', ?::text))
                       ELSE m.erro_entrega
                   END
              FROM mensagem_id_externo e
              JOIN atendimento a ON a.id = e.atendimento_id
             WHERE e.wamid = ?
               AND m.id = e.mensagem_id
               AND m.enviado_em = e.mensagem_enviada_em
               AND (
                    CASE m.status_entrega
                        WHEN 'LIDO' THEN FALSE
                        WHEN 'FALHOU' THEN FALSE
                        ELSE CASE ?::status_entrega
                            WHEN m.status_entrega THEN FALSE
                            WHEN 'FALHOU' THEN m.status_entrega IN ('PENDENTE', 'ENVIADO')
                            WHEN 'LIDO' THEN m.status_entrega IN ('PENDENTE', 'ENVIADO', 'ENTREGUE')
                            WHEN 'ENTREGUE' THEN m.status_entrega IN ('PENDENTE', 'ENVIADO')
                            WHEN 'ENVIADO' THEN m.status_entrega = 'PENDENTE'
                            ELSE FALSE
                        END
                    END
               )
            RETURNING m.id, e.atendimento_id, a.lead_id, m.status_entrega
            """;

    private final JdbcTemplate chat;

    MensagemRepositorioJdbc(@Qualifier(Pools.CHAT_DATA_SOURCE) DataSource chatDataSource) {
        this.chat = new JdbcTemplate(chatDataSource);
    }

    @Override
    public Mensagem registrar(Mensagem mensagem) {
        TransacaoObrigatoria.exigir("registrar");
        chat.update(
                SQL_REGISTRAR,
                mensagem.id(),
                mensagem.atendimentoId(),
                mensagem.remetente().tipo().name(),
                mensagem.remetente().id(),
                mensagem.tipo().name(),
                mensagem.conteudo(),
                mensagem.midiaUrl(),
                mensagem.midiaMetadados(),
                mensagem.opcoes(),
                mensagem.statusEntrega().name(),
                Timestamp.from(mensagem.enviadoEm()));
        return mensagem;
    }

    /**
     * Move a mensagem no ciclo de entrega. Chamado pelo publisher da outbox depois de o provedor
     * responder — {@code PENDENTE} vira {@code ENVIADO} ou {@code FALHOU}.
     *
     * <p>{@code enviado_em} entra no {@code WHERE} porque e a chave de particao: sem ela o PostgreSQL
     * varreria todas as particoes para achar uma linha.
     */
    @Override
    public void atualizarStatusEntrega(UUID mensagemId, Instant enviadoEm, StatusEntrega status) {
        TransacaoObrigatoria.exigir("atualizarStatusEntrega");
        chat.update(SQL_STATUS_ENTREGA, status.name(), mensagemId, Timestamp.from(enviadoEm));
    }

    @Override
    public Optional<StatusDeEntregaAplicado> aplicarStatusDoProvedor(
            String wamid, StatusEntrega novo, Integer codigoErro, String tituloErro) {
        TransacaoObrigatoria.exigir("aplicarStatusDoProvedor");
        if (wamid == null || wamid.isBlank() || novo == null) {
            return Optional.empty();
        }
        List<StatusDeEntregaAplicado> aplicados = chat.query(
                SQL_APLICAR_STATUS_PROVEDOR,
                (rs, i) -> new StatusDeEntregaAplicado(
                        rs.getObject("id", UUID.class),
                        rs.getObject("atendimento_id", UUID.class),
                        rs.getObject("lead_id", UUID.class),
                        StatusEntrega.valueOf(rs.getString("status_entrega"))),
                novo.name(),
                novo.name(),
                codigoErro,
                tituloErro,
                wamid,
                novo.name());
        return aplicados.stream().findFirst();
    }
}
