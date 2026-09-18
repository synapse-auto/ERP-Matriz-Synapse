package com.synapse.migrationrunner;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.flywaydb.core.internal.resolver.ChecksumCalculator;
import org.flywaydb.core.internal.resource.StringResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Execução paginada da V73 usada somente pelo runner one-shot.
 *
 * <p>O SQL versionado continua imutável e continua sendo a referência do checksum. Esta classe
 * reproduz a operação de dados em transações curtas para que a V73 não fique presa a uma única
 * transação gigante. O Flyway registra a versão somente depois que este método retorna com sucesso.
 */
final class V73__NormalizarPrefixoDiscagemLeads extends BaseJavaMigration {

    static final MigrationVersion VERSAO = MigrationVersion.fromVersion("73");
    static final String DESCRICAO = "normalizar prefixo discagem leads";
    static final String CHECKPOINT_TABLE = "synapse_v73_runner_checkpoint";
    static final String FASE_FUSAO = "FUSAO";
    static final String FASE_NORMALIZACAO = "NORMALIZACAO";

    private static final Logger log = LoggerFactory.getLogger(V73__NormalizarPrefixoDiscagemLeads.class);
    private static final String SCRIPT_RESOURCE = "/db/migration/V73__normalizar_prefixo_discagem_leads.sql";

    private final String ddiPadrao;
    private final int tamanhoLote;
    private final int maxTentativas;
    private final Duration lease;

    V73__NormalizarPrefixoDiscagemLeads(String ddiPadrao, int tamanhoLote, int maxTentativas, Duration lease) {
        this.ddiPadrao = Objects.requireNonNull(ddiPadrao, "ddiPadrao");
        this.tamanhoLote = tamanhoLote;
        this.maxTentativas = maxTentativas;
        this.lease = lease;
        if (!ddiPadrao.matches("[0-9]{1,3}")) {
            throw new IllegalArgumentException("DDI padrão deve conter de um a três dígitos");
        }
        if (tamanhoLote < 1 || maxTentativas < 1 || lease.isNegative() || lease.isZero()) {
            throw new IllegalArgumentException("Parâmetros da migration devem ser positivos");
        }
    }

    static int checksumOriginal() {
        return checksumOriginal("55");
    }

    static int checksumOriginal(String ddiPadrao) {
        try (InputStream fluxo = V73__NormalizarPrefixoDiscagemLeads.class.getResourceAsStream(SCRIPT_RESOURCE)) {
            if (fluxo == null) {
                throw new IllegalStateException("Script imutável da V73 não encontrado");
            }
            String conteudo;
            try (Reader leitor = new InputStreamReader(fluxo, StandardCharsets.UTF_8)) {
                StringBuilder texto = new StringBuilder();
                char[] buffer = new char[8192];
                int lidos;
                while ((lidos = leitor.read(buffer)) != -1) {
                    texto.append(buffer, 0, lidos);
                }
                conteudo = texto.toString();
            }
            // O Flyway calcula o checksum após a substituição de placeholders. A implementação
            // Java precisa produzir exatamente o mesmo valor para validar V73 já aplicada.
            conteudo = conteudo.replace("${telefone_ddi_padrao}", ddiPadrao);
            return ChecksumCalculator.calculate(new StringResource(conteudo));
        } catch (IOException erro) {
            throw new IllegalStateException("Não foi possível ler o checksum imutável da V73", erro);
        }
    }

    @Override
    public MigrationVersion getVersion() {
        return VERSAO;
    }

    @Override
    public String getDescription() {
        return DESCRICAO;
    }

    @Override
    public Integer getChecksum() {
        return checksumOriginal(ddiPadrao);
    }

    @Override
    public boolean canExecuteInTransaction() {
        return false;
    }

    @Override
    public void migrate(Context contexto) throws Exception {
        Connection conexao = contexto.getConnection();
        boolean autoCommitOriginal = conexao.getAutoCommit();
        String papelAnterior = lerPapelAtual(conexao);
        String etapa = "PAPEL";
        try {
            conexao.setAutoCommit(true);
            definirPapelServico(conexao);
            etapa = "ESTRUTURA";
            prepararEstrutura(conexao);
            etapa = "FUNCOES";
            aplicarFuncoes(conexao);
            etapa = "CONTEXTO";
            validarContextoServico(conexao);
            etapa = "FK";
            validarFks(conexao);
            etapa = "FUSAO";
            processarFusoes(conexao);
            etapa = "NORMALIZACAO";
            processarNormalizacoes(conexao);
            etapa = "CONCLUSAO";
            registrarConclusao(conexao);
        } catch (Exception erro) {
            log.error(
                    "[FLYWAY_CONTROLADO] falha na fase={} tipo={} sqlState={}",
                    etapa,
                    erro.getClass().getSimpleName(),
                    erro instanceof SQLException sql ? sql.getSQLState() : "nao-sql");
            throw erro;
        } finally {
            restaurarPapel(conexao, papelAnterior);
            if (conexao.getAutoCommit() != autoCommitOriginal) {
                conexao.setAutoCommit(autoCommitOriginal);
            }
        }
    }

    private static String lerPapelAtual(Connection conexao) throws SQLException {
        try (Statement comando = conexao.createStatement();
                ResultSet resultado = comando.executeQuery("SELECT current_setting('app.papel', true)")) {
            return resultado.next() ? resultado.getString(1) : null;
        }
    }

    private static void definirPapelServico(Connection conexao) throws SQLException {
        try (Statement comando = conexao.createStatement()) {
            comando.execute("SELECT set_config('app.papel', 'SERVICO', false)");
        }
    }

    private static void restaurarPapel(Connection conexao, String papelAnterior) throws SQLException {
        try (Statement comando = conexao.createStatement()) {
            if (papelAnterior == null || papelAnterior.isBlank()) {
                comando.execute("RESET app.papel");
            } else {
                try (PreparedStatement configuracao = conexao.prepareStatement(
                        "SELECT set_config('app.papel', ?, false)")) {
                    configuracao.setString(1, papelAnterior);
                    configuracao.execute();
                }
            }
        }
    }

    private void prepararEstrutura(Connection conexao) throws SQLException {
        try (Statement comando = conexao.createStatement()) {
            comando.execute("""
                    CREATE TABLE IF NOT EXISTS synapse_v73_runner_checkpoint (
                        fase VARCHAR(32) NOT NULL,
                        item_chave VARCHAR(160) NOT NULL,
                        estado VARCHAR(16) NOT NULL,
                        tentativas INTEGER NOT NULL DEFAULT 0,
                        lease_ate TIMESTAMPTZ,
                        ultimo_erro VARCHAR(512),
                        atualizado_em TIMESTAMPTZ NOT NULL DEFAULT now(),
                        CONSTRAINT pk_synapse_v73_checkpoint PRIMARY KEY (fase, item_chave),
                        CONSTRAINT ck_synapse_v73_checkpoint_estado
                            CHECK (estado IN ('PROCESSANDO', 'CONCLUIDO', 'IGNORADO', 'FALHOU'))
                    )
                    """);
            comando.execute("""
                    CREATE INDEX IF NOT EXISTS ix_synapse_v73_checkpoint_estado
                        ON synapse_v73_runner_checkpoint (fase, estado, lease_ate)
                    """);
        }
    }

    private void aplicarFuncoes(Connection conexao) throws SQLException {
        try (Statement comando = conexao.createStatement()) {
            comando.execute("""
                    CREATE OR REPLACE FUNCTION app_telefone_com_ddi(entrada TEXT, ddi_padrao TEXT)
                    RETURNS TEXT LANGUAGE sql IMMUTABLE AS $$
                        WITH limpo AS (
                            SELECT regexp_replace(entrada, '[^0-9]', '', 'g') AS digitos
                        ), sem_prefixo AS (
                            SELECT CASE
                                       WHEN length(digitos) >= 4
                                            AND left(digitos, 4) IN ('0300', '0400', '0500', '0800', '0900')
                                           THEN digitos
                                       WHEN left(digitos, 1) = '0'
                                            AND length(digitos) - 1 IN (10, 11)
                                           THEN substr(digitos, 2)
                                       WHEN left(digitos, 1) = '0'
                                            AND length(digitos) - 3 IN (10, 11)
                                           THEN substr(digitos, 4)
                                       ELSE digitos
                                   END AS digitos
                              FROM limpo
                        )
                        SELECT CASE
                                   WHEN digitos IS NULL THEN NULL
                                   WHEN length(digitos) >= 4
                                        AND left(digitos, 4) IN ('0300', '0400', '0500', '0800', '0900')
                                       THEN CASE WHEN length(digitos) < 10 THEN NULL ELSE digitos END
                                   WHEN length(digitos) < 10 THEN NULL
                                   WHEN length(digitos) IN (10, 11) THEN ddi_padrao || digitos
                                   ELSE digitos
                               END
                          FROM sem_prefixo;
                    $$
                    """);
            comando.execute("""
                    CREATE OR REPLACE FUNCTION app_telefone_canonico(entrada TEXT, ddi_padrao TEXT)
                    RETURNS TEXT LANGUAGE sql IMMUTABLE AS $$
                        SELECT CASE
                                   WHEN com_ddi IS NULL THEN NULL
                                   WHEN length(com_ddi) = 12
                                        AND left(com_ddi, 2) = '55'
                                        AND substr(com_ddi, 5, 1) BETWEEN '6' AND '9'
                                       THEN substr(com_ddi, 1, 4) || '9' || substr(com_ddi, 5)
                                   ELSE com_ddi
                               END
                          FROM (SELECT app_telefone_com_ddi(entrada, ddi_padrao) AS com_ddi) AS base;
                    $$
                    """);
            comando.execute("""
                    COMMENT ON FUNCTION app_telefone_com_ddi(TEXT, TEXT) IS
                        'Remove trunk 0 ou operadora 0XX somente quando o restante tem 10/11 digitos; preserva 0300, '
                        '0400, 0500, 0800 e 0900. Depois completa o DDI configurado.'
                    """);
            comando.execute("""
                    COMMENT ON FUNCTION app_telefone_canonico(TEXT, TEXT) IS
                        'Telefone canonico do CRM: prefixo de discagem brasileiro, DDI e nono digito. '
                        'Espelha TelefoneCanonico do dominio; TelefoneNonoDigitoIT.Paridade reprova divergencias.'
                    """);
        }
    }

    private void validarFks(Connection conexao) throws SQLException {
        String sql = """
                SELECT count(*)
                  FROM pg_constraint
                 WHERE confrelid = 'lead'::regclass
                   AND contype = 'f'
                   AND conrelid::regclass::text <> ALL (ARRAY[
                       'atendimento', 'campanha_mensagem_metrica', 'evento_timeline', 'lead_tag',
                       'lembrete', 'mensagem_programada', 'mensagem_envio_idempotencia'])
                """;
        try (PreparedStatement consulta = conexao.prepareStatement(sql); ResultSet resultado = consulta.executeQuery()) {
            resultado.next();
            if (resultado.getInt(1) != 0) {
                throw new IllegalStateException("Existem FKs novas para lead; migration interrompida");
            }
        }
    }

    private static void validarContextoServico(Connection conexao) throws SQLException {
        try (Statement comando = conexao.createStatement();
                ResultSet resultado = comando.executeQuery("SELECT app_enxerga_todos_os_leads()")) {
            if (!resultado.next() || !resultado.getBoolean(1)) {
                throw new IllegalStateException(
                        "contexto de servico nao aplicado: a limpeza do prefixo enxergaria zero leads");
            }
        }
    }

    private void processarFusoes(Connection conexao) throws SQLException {
        int processados = 0;
        int fundidos = 0;
        int gruposParaRevisao = contarGruposComMaisDeDoisCandidatos(conexao);
        if (gruposParaRevisao > 0) {
            log.info(
                    "[FLYWAY_CONTROLADO] fase=FUSAO gruposComMaisDeDoisCandidatos={} mantidosParaRevisao=true",
                    gruposParaRevisao);
        }
        while (true) {
            List<Par> candidatos = buscarPares(conexao);
            if (candidatos.isEmpty()) {
                break;
            }
            boolean reservado = false;
            for (Par par : candidatos) {
                if (!reservar(conexao, FASE_FUSAO, par.chave())) {
                    continue;
                }
                reservado = true;
                try {
                    boolean fundiu = fundirPar(conexao, par);
                    concluir(conexao, FASE_FUSAO, par.chave(), fundiu ? "CONCLUIDO" : "IGNORADO", null);
                    fundidos += fundiu ? 1 : 0;
                    processados++;
                    log.info(
                            "[FLYWAY_CONTROLADO] fase=FUSAO lote={} processados={} fundidos={} restantesAproximados={}",
                            tamanhoLote,
                            processados,
                            fundidos,
                            Math.max(0, candidatos.size() - processados));
                } catch (RuntimeException | SQLException erro) {
                    registrarFalha(conexao, FASE_FUSAO, par.chave(), erro);
                    throw erro;
                }
            }
            if (!reservado) {
                throw new IllegalStateException("Todos os pares pendentes excederam as tentativas do lote");
            }
        }
        log.info("[FLYWAY_CONTROLADO] fase=FUSAO concluida processados={} fundidos={}", processados, fundidos);
    }

    private int contarGruposComMaisDeDoisCandidatos(Connection conexao) throws SQLException {
        String sql = """
                SELECT count(*)
                  FROM (
                        SELECT app_telefone_canonico(l.telefone, ?) AS canonico
                          FROM lead l
                         WHERE l.telefone IS NOT NULL
                           AND l.telefone !~ '^55[1-9][0-9]{9,10}$'
                           AND app_telefone_canonico(l.telefone, ?) ~ '^55[1-9][0-9]{9,10}$'
                         GROUP BY 1
                        HAVING count(*) > 2
                       ) grupos
                """;
        try (PreparedStatement consulta = conexao.prepareStatement(sql)) {
            consulta.setString(1, ddiPadrao);
            consulta.setString(2, ddiPadrao);
            consulta.setString(3, ddiPadrao);
            try (ResultSet resultado = consulta.executeQuery()) {
                resultado.next();
                return resultado.getInt(1);
            }
        }
    }

    private List<Par> buscarPares(Connection conexao) throws SQLException {
        String sql = """
                WITH candidatos AS (
                    SELECT l.id,
                           l.telefone,
                           l.telefone_provedor,
                           app_telefone_canonico(l.telefone, ?) AS canonico,
                           EXISTS (SELECT 1 FROM atendimento a WHERE a.lead_id = l.id) AS tem_conversa,
                           (SELECT count(*)
                              FROM mensagem m
                              JOIN atendimento a ON a.id = m.atendimento_id
                             WHERE a.lead_id = l.id) AS mensagens
                      FROM lead l
                     WHERE l.telefone IS NOT NULL
                ), pares AS (
                    SELECT bad.id AS perdedor, survivor.id AS sobrevivente, bad.canonico
                      FROM candidatos bad
                      JOIN candidatos survivor
                        ON survivor.id <> bad.id
                       AND survivor.canonico = bad.canonico
                     WHERE bad.telefone !~ '^55[1-9][0-9]{9,10}$'
                       AND bad.canonico ~ '^55[1-9][0-9]{9,10}$'
                       AND (bad.telefone_provedor IS NULL OR btrim(bad.telefone_provedor) = '')
                       AND bad.mensagens = 0
                       AND survivor.tem_conversa
                )
                SELECT perdedor, sobrevivente, canonico
                  FROM pares p
                 WHERE (SELECT count(*) FROM candidatos c WHERE c.canonico = p.canonico) = 2
                   AND NOT EXISTS (
                       SELECT 1 FROM synapse_v73_runner_checkpoint cp
                        WHERE cp.fase = 'FUSAO'
                          AND cp.item_chave = p.sobrevivente::text || ':' || p.perdedor::text
                          AND cp.estado IN ('CONCLUIDO', 'IGNORADO'))
                 ORDER BY canonico, perdedor
                 LIMIT ?
                """;
        List<Par> pares = new ArrayList<>();
        try (PreparedStatement consulta = conexao.prepareStatement(sql)) {
            consulta.setString(1, ddiPadrao);
            consulta.setInt(2, tamanhoLote);
            try (ResultSet resultado = consulta.executeQuery()) {
                while (resultado.next()) {
                    pares.add(new Par(
                            resultado.getObject("perdedor", UUID.class),
                            resultado.getObject("sobrevivente", UUID.class),
                            resultado.getString("canonico")));
                }
            }
        }
        return pares;
    }

    private boolean fundirPar(Connection conexao, Par par) throws SQLException {
        conexao.setAutoCommit(false);
        try {
            contextoServico(conexao);
            if (!bloquearPar(conexao, par)) {
                conexao.commit();
                conexao.setAutoCommit(true);
                return false;
            }
            verificarMetrica(conexao, par);
            executarAtualizacoesDaFusao(conexao, par);
            conexao.commit();
            conexao.setAutoCommit(true);
            return true;
        } catch (RuntimeException | SQLException erro) {
            conexao.rollback();
            conexao.setAutoCommit(true);
            throw erro;
        }
    }

    private boolean bloquearPar(Connection conexao, Par par) throws SQLException {
        String sql = "SELECT id FROM lead WHERE id IN (?, ?) ORDER BY id FOR UPDATE";
        int encontrados = 0;
        try (PreparedStatement consulta = conexao.prepareStatement(sql)) {
            consulta.setObject(1, par.perdedor());
            consulta.setObject(2, par.sobrevivente());
            try (ResultSet resultado = consulta.executeQuery()) {
                while (resultado.next()) {
                    encontrados++;
                }
            }
        }
        return encontrados == 2 && candidatoAindaValido(conexao, par);
    }

    private boolean candidatoAindaValido(Connection conexao, Par par) throws SQLException {
        String sql = """
                SELECT bad.id
                  FROM lead bad
                  JOIN lead survivor ON survivor.id = ?
                 WHERE bad.id = ?
                   AND bad.telefone IS NOT NULL
                   AND bad.telefone !~ '^55[1-9][0-9]{9,10}$'
                   AND app_telefone_canonico(bad.telefone, ?) ~ '^55[1-9][0-9]{9,10}$'
                   AND (bad.telefone_provedor IS NULL OR btrim(bad.telefone_provedor) = '')
                   AND NOT EXISTS (
                       SELECT 1 FROM mensagem m JOIN atendimento a ON a.id = m.atendimento_id
                        WHERE a.lead_id = bad.id)
                   AND EXISTS (SELECT 1 FROM atendimento a WHERE a.lead_id = survivor.id)
                   AND app_telefone_canonico(bad.telefone, ?) = app_telefone_canonico(survivor.telefone, ?)
                """;
        try (PreparedStatement consulta = conexao.prepareStatement(sql)) {
            consulta.setObject(1, par.sobrevivente());
            consulta.setObject(2, par.perdedor());
            consulta.setString(3, ddiPadrao);
            consulta.setString(4, ddiPadrao);
            consulta.setString(5, ddiPadrao);
            try (ResultSet resultado = consulta.executeQuery()) {
                return resultado.next();
            }
        }
    }

    private void verificarMetrica(Connection conexao, Par par) throws SQLException {
        String sql = """
                SELECT 1
                  FROM campanha_mensagem_metrica perdida
                  JOIN campanha_mensagem_metrica mantida
                    ON mantida.lead_id = ?
                   AND mantida.campanha_mensagem_id = perdida.campanha_mensagem_id
                 WHERE perdida.lead_id = ?
                 LIMIT 1
                """;
        try (PreparedStatement consulta = conexao.prepareStatement(sql)) {
            consulta.setObject(1, par.sobrevivente());
            consulta.setObject(2, par.perdedor());
            try (ResultSet resultado = consulta.executeQuery()) {
                if (resultado.next()) {
                    throw new IllegalStateException("Colisão de métrica de campanha exige revisão manual");
                }
            }
        }
    }

    private void executarAtualizacoesDaFusao(Connection conexao, Par par) throws SQLException {
        executar(conexao, """
                INSERT INTO lead_tag (lead_id, tag_id)
                SELECT ?, tag_id FROM lead_tag WHERE lead_id = ? ON CONFLICT DO NOTHING
                """, par.sobrevivente(), par.perdedor());
        executar(conexao, "DELETE FROM lead_tag WHERE lead_id = ?", par.perdedor());
        for (String tabela : List.of(
                "lembrete",
                "mensagem_programada",
                "campanha_mensagem_metrica",
                "mensagem_envio_idempotencia",
                "evento_timeline",
                "atendimento",
                "audit_log")) {
            executar(conexao, "UPDATE " + tabela + " SET lead_id = ? WHERE lead_id = ?", par.sobrevivente(), par.perdedor());
        }
        finalizarAtendimentosExtras(conexao, par.sobrevivente());
        executar(conexao, """
                UPDATE lead sobrevivente
                   SET foto_url = COALESCE(NULLIF(sobrevivente.foto_url, ''), NULLIF(perdedor.foto_url, '')),
                       email = COALESCE(NULLIF(sobrevivente.email, ''), NULLIF(perdedor.email, '')),
                       empresa = COALESCE(NULLIF(sobrevivente.empresa, ''), NULLIF(perdedor.empresa, '')),
                       cpf = COALESCE(NULLIF(sobrevivente.cpf, ''), NULLIF(perdedor.cpf, '')),
                       localizacao = COALESCE(NULLIF(sobrevivente.localizacao, ''), NULLIF(perdedor.localizacao, '')),
                       codigo = COALESCE(NULLIF(sobrevivente.codigo, ''), NULLIF(perdedor.codigo, '')),
                       canal_origem_id = COALESCE(sobrevivente.canal_origem_id, perdedor.canal_origem_id),
                       etapa_atendimento_id = COALESCE(sobrevivente.etapa_atendimento_id, perdedor.etapa_atendimento_id),
                       atendente_responsavel_id = COALESCE(sobrevivente.atendente_responsavel_id, perdedor.atendente_responsavel_id),
                       notas = COALESCE(NULLIF(sobrevivente.notas, ''), NULLIF(perdedor.notas, '')),
                       resumo_ia = COALESCE(NULLIF(sobrevivente.resumo_ia, ''), NULLIF(perdedor.resumo_ia, '')),
                       dados_customizados = COALESCE(perdedor.dados_customizados, '{}'::jsonb)
                           || COALESCE(sobrevivente.dados_customizados, '{}'::jsonb),
                       foto_referencia = COALESCE(sobrevivente.foto_referencia, perdedor.foto_referencia),
                       foto_hash = CASE WHEN sobrevivente.foto_referencia IS NULL
                                        THEN COALESCE(sobrevivente.foto_hash, perdedor.foto_hash)
                                        ELSE sobrevivente.foto_hash END,
                       foto_atualizada_em = GREATEST(sobrevivente.foto_atualizada_em, perdedor.foto_atualizada_em),
                       num_atendimentos = sobrevivente.num_atendimentos + perdedor.num_atendimentos,
                       num_mensagens = sobrevivente.num_mensagens + perdedor.num_mensagens,
                       ultima_interacao_em = GREATEST(sobrevivente.ultima_interacao_em, perdedor.ultima_interacao_em),
                       ultima_mensagem_do_lead_em = GREATEST(sobrevivente.ultima_mensagem_do_lead_em, perdedor.ultima_mensagem_do_lead_em),
                       resumo_ia_atualizado_em = GREATEST(sobrevivente.resumo_ia_atualizado_em, perdedor.resumo_ia_atualizado_em),
                       preenchimento_automatico_avaliado_em = GREATEST(
                           sobrevivente.preenchimento_automatico_avaliado_em,
                           perdedor.preenchimento_automatico_avaliado_em)
                  FROM lead perdedor
                 WHERE sobrevivente.id = ? AND perdedor.id = ?
                """, par.sobrevivente(), par.perdedor());
        executar(conexao, "DELETE FROM lead WHERE id = ?", par.perdedor());
    }

    private static void finalizarAtendimentosExtras(Connection conexao, UUID leadId) throws SQLException {
        String ranking = """
                SELECT id
                  FROM (
                      SELECT a.id,
                             row_number() OVER (ORDER BY count(m.id) DESC, a.iniciado_em ASC, a.id ASC) AS posicao
                        FROM atendimento a
                        LEFT JOIN mensagem m ON m.atendimento_id = a.id
                       WHERE a.lead_id = ? AND a.status <> 'FINALIZADO'
                       GROUP BY a.id
                  ) ranqueados
                 WHERE posicao > 1
                """;
        List<UUID> extras = new ArrayList<>();
        try (PreparedStatement consulta = conexao.prepareStatement(ranking)) {
            consulta.setObject(1, leadId);
            try (ResultSet resultado = consulta.executeQuery()) {
                while (resultado.next()) {
                    extras.add(resultado.getObject(1, UUID.class));
                }
            }
        }
        for (UUID extra : extras) {
            executar(conexao, "UPDATE atendimento SET status = 'FINALIZADO', finalizado_em = now() WHERE id = ?", extra);
            executar(conexao, """
                    UPDATE atendimento_participante
                       SET saiu_em = now()
                     WHERE atendimento_id = ? AND saiu_em IS NULL
                    """, extra);
        }
    }

    private void processarNormalizacoes(Connection conexao) throws SQLException {
        int processados = 0;
        while (true) {
            List<UUID> candidatos = buscarNormalizacoes(conexao);
            if (candidatos.isEmpty()) {
                break;
            }
            boolean reservado = false;
            for (UUID id : candidatos) {
                String chave = id.toString();
                if (!reservar(conexao, FASE_NORMALIZACAO, chave)) {
                    continue;
                }
                reservado = true;
                try {
                    normalizar(conexao, id);
                    concluir(conexao, FASE_NORMALIZACAO, chave, "CONCLUIDO", null);
                    processados++;
                } catch (RuntimeException | SQLException erro) {
                    registrarFalha(conexao, FASE_NORMALIZACAO, chave, erro);
                    throw erro;
                }
            }
            if (!reservado) {
                throw new IllegalStateException("Todos os telefones pendentes excederam as tentativas do lote");
            }
            log.info("[FLYWAY_CONTROLADO] fase=NORMALIZACAO processados={} restantesAproximados={}", processados, candidatos.size());
        }
        int revisaoManual = contarForaDaRegra(conexao);
        log.info("[FLYWAY_CONTROLADO] fase=NORMALIZACAO concluida processados={} revisaoManual={}", processados, revisaoManual);
    }

    private List<UUID> buscarNormalizacoes(Connection conexao) throws SQLException {
        String sql = """
                SELECT l.id
                  FROM lead l
                 WHERE l.telefone IS NOT NULL
                   AND l.telefone !~ '^55[1-9][0-9]{9,10}$'
                   AND app_telefone_canonico(l.telefone, ?) ~ '^55[1-9][0-9]{9,10}$'
                   AND NOT EXISTS (
                       SELECT 1 FROM lead outro
                        WHERE outro.id <> l.id
                          AND app_telefone_canonico(outro.telefone, ?) = app_telefone_canonico(l.telefone, ?))
                   AND NOT EXISTS (
                       SELECT 1 FROM synapse_v73_runner_checkpoint cp
                        WHERE cp.fase = 'NORMALIZACAO'
                          AND cp.item_chave = l.id::text
                          AND cp.estado IN ('CONCLUIDO', 'IGNORADO'))
                 ORDER BY l.id
                 LIMIT ?
                """;
        List<UUID> ids = new ArrayList<>();
        try (PreparedStatement consulta = conexao.prepareStatement(sql)) {
            consulta.setString(1, ddiPadrao);
            consulta.setString(2, ddiPadrao);
            consulta.setString(3, ddiPadrao);
            consulta.setInt(4, tamanhoLote);
            try (ResultSet resultado = consulta.executeQuery()) {
                while (resultado.next()) {
                    ids.add(resultado.getObject(1, UUID.class));
                }
            }
        }
        return ids;
    }

    private void normalizar(Connection conexao, UUID id) throws SQLException {
        conexao.setAutoCommit(false);
        try {
            contextoServico(conexao);
            bloquearLead(conexao, id);
            executar(conexao, """
                    UPDATE lead
                       SET telefone = app_telefone_canonico(telefone, ?)
                     WHERE id = ?
                       AND telefone IS NOT NULL
                       AND telefone IS DISTINCT FROM app_telefone_canonico(telefone, ?)
                    """, ddiPadrao, id, ddiPadrao);
            conexao.commit();
            conexao.setAutoCommit(true);
        } catch (RuntimeException | SQLException erro) {
            conexao.rollback();
            conexao.setAutoCommit(true);
            throw erro;
        }
    }

    private static void bloquearLead(Connection conexao, UUID id) throws SQLException {
        try (PreparedStatement consulta = conexao.prepareStatement("SELECT id FROM lead WHERE id = ? FOR UPDATE")) {
            consulta.setObject(1, id);
            try (ResultSet resultado = consulta.executeQuery()) {
                // O lock é a operação relevante; o lead pode ter sido removido por uma execução
                // anterior, caso em que o UPDATE seguinte torna o checkpoint idempotente.
                resultado.next();
            }
        }
    }

    private int contarForaDaRegra(Connection conexao) throws SQLException {
        try (PreparedStatement consulta = conexao.prepareStatement("""
                SELECT count(*) FROM lead
                 WHERE telefone IS NOT NULL
                   AND telefone !~ '^55[1-9][0-9]{9,10}$'
                """); ResultSet resultado = consulta.executeQuery()) {
            resultado.next();
            return resultado.getInt(1);
        }
    }

    private boolean reservar(Connection conexao, String fase, String chave) throws SQLException {
        String sql = """
                INSERT INTO synapse_v73_runner_checkpoint
                    (fase, item_chave, estado, tentativas, lease_ate, ultimo_erro, atualizado_em)
                VALUES (?, ?, 'PROCESSANDO', 1, now() + (? * interval '1 millisecond'), NULL, now())
                ON CONFLICT (fase, item_chave) DO UPDATE
                    SET estado = 'PROCESSANDO',
                        tentativas = synapse_v73_runner_checkpoint.tentativas + 1,
                        lease_ate = now() + (? * interval '1 millisecond'),
                        ultimo_erro = NULL,
                        atualizado_em = now()
                  WHERE synapse_v73_runner_checkpoint.estado NOT IN ('CONCLUIDO', 'IGNORADO')
                    AND synapse_v73_runner_checkpoint.tentativas < ?
                    AND (synapse_v73_runner_checkpoint.lease_ate IS NULL
                         OR synapse_v73_runner_checkpoint.lease_ate < now())
                RETURNING 1
                """;
        long leaseMs = lease.toMillis();
        try (PreparedStatement comando = conexao.prepareStatement(sql)) {
            comando.setString(1, fase);
            comando.setString(2, chave);
            comando.setLong(3, leaseMs);
            comando.setLong(4, leaseMs);
            comando.setInt(5, maxTentativas);
            try (ResultSet resultado = comando.executeQuery()) {
                return resultado.next();
            }
        }
    }

    private void concluir(Connection conexao, String fase, String chave, String estado, String erro) throws SQLException {
        executar(conexao, """
                UPDATE synapse_v73_runner_checkpoint
                   SET estado = ?, lease_ate = NULL, ultimo_erro = ?, atualizado_em = now()
                 WHERE fase = ? AND item_chave = ?
                """, estado, erro, fase, chave);
    }

    private void registrarFalha(Connection conexao, String fase, String chave, Throwable erro) throws SQLException {
        String mensagem = erro.getClass().getSimpleName();
        try (PreparedStatement comando = conexao.prepareStatement("""
                UPDATE synapse_v73_runner_checkpoint
                   SET estado = 'FALHOU', lease_ate = NULL, ultimo_erro = ?, atualizado_em = now()
                 WHERE fase = ? AND item_chave = ?
                """)) {
            comando.setString(1, mensagem.length() > 500 ? mensagem.substring(0, 500) : mensagem);
            comando.setString(2, fase);
            comando.setString(3, chave);
            comando.executeUpdate();
        }
    }

    private void registrarConclusao(Connection conexao) throws SQLException {
        log.info("[FLYWAY_CONTROLADO] preparação da V73 concluída; checkpoints preservados até migration posterior");
    }

    private static void contextoServico(Connection conexao) throws SQLException {
        try (Statement comando = conexao.createStatement()) {
            comando.execute("SELECT set_config('app.papel', 'SERVICO', TRUE)");
        }
    }

    private static void executar(Connection conexao, String sql, Object... parametros) throws SQLException {
        try (PreparedStatement comando = conexao.prepareStatement(sql)) {
            for (int i = 0; i < parametros.length; i++) {
                comando.setObject(i + 1, parametros[i]);
            }
            comando.executeUpdate();
        }
    }

    private record Par(UUID perdedor, UUID sobrevivente, String canonico) {
        String chave() {
            return sobrevivente + ":" + perdedor;
        }
    }
}
