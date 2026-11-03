package com.synapse.crm.core.infrastructure.persistencia.lead;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.synapse.crm.core.application.lead.foto.CapturaDeFotoRepositorio;

@Repository
class CapturaDeFotoRepositorioJdbc implements CapturaDeFotoRepositorio {
    private final JdbcTemplate jdbc;
    CapturaDeFotoRepositorioJdbc(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    @Override public boolean credencialAtiva(UUID credencial, String instancia) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM canal_credencial cc JOIN canal c ON c.id=cc.canal_id
                WHERE cc.id=? AND cc.identificador_externo=? AND cc.token_ref='env://Z_API_TOKEN'
                  AND cc.ativo AND c.ativo AND cc.vigente_desde<=now() AND (cc.vigente_ate IS NULL OR cc.vigente_ate>now()))
                """, Boolean.class, credencial, instancia));
    }
    @Override public Optional<Reserva> reservar(UUID leadId, String instancia, UUID credencial, String telefoneHash, Instant agora, Instant ate) {
        UUID token = UUID.randomUUID();
        return jdbc.query("""
                INSERT INTO lead_foto_captura AS c (lead_id, instancia, credencial_id, telefone_hash, token, proxima_consulta)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT (lead_id) DO UPDATE SET token=EXCLUDED.token, proxima_consulta=EXCLUDED.proxima_consulta
                 WHERE c.proxima_consulta<=? AND c.instancia=EXCLUDED.instancia AND c.credencial_id=EXCLUDED.credencial_id
                   AND c.telefone_hash=EXCLUDED.telefone_hash
                RETURNING referencia_gerada
                """, (rs,n) -> new Reserva(leadId, token, instancia, credencial, rs.getString(1)),
                leadId, instancia, credencial, telefoneHash, token, Timestamp.from(ate), Timestamp.from(agora)).stream().findFirst();
    }
    @Override public boolean concluir(Reserva reserva, String referencia, Instant proxima, String resultado) {
        return jdbc.update("""
                UPDATE lead_foto_captura SET referencia_gerada=?, proxima_consulta=?, resultado=?
                WHERE lead_id=? AND token=? AND instancia=? AND credencial_id=?
                """, referencia, Timestamp.from(proxima), resultado, reserva.leadId(), reserva.token(),
                reserva.instancia(), reserva.credencial()) == 1;
    }
}
