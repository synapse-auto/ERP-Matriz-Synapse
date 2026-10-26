package com.synapse.crm.atendimento.infrastructure.persistencia.canal;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.synapse.crm.atendimento.application.canal.StatusLegadoDeCanalRepositorio;
import com.synapse.crm.sharedkernel.persistencia.Pools;

@Repository
class StatusLegadoDeCanalRepositorioJdbc implements StatusLegadoDeCanalRepositorio {
    private final JdbcTemplate chat;
    StatusLegadoDeCanalRepositorioJdbc(@Qualifier(Pools.CHAT_DATA_SOURCE) DataSource fonte){chat=new JdbcTemplate(fonte);}
    @Override public boolean destinoPermitido(String id) {
        return Boolean.TRUE.equals(chat.queryForObject("SELECT EXISTS(SELECT 1 FROM canal_transicao WHERE provedor_origem='uzapi-autotic' AND identificador_origem=?)",Boolean.class,id));
    }
    @Override public boolean mensagemAnteriorAoCorte(String id) {
        return Boolean.TRUE.equals(chat.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM mensagem_id_externo x
                  JOIN mensagem m ON m.id=x.mensagem_id AND m.enviado_em=x.mensagem_enviada_em
                  JOIN atendimento a ON a.id=m.atendimento_id
                  JOIN canal_transicao t ON t.origem_credencial_id=a.canal_credencial_id
                    OR EXISTS(SELECT 1 FROM atendimento_rota_canal r WHERE r.atendimento_id=a.id AND r.transicao_id=t.id)
                WHERE x.wamid=? AND t.ativada_em IS NOT NULL AND m.enviado_em<t.ativada_em
                  AND NOT EXISTS(SELECT 1 FROM mensagem_correlacao_canal z
                    WHERE z.mensagem_id=m.id AND z.mensagem_enviada_em=m.enviado_em AND z.provedor<>'uzapi-autotic'))
                """,Boolean.class,id));
    }
}
