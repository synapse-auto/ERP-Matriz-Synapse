package com.synapse.crm.app.config.auditoria;

import java.sql.Timestamp;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.sharedkernel.persistencia.Pools;

/**
 * Grava a linha de auditoria — bean separado de {@link AuditoriaAspect} de proposito, pelo mesmo
 * motivo do E07b: o aspecto chama {@link #registrar} atraves desta instancia injetada, uma chamada
 * externa que passa pelo proxy do Spring, entao {@code @Transactional} realmente vale. Se
 * {@code registrar} vivesse no proprio aspecto e fosse chamado via {@code this::registrar}, a
 * auto-invocacao pularia o proxy CGLIB.
 *
 * <p>{@code REQUIRES_NEW}: pelo desenho do aspecto (ver o Javadoc de {@link AuditoriaAspect} sobre
 * {@code @Order}), a transacao do caso de uso ja terminou quando chegamos aqui, entao normalmente nao
 * ha transacao ativa para propagar. O propagation explicito e defensivo — cobre o caso raro de um
 * caso de uso {@code @Auditable} chamado de dentro de outro, com uma transacao externa ainda aberta —
 * e documenta a intencao mesmo quando nao muda o comportamento.
 */
@Component
class EscritorDeAuditoria {

    private static final Logger log = LoggerFactory.getLogger(EscritorDeAuditoria.class);

    /** Marcador do alarme, no mesmo padrao greppavel dos demais ({@code [ALERTA_...]}). */
    static final String MARCADOR_ALARME = "[ALERTA_AUDITORIA_FALHOU]";

    private static final String SQL =
            """
            INSERT INTO audit_log (ator_id, ator_tipo, acao, entidade_tipo, entidade_id, lead_id,
                                    dados_antes, dados_depois, ip, criado_em)
                 VALUES (?, ?::origem_evento, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?::inet, ?)
            """;

    private final JdbcTemplate geral;

    EscritorDeAuditoria(@Qualifier(Pools.GENERAL_DATA_SOURCE) DataSource generalDataSource) {
        this.geral = new JdbcTemplate(generalDataSource);
    }

    /**
     * Nunca lanca. Uma acao de negocio ja concluida com sucesso nao pode virar erro 500 para o
     * cliente so porque a auditoria dessa acao falhou em gravar — a operacao real ja aconteceu.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void registrar(RegistroDeAuditoria registro) {
        try {
            geral.update(
                    SQL,
                    registro.atorId(),
                    registro.atorTipo(),
                    registro.acao(),
                    registro.entidadeTipo(),
                    registro.entidadeId(),
                    registro.leadId(),
                    registro.dadosAntes(),
                    registro.dadosDepois(),
                    registro.ip(),
                    Timestamp.from(registro.criadoEm()));
        } catch (RuntimeException e) {
            log.error(
                    "{} nao foi possivel gravar a auditoria da acao {} sobre {} {}. A operacao em si "
                            + "JA foi concluida com sucesso — so o registro de auditoria falhou, e essa "
                            + "acao NAO aparecera no historico ate alguem investigar. Motivo: {}",
                    MARCADOR_ALARME,
                    registro.acao(),
                    registro.entidadeTipo(),
                    registro.entidadeId(),
                    e.toString(),
                    e);
        }
    }
}
