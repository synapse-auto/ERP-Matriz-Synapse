package com.synapse.crm.atendimento.infrastructure.precificacao;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.synapse.crm.atendimento.application.precificacao.FilaDePrecificacaoMeta;
import com.synapse.crm.atendimento.domain.canal.TradutorDeCanal.PrecificacaoObservada;
import com.synapse.crm.core.infrastructure.persistencia.TransacaoObrigatoria;
import com.synapse.crm.sharedkernel.persistencia.Pools;

@Repository
class FilaDePrecificacaoMetaJdbc implements FilaDePrecificacaoMeta {

    private static final String SQL_REGISTRAR = """
            INSERT INTO meta_precificacao_entrada
                (id_evento, wamid, ocorrido_em, cobravel, categoria, tipo, modelo)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (id_evento) DO NOTHING
            """;

    private static final String SQL_PROXIMO = """
            SELECT id_evento FROM meta_precificacao_entrada
             WHERE processado_em IS NULL AND esgotado_em IS NULL
               AND proxima_tentativa_em <= now()
             ORDER BY proxima_tentativa_em, recebido_em
             LIMIT 1
            """;

    private static final String SQL_RESERVAR = """
            SELECT wamid, ocorrido_em, cobravel, categoria, tipo, modelo
              FROM meta_precificacao_entrada
             WHERE id_evento = ? AND processado_em IS NULL AND esgotado_em IS NULL
               AND proxima_tentativa_em <= now()
             FOR UPDATE SKIP LOCKED
            """;

    private static final String SQL_APURAR = """
            INSERT INTO meta_precificacao_observada
                (wamid, entregue_em, classificacao_em, cobravel, categoria, tipo, modelo)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (wamid) DO UPDATE SET
                entregue_em = LEAST(meta_precificacao_observada.entregue_em, EXCLUDED.entregue_em),
                classificacao_em = CASE
                    WHEN EXCLUDED.classificacao_em IS NOT NULL
                     AND (meta_precificacao_observada.classificacao_em IS NULL
                          OR EXCLUDED.classificacao_em >= meta_precificacao_observada.classificacao_em)
                    THEN EXCLUDED.classificacao_em ELSE meta_precificacao_observada.classificacao_em END,
                cobravel = CASE
                    WHEN EXCLUDED.classificacao_em IS NOT NULL
                     AND (meta_precificacao_observada.classificacao_em IS NULL
                          OR EXCLUDED.classificacao_em >= meta_precificacao_observada.classificacao_em)
                    THEN EXCLUDED.cobravel ELSE meta_precificacao_observada.cobravel END,
                categoria = CASE
                    WHEN EXCLUDED.classificacao_em IS NOT NULL
                     AND (meta_precificacao_observada.classificacao_em IS NULL
                          OR EXCLUDED.classificacao_em >= meta_precificacao_observada.classificacao_em)
                    THEN EXCLUDED.categoria ELSE meta_precificacao_observada.categoria END,
                tipo = CASE
                    WHEN EXCLUDED.classificacao_em IS NOT NULL
                     AND (meta_precificacao_observada.classificacao_em IS NULL
                          OR EXCLUDED.classificacao_em >= meta_precificacao_observada.classificacao_em)
                    THEN EXCLUDED.tipo ELSE meta_precificacao_observada.tipo END,
                modelo = CASE
                    WHEN EXCLUDED.classificacao_em IS NOT NULL
                     AND (meta_precificacao_observada.classificacao_em IS NULL
                          OR EXCLUDED.classificacao_em >= meta_precificacao_observada.classificacao_em)
                    THEN EXCLUDED.modelo ELSE meta_precificacao_observada.modelo END,
                atualizado_em = now()
            """;

    private final JdbcTemplate geral;

    FilaDePrecificacaoMetaJdbc(@Qualifier(Pools.GENERAL_DATA_SOURCE) DataSource dataSource) {
        this.geral = new JdbcTemplate(dataSource);
    }

    @Override
    public void registrar(List<PrecificacaoObservada> observacoes) {
        TransacaoObrigatoria.exigir("enfileirar precificacao Meta");
        for (PrecificacaoObservada observacao : observacoes) {
            geral.update(
                    SQL_REGISTRAR,
                    observacao.idEvento(),
                    observacao.wamid(),
                    Timestamp.from(observacao.ocorridoEm()),
                    observacao.cobravel(),
                    observacao.categoria(),
                    observacao.tipo(),
                    observacao.modelo());
        }
    }

    @Override
    public Optional<String> proximoEventoId() {
        TransacaoObrigatoria.exigir("localizar precificacao Meta pendente");
        return geral.query(SQL_PROXIMO, (rs, i) -> rs.getString(1)).stream().findFirst();
    }

    @Override
    public boolean processar(String idEvento) {
        TransacaoObrigatoria.exigir("apurar precificacao Meta");
        List<Linha> linhas = geral.query(
                SQL_RESERVAR,
                (rs, i) -> new Linha(
                        rs.getString("wamid"),
                        rs.getTimestamp("ocorrido_em"),
                        (Boolean) rs.getObject("cobravel"),
                        rs.getString("categoria"),
                        rs.getString("tipo"),
                        rs.getString("modelo")),
                idEvento);
        if (linhas.isEmpty()) {
            return false;
        }
        Linha linha = linhas.get(0);
        geral.update(
                SQL_APURAR,
                linha.wamid(),
                linha.ocorridoEm(),
                linha.cobravel() == null ? null : linha.ocorridoEm(),
                linha.cobravel(),
                linha.categoria(),
                linha.tipo(),
                linha.modelo());
        geral.update(
                "UPDATE meta_precificacao_entrada SET processado_em = now() WHERE id_evento = ?",
                idEvento);
        return true;
    }

    @Override
    public void reagendar(String idEvento) {
        TransacaoObrigatoria.exigir("reagendar precificacao Meta");
        geral.update(
                """
                UPDATE meta_precificacao_entrada
                   SET tentativas = tentativas + 1,
                       proxima_tentativa_em = now() + make_interval(
                           secs => LEAST(1800, (5 * power(2, tentativas))::int)),
                       esgotado_em = CASE WHEN tentativas + 1 >= 5 THEN now() ELSE NULL END
                 WHERE id_evento = ? AND processado_em IS NULL
                """,
                idEvento);
    }

    private record Linha(
            String wamid,
            Timestamp ocorridoEm,
            Boolean cobravel,
            String categoria,
            String tipo,
            String modelo) {}
}
