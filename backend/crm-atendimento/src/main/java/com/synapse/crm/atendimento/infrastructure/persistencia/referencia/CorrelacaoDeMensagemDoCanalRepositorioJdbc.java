package com.synapse.crm.atendimento.infrastructure.persistencia.referencia;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.synapse.crm.atendimento.application.MensagemRepositorio.StatusDeEntregaAplicado;
import com.synapse.crm.atendimento.application.referencia.CorrelacaoDeMensagemDoCanalRepositorio;
import com.synapse.crm.atendimento.domain.canal.IdentidadeDeMensagemDoCanal;
import com.synapse.crm.atendimento.domain.canal.TradutorDeCanal.StatusComIdentidade;
import com.synapse.crm.atendimento.domain.mensagem.StatusEntrega;
import com.synapse.crm.core.infrastructure.persistencia.TransacaoObrigatoria;
import com.synapse.crm.sharedkernel.persistencia.Pools;

@Repository
class CorrelacaoDeMensagemDoCanalRepositorioJdbc implements CorrelacaoDeMensagemDoCanalRepositorio {
    private final JdbcTemplate chat;
    CorrelacaoDeMensagemDoCanalRepositorioJdbc(@Qualifier(Pools.CHAT_DATA_SOURCE) DataSource fonte) { chat = new JdbcTemplate(fonte); }

    @Override public boolean registrar(IdentidadeDeMensagemDoCanal chave, UUID mensagemId, Instant enviadoEm, UUID atendimentoId) {
        TransacaoObrigatoria.exigir("correlacionar mensagem");
        int alterados = chat.update("""
                INSERT INTO mensagem_correlacao_canal AS c
                    (provedor, instancia, namespace, id_externo, mensagem_id, mensagem_enviada_em, atendimento_id)
                SELECT ?, ?, ?, ?, m.id, m.enviado_em, m.atendimento_id FROM mensagem m
                 WHERE m.id = ? AND m.enviado_em = ? AND m.atendimento_id = ?
                ON CONFLICT (provedor, instancia, namespace, id_externo) DO UPDATE
                   SET mensagem_id = EXCLUDED.mensagem_id, mensagem_enviada_em = EXCLUDED.mensagem_enviada_em,
                       atendimento_id = EXCLUDED.atendimento_id
                 WHERE NOT c.conflitante AND (c.mensagem_id IS NULL OR (c.mensagem_id = EXCLUDED.mensagem_id
                    AND c.mensagem_enviada_em = EXCLUDED.mensagem_enviada_em AND c.atendimento_id = EXCLUDED.atendimento_id))
                """, chave.provedor(), chave.instancia(), chave.namespace(), chave.valor(), mensagemId, Timestamp.from(enviadoEm), atendimentoId);
        if (alterados != 1) {
            // O conflito conserva o vinculo original para auditoria, mas nao permite que callbacks
            // futuros confirmem a mensagem antiga com um ID que passou a ser ambiguo.
            chat.update("""
                    UPDATE mensagem_correlacao_canal SET conflitante = TRUE
                     WHERE provedor = ? AND instancia = ? AND namespace = ? AND id_externo = ?
                    """, chave.provedor(), chave.instancia(), chave.namespace(), chave.valor());
        }
        return alterados == 1;
    }

    @Override public void observar(StatusComIdentidade observacao) {
        TransacaoObrigatoria.exigir("observar status correlacionado");
        var chave = observacao.identidade();
        // Mesmo ciclo de StatusEntrega.ehPosteriorA; falha/leitura sao terminais, sem regressao.
        chat.update("""
                INSERT INTO mensagem_correlacao_canal AS c
                    (provedor, instancia, namespace, id_externo, status_observado, ocorrido_em)
                VALUES (?, ?, ?, ?, ?::status_entrega, ?)
                ON CONFLICT (provedor, instancia, namespace, id_externo) DO UPDATE
                   SET status_observado = EXCLUDED.status_observado, ocorrido_em = EXCLUDED.ocorrido_em
                 WHERE c.status_observado IS NULL OR CASE c.status_observado
                     WHEN 'PENDENTE' THEN EXCLUDED.status_observado <> 'PENDENTE'
                     WHEN 'ENVIADO' THEN EXCLUDED.status_observado IN ('ENTREGUE', 'LIDO', 'FALHOU')
                     WHEN 'ENTREGUE' THEN EXCLUDED.status_observado = 'LIDO'
                     ELSE FALSE END
                """, chave.provedor(), chave.instancia(), chave.namespace(), chave.valor(), observacao.status().name(), Timestamp.from(observacao.ocorridoEm()));
    }

    @Override public Optional<StatusDeEntregaAplicado> aplicar(IdentidadeDeMensagemDoCanal chave) {
        TransacaoObrigatoria.exigir("aplicar status correlacionado");
        return chat.query("""
                UPDATE mensagem m SET status_entrega = c.status_observado,
                    erro_entrega = CASE WHEN c.status_observado = 'FALHOU'
                        THEN jsonb_build_object('titulo', 'ENVIO_RECUSADO_PELO_CANAL') ELSE m.erro_entrega END
                  FROM mensagem_correlacao_canal c JOIN atendimento a ON a.id = c.atendimento_id
                 WHERE c.provedor = ? AND c.instancia = ? AND c.namespace = ? AND c.id_externo = ?
                   AND NOT c.conflitante
                   AND m.id = c.mensagem_id AND m.enviado_em = c.mensagem_enviada_em
                   AND CASE m.status_entrega
                       WHEN 'PENDENTE' THEN c.status_observado IN ('ENVIADO', 'ENTREGUE', 'LIDO', 'FALHOU')
                       WHEN 'ENVIADO' THEN c.status_observado IN ('ENTREGUE', 'LIDO', 'FALHOU')
                       WHEN 'ENTREGUE' THEN c.status_observado = 'LIDO'
                       ELSE FALSE END
                RETURNING m.id, m.atendimento_id, a.lead_id, m.status_entrega
                """, (rs, i) -> new StatusDeEntregaAplicado(rs.getObject("id", UUID.class), rs.getObject("atendimento_id", UUID.class),
                    rs.getObject("lead_id", UUID.class), StatusEntrega.valueOf(rs.getString("status_entrega"))),
                chave.provedor(), chave.instancia(), chave.namespace(), chave.valor()).stream().findFirst();
    }
}
