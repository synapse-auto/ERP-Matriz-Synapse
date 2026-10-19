package com.synapse.crm.relatorios.infrastructure.persistencia.cancelamentos;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.synapse.crm.relatorios.application.cancelamentos.*;
import com.synapse.crm.relatorios.domain.cancelamentos.*;
import com.synapse.crm.sharedkernel.identidade.UsuarioAutenticado;

/** Pool geral; RLS da projecao aplica visibilidade antes de filtros/LIMIT, sem N+1. */
@Repository
class CancelamentosRepositorioJdbc implements LeituraDeCancelamentos, GravacaoDeCancelamentos {
    private static final String CAMPOS = "id, agendado_para, cancelado_em, recebido_em, contato_nome, "
            + "profissional_nome, tipo_agendamento, descricao, lead_id, atendimento_id";
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    CancelamentosRepositorioJdbc(JdbcTemplate jdbc, ObjectMapper json) { this.jdbc=jdbc; this.json=json; }

    @Override public Resultado registrar(NovoCancelamento e, String chave, Instant recebidoEm) {
        String impressao = impressao(e);
        UUID id = UUID.randomUUID();
        // ON CONFLICT espera o concorrente; uma nova instrucao no READ COMMITTED ve seu commit.
        var inseridos = jdbc.query("""
            INSERT INTO cancelamento_agendamento (id,origem,evento_externo_id,agendamento_externo_id,
                chave_idempotencia,impressao,lead_id,atendimento_id,agendado_para,cancelado_em,recebido_em,
                contato_nome,profissional_nome,tipo_agendamento,descricao)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) ON CONFLICT DO NOTHING RETURNING id,recebido_em
            """, (r,n) -> new Resultado(r.getObject("id",UUID.class),instante(r,"recebido_em"),false),
            id,e.origem(),e.eventoExternoId(),e.agendamentoExternoId(),chave,impressao,e.leadId(),e.atendimentoId(),
            timestamp(e.agendadoPara()),timestamp(e.canceladoEm()),timestamp(recebidoEm),
            e.contatoNome(),e.profissionalNome(),e.tipoAgendamento(),e.descricao());
        if (!inseridos.isEmpty()) return inseridos.getFirst();
        var existentes = jdbc.query("""
            SELECT id,recebido_em,chave_idempotencia,impressao FROM cancelamento_agendamento
            WHERE origem=? AND (chave_idempotencia=? OR evento_externo_id=?)
            """, (r,n) -> {
                if (!chave.equals(r.getString("chave_idempotencia")) || !impressao.equals(r.getString("impressao")))
                    throw new ConflitoDeCancelamentoException();
                return new Resultado(r.getObject("id",UUID.class),instante(r,"recebido_em"),true);
            }, e.origem(),chave,e.eventoExternoId());
        if (existentes.size()!=1) throw new ConflitoDeCancelamentoException();
        return existentes.getFirst();
    }

    @Override public Pagina listar(FiltroCancelamentos f, UsuarioAutenticado usuario) {
        String data = switch(f.base()) {
            case REGISTRO -> "recebido_em";
            case AGENDAMENTO -> "agendado_para";
            case CANCELAMENTO -> "cancelado_em";
        };
        List<Object> p = new ArrayList<>(List.of(timestamp(f.inicio()),timestamp(f.fimExclusivo())));
        StringBuilder sql = new StringBuilder("SELECT "+CAMPOS+" FROM cancelamento_agendamento WHERE "+data+">=? AND "+data+"<?");
        busca(sql,p,"profissional_nome",f.profissional()); busca(sql,p,"tipo_agendamento",f.tipo()); busca(sql,p,"contato_nome",f.nome());
        if (f.descricao()==FiltroCancelamentos.Descricao.COM_DESCRICAO) sql.append(" AND descricao IS NOT NULL");
        if (f.descricao()==FiltroCancelamentos.Descricao.SEM_DESCRICAO) sql.append(" AND descricao IS NULL");
        // O intervalo >= / < ja exclui NULL. NULLS LAST nao muda o resultado, mas impede
        // que o PostgreSQL use a ordem DESC do indice sem um Sort intermediario.
        sql.append(" ORDER BY "+data+" DESC,id DESC LIMIT ? OFFSET ?");
        p.add(f.tamanho()+1); p.add(Math.multiplyExact(f.pagina(),f.tamanho()));
        try {
            var linhas = jdbc.query(sql.toString(),CancelamentosRepositorioJdbc::mapear,p.toArray());
            boolean mais=linhas.size()>f.tamanho();
            return new Pagina(mais?linhas.subList(0,f.tamanho()):linhas,mais);
        } catch (DataAccessException indisponivel) {
            throw new FonteCancelamentosException(FonteCancelamentosException.Codigo.FONTE_INDISPONIVEL);
        }
    }
    @Override public Optional<CancelamentoAgendamento> detalhe(String id, UsuarioAutenticado usuario) {
        UUID uuid;
        try { uuid=UUID.fromString(id); } catch (IllegalArgumentException invalido) { return Optional.empty(); }
        try {
            return jdbc.query("SELECT "+CAMPOS+" FROM cancelamento_agendamento WHERE id=?",
                    CancelamentosRepositorioJdbc::mapear,uuid).stream().findFirst();
        } catch (DataAccessException indisponivel) {
            throw new FonteCancelamentosException(FonteCancelamentosException.Codigo.FONTE_INDISPONIVEL);
        }
    }
    private static void busca(StringBuilder sql,List<Object> p,String coluna,String valor) {
        if (valor==null) return;
        sql.append(" AND "+coluna+" ILIKE ? ESCAPE '!'");
        p.add("%"+valor.replace("!","!!").replace("%","!%").replace("_","!_")+"%");
    }
    private String impressao(NovoCancelamento e) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(json.writeValueAsString(e).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | JsonProcessingException erro) {
            throw new IllegalStateException("Falha ao calcular impressao do cancelamento");
        }
    }
    private static Timestamp timestamp(Instant instante) { return instante==null?null:Timestamp.from(instante); }
    private static Instant instante(ResultSet r,String campo) throws SQLException {
        var t=r.getTimestamp(campo); return t==null?null:t.toInstant();
    }
    private static CancelamentoAgendamento mapear(ResultSet r,int n) throws SQLException {
        return new CancelamentoAgendamento(r.getString("id"),instante(r,"agendado_para"),instante(r,"cancelado_em"),
            r.getString("contato_nome"),r.getString("profissional_nome"),r.getString("tipo_agendamento"),r.getString("descricao"),
            instante(r,"recebido_em"),instante(r,"cancelado_em"),r.getObject("lead_id",UUID.class),r.getObject("atendimento_id",UUID.class));
    }
}
