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

import com.synapse.crm.atendimento.application.participacao.*;
import com.synapse.crm.core.infrastructure.persistencia.TransacaoObrigatoria;
import com.synapse.crm.sharedkernel.persistencia.Pools;

@Repository
class ParticipacaoAtendimentoRepositorioJdbc implements ParticipacaoAtendimentoRepositorio {
    private final JdbcTemplate jdbc;
    ParticipacaoAtendimentoRepositorioJdbc(@Qualifier(Pools.CHAT_DATA_SOURCE) DataSource ds) { jdbc = new JdbcTemplate(ds); }
    public Optional<UUID> solicitar(UUID a, UUID u) { TransacaoObrigatoria.exigir("solicitarEntrada");
        List<UUID> ids = jdbc.query("SELECT app_registrar_pedido_entrada(?, ?)", (r,i)->r.getObject(1, UUID.class), a,u);
        return ids.isEmpty() || ids.get(0)==null ? Optional.empty() : Optional.of(ids.get(0)); }
    public Optional<UUID> leadId(UUID a) { return jdbc.query("SELECT app_lead_do_atendimento(?)",(r,i)->r.getObject(1,UUID.class),a).stream().findFirst(); }
    public Optional<UUID> atendimentoAbertoDoLead(UUID lead) { return jdbc.query("SELECT app_atendimento_aberto_do_lead(?)",(r,i)->r.getObject(1,UUID.class),lead).stream().findFirst(); }
    public Optional<UUID> donoId(UUID a) { return jdbc.query("SELECT app_dono_do_atendimento(?)",(r,i)->r.getObject(1,UUID.class),a).stream().findFirst(); }
    public java.time.Duration validadeConfigurada() { Integer minutos=jdbc.query("SELECT valor::int FROM configuracao_automacao WHERE chave='atendimento.pedido-entrada-expiracao-minutos'",(r,i)->r.getInt(1)).stream().findFirst().orElse(30); return java.time.Duration.ofMinutes(minutos); }
    public Optional<PedidoEntradaAtendimento> pedido(UUID id) { return jdbc.query("SELECT p.*, u.nome FROM pedido_entrada_atendimento p JOIN usuario u ON u.id=p.solicitante_id WHERE p.id=?", (r,i)->new PedidoEntradaAtendimento(r.getObject("id",UUID.class),r.getObject("atendimento_id",UUID.class),r.getObject("solicitante_id",UUID.class),r.getString("nome"),StatusPedidoEntrada.valueOf(r.getString("status")),r.getTimestamp("solicitado_em").toInstant()), id).stream().findFirst(); }
    public Optional<PedidoEntradaAtendimento> pedidoDoSolicitante(UUID atendimentoId, UUID solicitanteId, Instant limite) {
        return jdbc.query("SELECT p.*, u.nome FROM pedido_entrada_atendimento p JOIN usuario u ON u.id=p.solicitante_id WHERE p.atendimento_id=? AND p.solicitante_id=? ORDER BY p.solicitado_em DESC LIMIT 1", (r,i)-> {
            StatusPedidoEntrada status=StatusPedidoEntrada.valueOf(r.getString("status"));
            if (status == StatusPedidoEntrada.PENDENTE && r.getTimestamp("solicitado_em").toInstant().isBefore(limite)) status=StatusPedidoEntrada.EXPIRADO;
            return new PedidoEntradaAtendimento(r.getObject("id",UUID.class),r.getObject("atendimento_id",UUID.class),r.getObject("solicitante_id",UUID.class),r.getString("nome"),status,r.getTimestamp("solicitado_em").toInstant());
        }, atendimentoId, solicitanteId).stream().findFirst();
    }
    public List<PedidoEntradaAtendimento> pendentes(UUID a, Instant limite) { return jdbc.query("SELECT p.*, u.nome FROM pedido_entrada_atendimento p JOIN usuario u ON u.id=p.solicitante_id WHERE p.atendimento_id=? AND p.status='PENDENTE' AND p.solicitado_em > ?", (r,i)->new PedidoEntradaAtendimento(r.getObject("id",UUID.class),r.getObject("atendimento_id",UUID.class),r.getObject("solicitante_id",UUID.class),r.getString("nome"),StatusPedidoEntrada.PENDENTE,r.getTimestamp("solicitado_em").toInstant()), a, Timestamp.from(limite)); }
    public boolean eDono(UUID a, UUID u) { return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM atendimento WHERE id=? AND atendente_id=?)", Boolean.class,a,u)); }
    public boolean eParticipanteAtivo(UUID a, UUID u) { return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM atendimento_participante WHERE atendimento_id=? AND usuario_id=? AND saiu_em IS NULL)",Boolean.class,a,u)); }
    public void aprovar(UUID p, UUID dono, Instant agora) { int n=jdbc.update("UPDATE pedido_entrada_atendimento p SET status='APROVADO',respondido_em=?,respondido_por_id=? WHERE p.id=? AND p.status='PENDENTE' AND EXISTS(SELECT 1 FROM atendimento a WHERE a.id=p.atendimento_id AND a.atendente_id=? AND a.status<>'FINALIZADO')",Timestamp.from(agora),dono,p,dono); if(n==0) throw new IllegalStateException("pedido expirado, recusado ou atendimento indisponivel"); jdbc.update("INSERT INTO atendimento_participante(atendimento_id,usuario_id) SELECT atendimento_id,solicitante_id FROM pedido_entrada_atendimento WHERE id=? ON CONFLICT DO NOTHING",p); }
    public void recusar(UUID p, UUID dono, Instant agora) { int n=jdbc.update("UPDATE pedido_entrada_atendimento p SET status='RECUSADO',respondido_em=?,respondido_por_id=? WHERE p.id=? AND p.status='PENDENTE' AND EXISTS(SELECT 1 FROM atendimento a WHERE a.id=p.atendimento_id AND a.atendente_id=?)",Timestamp.from(agora),dono,p,dono); if(n==0) throw new IllegalStateException("pedido indisponivel"); }
    public void entrar(UUID a, UUID u, Instant agora) { jdbc.update("INSERT INTO atendimento_participante(atendimento_id,usuario_id,entrou_em) VALUES(?,?,?) ON CONFLICT DO NOTHING",a,u,Timestamp.from(agora)); }
    public void sair(UUID a, UUID u, Instant agora) { jdbc.update("UPDATE atendimento_participante SET saiu_em=? WHERE atendimento_id=? AND usuario_id=? AND saiu_em IS NULL",Timestamp.from(agora),a,u); }
    public List<ParticipanteAtendimento> ativos(UUID a) { return jdbc.query("SELECT p.usuario_id,u.nome,p.entrou_em, CASE WHEN u.foto_referencia IS NOT NULL THEN '/api/v1/me/foto/' || u.id::text END AS foto_url FROM atendimento_participante p JOIN usuario u ON u.id=p.usuario_id WHERE p.atendimento_id=? AND p.saiu_em IS NULL ORDER BY p.entrou_em",(r,i)->new ParticipanteAtendimento(r.getObject(1,UUID.class),r.getString(2),r.getTimestamp(3).toInstant(),r.getString("foto_url")),a); }
}
