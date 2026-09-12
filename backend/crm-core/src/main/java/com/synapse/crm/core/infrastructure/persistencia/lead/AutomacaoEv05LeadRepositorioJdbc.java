package com.synapse.crm.core.infrastructure.persistencia.lead;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.synapse.crm.core.application.lead.AutomacaoEv05LeadRepositorio;
import com.synapse.crm.core.application.lead.EscritaEv05ObsoletaException;
import com.synapse.crm.core.application.lead.LeadEv05NaoEncontradoException;
import com.synapse.crm.core.infrastructure.persistencia.TransacaoObrigatoria;
import com.synapse.crm.sharedkernel.persistencia.Pools;

/** JDBC transacional das leituras/escritas minimas do EV-05. */
@Repository
class AutomacaoEv05LeadRepositorioJdbc implements AutomacaoEv05LeadRepositorio {

    private final JdbcTemplate chat;

    AutomacaoEv05LeadRepositorioJdbc(@Qualifier(Pools.CHAT_DATA_SOURCE) DataSource dataSource) {
        this.chat = new JdbcTemplate(dataSource);
    }

    @Override
    public EstadoResumo resumo(UUID leadId) {
        TransacaoObrigatoria.exigir("consultar estado do resumo EV-05");
        try {
            return chat.queryForObject(
                    "SELECT id, resumo_ia IS NOT NULL AND btrim(resumo_ia) <> '' AS existe, "
                            + "resumo_ia_atualizado_em, ultima_interacao_em FROM lead WHERE id = ?",
                    (rs, row) -> new EstadoResumo(
                            rs.getObject("id", UUID.class),
                            rs.getBoolean("existe"),
                            instante(rs.getTimestamp("resumo_ia_atualizado_em")),
                            instante(rs.getTimestamp("ultima_interacao_em"))),
                    leadId);
        } catch (EmptyResultDataAccessException erro) {
            throw new LeadEv05NaoEncontradoException(leadId);
        }
    }

    @Override
    public EstadoPreenchimento preenchimento(UUID leadId) {
        TransacaoObrigatoria.exigir("consultar estado do preenchimento EV-05");
        try {
            return chat.queryForObject(
                    "SELECT id, email, cpf, empresa, localizacao, preenchimento_automatico_avaliado_em "
                            + "FROM lead WHERE id = ?",
                    (rs, row) -> new EstadoPreenchimento(
                            rs.getObject("id", UUID.class),
                            campo(rs.getString("email")),
                            campo(rs.getString("cpf")),
                            campo(rs.getString("empresa")),
                            campo(rs.getString("localizacao")),
                            instante(rs.getTimestamp("preenchimento_automatico_avaliado_em"))),
                    leadId);
        } catch (EmptyResultDataAccessException erro) {
            throw new LeadEv05NaoEncontradoException(leadId);
        }
    }

    @Override
    public EscritaResumo gravarResumo(UUID leadId, String resumo, Instant contextoGeradoEm, Instant quando) {
        TransacaoObrigatoria.exigir("gravar resumo EV-05");
        int alterados = chat.update(
                "UPDATE lead SET resumo_ia = ?, resumo_ia_atualizado_em = ? "
                        + "WHERE id = ? AND ( ?::timestamptz IS NULL OR ultima_interacao_em IS NULL OR ultima_interacao_em <= ?::timestamptz )",
                resumo,
                Timestamp.from(quando),
                leadId,
                contextoGeradoEm == null ? null : Timestamp.from(contextoGeradoEm),
                contextoGeradoEm == null ? null : Timestamp.from(contextoGeradoEm));
        if (alterados != 1) {
            // O mesmo erro cobre lead removido e ciclo atrasado sem revelar existencia a quem chama.
            throw new EscritaEv05ObsoletaException(leadId);
        }
        return new EscritaResumo(leadId, quando);
    }

    @Override
    public EscritaPreenchimento aplicarPreenchimento(
            UUID leadId,
            String email,
            String cpf,
            String empresa,
            String localizacao,
            Instant quando,
            Set<String> invalidos) {
        TransacaoObrigatoria.exigir("aplicar preenchimento EV-05");
        try {
            var atual = chat.queryForObject(
                    "SELECT email, cpf, empresa, localizacao FROM lead WHERE id = ? FOR UPDATE",
                    (rs, row) -> new Valores(
                            rs.getString("email"), rs.getString("cpf"), rs.getString("empresa"), rs.getString("localizacao")),
                    leadId);
            ResultadoCampo rEmail = resultado("email", email, atual.email(), invalidos);
            ResultadoCampo rCpf = resultado("cpf", cpf, atual.cpf(), invalidos);
            ResultadoCampo rEmpresa = resultado("empresa", empresa, atual.empresa(), invalidos);
            ResultadoCampo rLocalizacao = resultado("localizacao", localizacao, atual.localizacao(), invalidos);
            chat.update(
                    "UPDATE lead SET email = CASE WHEN ? THEN COALESCE(NULLIF(btrim(email), ''), ?) ELSE email END, "
                            + "cpf = CASE WHEN ? THEN COALESCE(NULLIF(btrim(cpf), ''), ?) ELSE cpf END, "
                            + "empresa = CASE WHEN ? THEN COALESCE(NULLIF(btrim(empresa), ''), ?) ELSE empresa END, "
                            + "localizacao = CASE WHEN ? THEN COALESCE(NULLIF(btrim(localizacao), ''), ?) ELSE localizacao END, "
                            + "preenchimento_automatico_avaliado_em = ? WHERE id = ?",
                    !invalidos.contains("email"), vazioParaNulo(email),
                    !invalidos.contains("cpf"), vazioParaNulo(cpf),
                    !invalidos.contains("empresa"), vazioParaNulo(empresa),
                    !invalidos.contains("localizacao"), vazioParaNulo(localizacao),
                    Timestamp.from(quando), leadId);
            return new EscritaPreenchimento(leadId, rEmail, rCpf, rEmpresa, rLocalizacao, quando);
        } catch (EmptyResultDataAccessException erro) {
            throw new LeadEv05NaoEncontradoException(leadId);
        }
    }

    private static ResultadoCampo resultado(String nome, String valor, String atual, Set<String> invalidos) {
        if (valor == null || valor.isBlank()) {
            return ResultadoCampo.AUSENTE;
        }
        if (invalidos.contains(nome)) {
            return ResultadoCampo.IGNORADO_INVALIDO;
        }
        if (atual != null && !atual.isBlank()) {
            return ResultadoCampo.IGNORADO_JA_PREENCHIDO;
        }
        return ResultadoCampo.APLICADO;
    }

    private static Campo campo(String valor) {
        return new Campo(valor != null && !valor.isBlank(), "CRM");
    }

    private static String vazioParaNulo(String valor) {
        return valor == null || valor.isBlank() ? null : valor;
    }

    private static Instant instante(Timestamp valor) {
        return valor == null ? null : valor.toInstant();
    }

    private record Valores(String email, String cpf, String empresa, String localizacao) {}
}
