package com.synapse.crm.core.infrastructure.persistencia.lead;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.synapse.crm.core.application.darwin.ColetaDarwinRepositorio;

@Repository
class ColetaDarwinRepositorioJdbc implements ColetaDarwinRepositorio {
    private final JdbcTemplate jdbc;
    ColetaDarwinRepositorioJdbc(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public Optional<UUID> reservar(UUID id, String identidade, Instant agora, Instant lease, boolean manual) {
        var token = UUID.randomUUID();
        return jdbc.query("""
                INSERT INTO lead_coleta_cadastral AS c
                    (lead_id, identidade_hash, token, estado, proxima_consulta, repeticao_manual)
                VALUES (?, ?, ?, 'PROCESSANDO', ?, ?)
                ON CONFLICT (lead_id) DO UPDATE SET identidade_hash=EXCLUDED.identidade_hash,
                    token=EXCLUDED.token, estado='PROCESSANDO', proxima_consulta=EXCLUDED.proxima_consulta,
                    repeticao_manual=EXCLUDED.repeticao_manual
                WHERE c.proxima_consulta<=? OR
                    (c.estado<>'PROCESSANDO' AND (c.identidade_hash<>EXCLUDED.identidade_hash OR (? AND c.repeticao_manual<=?)))
                RETURNING token
                """, (rs,n) -> rs.getObject(1, UUID.class), id, identidade, token, Timestamp.from(lease),
                Timestamp.from(lease), Timestamp.from(agora), manual, Timestamp.from(agora)).stream().findFirst();
    }
    public boolean concluir(UUID id, UUID token, String estado, Instant proxima, Instant manual) {
        return jdbc.update("UPDATE lead_coleta_cadastral SET estado=?, proxima_consulta=?, repeticao_manual=? WHERE lead_id=? AND token=?",
                estado, Timestamp.from(proxima), Timestamp.from(manual), id, token) == 1;
    }
    public String estado(UUID id) {
        return jdbc.query("SELECT CASE WHEN estado='PROCESSANDO' AND proxima_consulta<=now() THEN 'AGUARDANDO' ELSE estado END FROM lead_coleta_cadastral WHERE lead_id=?", (rs,n)->rs.getString(1), id)
                .stream().findFirst().orElse("AGUARDANDO");
    }
}
