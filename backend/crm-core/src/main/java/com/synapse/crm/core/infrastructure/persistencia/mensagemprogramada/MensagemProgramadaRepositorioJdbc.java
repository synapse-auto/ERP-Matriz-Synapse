package com.synapse.crm.core.infrastructure.persistencia.mensagemprogramada;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.synapse.crm.core.application.mensagemprogramada.FiltroMensagensProgramadas;
import com.synapse.crm.core.application.mensagemprogramada.MensagemProgramadaRepositorio;
import com.synapse.crm.core.application.mensagemprogramada.PaginaMensagensProgramadas;
import com.synapse.crm.core.domain.mensagemprogramada.MensagemProgramada;
import com.synapse.crm.core.domain.mensagemprogramada.StatusMensagemProgramada;
import com.synapse.crm.core.infrastructure.persistencia.TransacaoObrigatoria;

@Repository
class MensagemProgramadaRepositorioJdbc implements MensagemProgramadaRepositorio {
    private final JdbcTemplate jdbc;
    MensagemProgramadaRepositorioJdbc(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override public PaginaMensagensProgramadas listar(FiltroMensagensProgramadas filtro) {
        TransacaoObrigatoria.exigir("listar mensagens programadas");
        StringBuilder sql = new StringBuilder("""
                SELECT m.id,m.lead_id,l.nome lead_nome,m.atendente_id,u.nome atendente_nome,
                       m.conteudo,m.data_envio,m.status::text
                  FROM mensagem_programada m JOIN lead l ON l.id=m.lead_id JOIN usuario u ON u.id=m.atendente_id
                 WHERE TRUE
                """);
        List<Object> p = new ArrayList<>();
        if (filtro.inicio()!=null) { sql.append(" AND m.data_envio>=?"); p.add(Timestamp.from(filtro.inicio())); }
        if (filtro.fim()!=null) { sql.append(" AND m.data_envio<=?"); p.add(Timestamp.from(filtro.fim())); }
        if (filtro.status()!=null) { sql.append(" AND m.status=CAST(? AS status_msg_prog)"); p.add(filtro.status().name()); }
        sql.append(" ORDER BY m.data_envio,m.id LIMIT ? OFFSET ?");
        p.add(filtro.tamanho()+1); p.add(Math.multiplyExact(filtro.pagina(), filtro.tamanho()));
        List<MensagemProgramada> itens=jdbc.query(sql.toString(), MensagemProgramadaRepositorioJdbc::mapear,p.toArray());
        boolean mais=itens.size()>filtro.tamanho();
        if(mais) itens=new ArrayList<>(itens.subList(0,filtro.tamanho()));
        return new PaginaMensagensProgramadas(itens,filtro.pagina(),mais);
    }

    @Override public MensagemProgramada criar(UUID leadId, UUID atendenteId, String conteudo, Instant dataEnvio) {
        TransacaoObrigatoria.exigir("criar mensagem programada"); UUID id=UUID.randomUUID();
        jdbc.update("INSERT INTO mensagem_programada(id,lead_id,atendente_id,conteudo,data_envio,status) VALUES(?,?,?,?,?,'AGENDADA')",
                id,leadId,atendenteId,conteudo,Timestamp.from(dataEnvio)); return porIdVisivel(id).orElseThrow();
    }
    @Override public Optional<MensagemProgramada> porIdVisivel(UUID id) { return jdbc.query(BASE+" WHERE m.id=?",MensagemProgramadaRepositorioJdbc::mapear,id).stream().findFirst(); }
    @Override public Optional<MensagemProgramada> atualizarAgendada(UUID id,String conteudo,Instant dataEnvio) {
        TransacaoObrigatoria.exigir("atualizar mensagem programada");
        int n=jdbc.update("UPDATE mensagem_programada SET conteudo=?,data_envio=? WHERE id=? AND status='AGENDADA'",conteudo,Timestamp.from(dataEnvio),id);
        return n==0?Optional.empty():porIdVisivel(id);
    }
    @Override public Optional<MensagemProgramada> cancelarAgendada(UUID id) {
        TransacaoObrigatoria.exigir("cancelar mensagem programada");
        int n=jdbc.update("UPDATE mensagem_programada SET status='CANCELADA' WHERE id=? AND status='AGENDADA'",id);
        return n==0?Optional.empty():porIdVisivel(id);
    }
    private static final String BASE="""
            SELECT m.id,m.lead_id,l.nome lead_nome,m.atendente_id,u.nome atendente_nome,
                   m.conteudo,m.data_envio,m.status::text
              FROM mensagem_programada m JOIN lead l ON l.id=m.lead_id JOIN usuario u ON u.id=m.atendente_id
            """;
    private static MensagemProgramada mapear(ResultSet r,int i)throws SQLException{return new MensagemProgramada(
            r.getObject("id",UUID.class),r.getObject("lead_id",UUID.class),r.getString("lead_nome"),
            r.getObject("atendente_id",UUID.class),r.getString("atendente_nome"),r.getString("conteudo"),
            r.getTimestamp("data_envio").toInstant(),StatusMensagemProgramada.valueOf(r.getString("status")));}
}
