package com.synapse.crm.atendimento.infrastructure.persistencia.canal;

import java.util.UUID;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.synapse.crm.atendimento.application.canal.RoteamentoDeSaida;
import com.synapse.crm.atendimento.application.canal.SaidaSuspensaParaTransicaoException;
import com.synapse.crm.atendimento.infrastructure.canal.CanalProperties;
import com.synapse.crm.sharedkernel.persistencia.Pools;

@Repository
class RoteamentoDeSaidaJdbc implements RoteamentoDeSaida {
    private final JdbcTemplate chat;
    private final CanalProperties canal;
    private final boolean suspensas;
    RoteamentoDeSaidaJdbc(@Qualifier(Pools.CHAT_DATA_SOURCE) DataSource fonte, CanalProperties canal,
            @Value("${synapse.canal.transicao.saidas-suspensas:false}") boolean suspensas) {
        this.chat=new JdbcTemplate(fonte); this.canal=canal; this.suspensas=suspensas;
    }
    @Override public Rota resolver(UUID atendimentoId, UUID historica, String contextoExterno) {
        // Lock compartilhado somente durante a transacao local, nunca durante HTTP. Nao espera
        // manutencao exclusiva: a mensagem/transferencia faz rollback e o rascunho permanece.
        if(suspensas || !Boolean.TRUE.equals(chat.queryForObject(
                "SELECT pg_try_advisory_xact_lock_shared(hashtextextended('synapse:transicao-canal',0))",Boolean.class)))
            throw new SaidaSuspensaParaTransicaoException();
        var transicoes=chat.queryForList("SELECT id,estado,provedor_destino,identificador_destino FROM canal_transicao");
        if(!transicoes.isEmpty() && !"ATIVA".equals(transicoes.getFirst().get("estado")))
            throw new SaidaSuspensaParaTransicaoException();
        if(!transicoes.isEmpty() && (!canal.provedor().equals(transicoes.getFirst().get("provedor_destino"))
                || !canal.numeroPrincipal().equals(transicoes.getFirst().get("identificador_destino"))))
            throw new SaidaSuspensaParaTransicaoException();
        if(transicoes.isEmpty() && historica!=null) return new Rota(historica,null);
        var rotas=chat.query("""
                SELECT cc.id,t.id AS transicao_id,t.provedor_destino,t.identificador_destino
                FROM atendimento_rota_canal r JOIN atendimento a ON a.id=r.atendimento_id
                JOIN canal_transicao t ON t.id=r.transicao_id
                JOIN canal_credencial cc ON cc.id=t.destino_credencial_id
                JOIN canal c ON c.id=cc.canal_id
                WHERE a.id=? AND t.estado='ATIVA' AND c.ativo AND cc.ativo
                  AND cc.vigente_desde<=now() AND (cc.vigente_ate IS NULL OR cc.vigente_ate>now())
                """,(rs,i)-> {
                    if(!canal.provedor().equals(rs.getString("provedor_destino"))
                            || !canal.numeroPrincipal().equals(rs.getString("identificador_destino")))
                        throw new SaidaSuspensaParaTransicaoException();
                    return new Rota(rs.getObject("id",UUID.class),rs.getObject("transicao_id",UUID.class));
                },atendimentoId);
        Rota rota=rotas.isEmpty()?new Rota(historica,null):rotas.getFirst();
        if(!transicoes.isEmpty() && rotas.isEmpty() && historica!=null
                && !Boolean.TRUE.equals(chat.queryForObject("SELECT EXISTS(SELECT 1 FROM canal_transicao WHERE destino_credencial_id=?)",Boolean.class,historica)))
            throw new SaidaSuspensaParaTransicaoException();
        if(rota.transicaoId()!=null && contextoExterno!=null && !contextoExterno.isBlank()) {
            // Nao converter uma citacao Uzapi em ID Z-API. Citacao Z-API exige alias comprovado.
            boolean conhecida=Boolean.TRUE.equals(chat.queryForObject("""
                    SELECT EXISTS(SELECT 1 FROM mensagem_correlacao_canal
                    WHERE provedor=? AND instancia=? AND namespace='WHATSAPP' AND id_externo=? AND NOT conflitante)
                    """,Boolean.class,canal.provedor(),canal.numeroPrincipal(),contextoExterno));
            if(!conhecida) throw new SaidaSuspensaParaTransicaoException("REFERENCIA_DE_OUTRO_CANAL");
        }
        // Ciclos novos que o fluxo manual ainda abre sem ancora recebem somente um snapshot
        // da credencial ativa; a entidade/historico nao e reescrita.
        if(rota.credencialId()==null && "z-api".equals(canal.provedor())) {
            var ativas=chat.query("""
                    SELECT cc.id FROM canal_credencial cc JOIN canal c ON c.id=cc.canal_id
                    WHERE c.ativo AND cc.ativo AND cc.identificador_externo=? AND cc.token_ref='env://Z_API_TOKEN'
                      AND cc.vigente_desde<=now() AND cc.vigente_ate IS NULL
                    """,(rs,i)->rs.getObject(1,UUID.class),canal.numeroPrincipal());
            if(ativas.size()!=1) throw new SaidaSuspensaParaTransicaoException();
            rota=new Rota(ativas.getFirst(),null);
        }
        return rota;
    }
}
