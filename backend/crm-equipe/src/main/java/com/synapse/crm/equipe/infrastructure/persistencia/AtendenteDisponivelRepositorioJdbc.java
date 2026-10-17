package com.synapse.crm.equipe.infrastructure.persistencia;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import com.synapse.crm.equipe.application.disponibilidade.AtendenteDisponivelRepositorio;
import com.synapse.crm.equipe.application.disponibilidade.EscopoDoRodizio;
import com.synapse.crm.equipe.domain.disponibilidade.AtendenteDisponivelParaIa;
import com.synapse.crm.equipe.domain.disponibilidade.DiagnosticoDoRodizio;
import com.synapse.crm.sharedkernel.persistencia.Pools;

/**
 * Elegibilidade e ordem do rodizio da IA em uma consulta agregada. As tabelas de disponibilidade e
 * usuario nao tem RLS; as agregacoes de {@code atendimento} e {@code evento_timeline} rodam no
 * contexto {@code SERVICO}, que e o unico que deve consultar a fila comercial inteira.
 */
@Repository
class AtendenteDisponivelRepositorioJdbc implements AtendenteDisponivelRepositorio {

    private static final String CHAVE_DISTRIBUICAO_SEQUENCIAL = "ia.distribuicao.sequencial";

    private static final String SQL_ESTRATEGIA =
            "SELECT valor FROM configuracao_automacao WHERE chave = ?";

    private static final String SQL_MENOR_CARGA =
            """
            WITH carga AS (
                SELECT a.atendente_id,
                       count(*) FILTER (WHERE a.status = 'EM_ATENDIMENTO') AS atendimentos_abertos
                  FROM atendimento a
                 WHERE a.atendente_id IS NOT NULL
                 GROUP BY a.atendente_id
            ), recebimentos AS (
                SELECT atendente_id, max(recebido_em) AS ultimo_recebido_em
                  FROM (
                        SELECT a.atendente_id, a.iniciado_em AS recebido_em
                          FROM atendimento a
                         WHERE a.atendente_id IS NOT NULL
                        UNION ALL
                        SELECT (e.dados ->> 'paraAtendenteId')::uuid, e.criado_em
                          FROM evento_timeline e
                         WHERE e.tipo = 'ATENDIMENTO_TRANSFERIDO'
                           AND e.dados ->> 'paraAtendenteId' IS NOT NULL
                        UNION ALL
                        SELECT e.ator_id, e.criado_em
                          FROM evento_timeline e
                         WHERE e.tipo = 'LEAD_TRANSFERIDO_POR_ENVIO'
                           AND e.ator_id IS NOT NULL
                  ) recebimento
                 GROUP BY atendente_id
            )
            SELECT u.id, u.nome, u.email
              FROM disponibilidade_atendente_ia d
              JOIN usuario u ON u.id = d.atendente_id
              LEFT JOIN carga c ON c.atendente_id = u.id
              LEFT JOIN recebimentos r ON r.atendente_id = u.id
             WHERE d.disponivel_para_ia = TRUE AND u.ativo = TRUE AND NOT u.usuario_teste
               -- Espelha PapelUsuario.recebeAtendimento()
               AND u.papel IN ('ATENDENTE', 'SUBGESTOR') AND u.status_presenca = 'ONLINE'
             ORDER BY COALESCE(c.atendimentos_abertos, 0),
                      r.ultimo_recebido_em NULLS FIRST,
                      u.id
            """;

    private static final String SQL_SEQUENCIAL =
            """
            WITH recebimentos AS (
                SELECT atendente_id, max(recebido_em) AS ultimo_recebido_em
                  FROM (
                        SELECT a.atendente_id, a.iniciado_em AS recebido_em
                          FROM atendimento a
                         WHERE a.atendente_id IS NOT NULL
                        UNION ALL
                        SELECT (e.dados ->> 'paraAtendenteId')::uuid, e.criado_em
                          FROM evento_timeline e
                         WHERE e.tipo = 'ATENDIMENTO_TRANSFERIDO'
                           AND e.dados ->> 'paraAtendenteId' IS NOT NULL
                        UNION ALL
                        SELECT e.ator_id, e.criado_em
                          FROM evento_timeline e
                         WHERE e.tipo = 'LEAD_TRANSFERIDO_POR_ENVIO'
                           AND e.ator_id IS NOT NULL
                  ) recebimento
                 GROUP BY atendente_id
            )
            SELECT u.id, u.nome, u.email
              FROM disponibilidade_atendente_ia d
              JOIN usuario u ON u.id = d.atendente_id
              LEFT JOIN recebimentos r ON r.atendente_id = u.id
             WHERE d.disponivel_para_ia = TRUE AND u.ativo = TRUE AND NOT u.usuario_teste
               -- Espelha PapelUsuario.recebeAtendimento()
               AND u.papel IN ('ATENDENTE', 'SUBGESTOR') AND u.status_presenca = 'ONLINE'
             ORDER BY r.ultimo_recebido_em NULLS FIRST,
                      u.id
            """;

    /**
     * Mesmos filtros, na mesma ordem, de {@link #SQL_MENOR_CARGA} e {@link #SQL_SEQUENCIAL}, mas contando em vez de
     * listar. LEFT JOIN de proposito: a consulta de elegibilidade usa JOIN, entao quem nao tem linha em
     * {@code disponibilidade_atendente_ia} nunca entra no rodizio, e e isso que a ultima coluna mede.
     */
    private static final String SQL_FUNIL =
            """
            SELECT count(*) FILTER (WHERE u.ativo = TRUE) AS ativos,
                   count(*) FILTER (WHERE u.ativo = TRUE
                                      AND u.papel IN ('ATENDENTE', 'SUBGESTOR')) AS com_papel_permitido,
                   count(*) FILTER (WHERE u.ativo = TRUE
                                      AND u.papel IN ('ATENDENTE', 'SUBGESTOR')
                                      AND d.disponivel_para_ia = TRUE) AS disponiveis_para_ia,
                   count(*) FILTER (WHERE u.ativo = TRUE
                                      AND u.papel IN ('ATENDENTE', 'SUBGESTOR')
                                      AND d.disponivel_para_ia = TRUE
                                      AND u.status_presenca = 'ONLINE') AS online,
                   count(*) FILTER (WHERE u.ativo = TRUE
                                      AND u.papel IN ('ATENDENTE', 'SUBGESTOR')
                                      AND d.atendente_id IS NULL) AS sem_registro
              FROM usuario u
              LEFT JOIN disponibilidade_atendente_ia d ON d.atendente_id = u.id
             WHERE NOT u.usuario_teste
            """;

    /**
     * Filtros de setor (D3a), acrescentados aos SQLs acima somente quando o escopo nao e GLOBAL. Com a capacidade
     * "setores" desligada o escopo e GLOBAL e as consultas executadas sao exatamente as constantes originais.
     */
    private static final String FILTRO_DO_SETOR =
            " AND EXISTS (SELECT 1 FROM usuario_setor us WHERE us.usuario_id = u.id AND us.setor_id = ?)";

    /** Vinculado direto (E231 (a)): com setores ligados, so quem esta num setor ativo que recebe da triagem. */
    private static final String FILTRO_EM_SETOR_QUE_RECEBE =
            """
             AND EXISTS (SELECT 1 FROM usuario_setor us JOIN setor s ON s.id = us.setor_id
                          WHERE us.usuario_id = u.id AND s.recebe_da_triagem = TRUE AND s.ativo = TRUE)""";

    private static final String FILTRO_SETOR_QUE_NAO_RECEBE =
            """
             AND NOT EXISTS (SELECT 1 FROM usuario_setor us JOIN setor s ON s.id = us.setor_id
                              WHERE us.usuario_id = u.id AND (s.recebe_da_triagem = FALSE OR s.ativo = FALSE))""";

    /** Mesmos filtros de elegibilidade das consultas acima, para um usuario so (chave primaria). */
    private static final String SQL_DISPONIVEL_POR_ID =
            """
            SELECT u.id, u.nome, u.email
              FROM disponibilidade_atendente_ia d
              JOIN usuario u ON u.id = d.atendente_id
             WHERE u.id = ?
               AND d.disponivel_para_ia = TRUE AND u.ativo = TRUE AND NOT u.usuario_teste
               -- Espelha PapelUsuario.recebeAtendimento()
               AND u.papel IN ('ATENDENTE', 'SUBGESTOR') AND u.status_presenca = 'ONLINE'
            """;

    private final JdbcTemplate jdbc;
    private final JdbcTemplate configuracaoJdbc;

    AtendenteDisponivelRepositorioJdbc(
            @Qualifier(Pools.CHAT_DATA_SOURCE) DataSource chatDataSource,
            @Qualifier(Pools.GENERAL_DATA_SOURCE) DataSource generalDataSource) {
        this.jdbc = new JdbcTemplate(chatDataSource);
        this.configuracaoJdbc = new JdbcTemplate(generalDataSource);
    }

    @Override
    public List<AtendenteDisponivelParaIa> listarDisponiveisParaIa(EscopoDoRodizio escopo) {
        String base = distribuicaoSequencialAtiva() ? SQL_SEQUENCIAL : SQL_MENOR_CARGA;
        // Antes do ORDER BY final: o filtro e mais uma condicao do WHERE, sem tocar nos CTEs nem na ordem.
        String sql = comEscopo(base, base.lastIndexOf("ORDER BY"), escopo);
        RowMapper<AtendenteDisponivelParaIa> mapeador = (linha, indice) -> new AtendenteDisponivelParaIa(
                UUID.fromString(linha.getString("id")), linha.getString("nome"), linha.getString("email"));
        // Escopo GLOBAL: a mesma chamada sem argumentos de sempre (nao troca o tipo de statement).
        return escopo.setorId() == null ? jdbc.query(sql, mapeador) : jdbc.query(sql, mapeador, escopo.setorId());
    }

    @Override
    public DiagnosticoDoRodizio diagnosticar(EscopoDoRodizio escopo) {
        String estrategia = distribuicaoSequencialAtiva()
                ? DiagnosticoDoRodizio.ESTRATEGIA_SEQUENCIAL
                : DiagnosticoDoRodizio.ESTRATEGIA_MENOR_CARGA;
        // O funil ja exclui contas de teste; o setor complementa esse mesmo WHERE.
        String sql = SQL_FUNIL + condicoes(escopo);
        RowMapper<DiagnosticoDoRodizio> mapeador = (linha, indice) -> new DiagnosticoDoRodizio(
                estrategia,
                linha.getLong("ativos"),
                linha.getLong("com_papel_permitido"),
                linha.getLong("disponiveis_para_ia"),
                linha.getLong("online"),
                linha.getLong("sem_registro"));
        return escopo.setorId() == null
                ? jdbc.queryForObject(sql, mapeador)
                : jdbc.queryForObject(sql, mapeador, escopo.setorId());
    }

    @Override
    public Optional<AtendenteDisponivelParaIa> disponivelPorId(UUID usuarioId, boolean comSetores) {
        // Os dois filtros: pertencer a um setor que recebe E nao estar em nenhum que nao recebe (se um dia o
        // UNIQUE(usuario_id) for removido, quem tem Diretoria junto com Vendas continua fora do vinculo direto).
        String sql = comSetores
                ? SQL_DISPONIVEL_POR_ID + FILTRO_EM_SETOR_QUE_RECEBE + FILTRO_SETOR_QUE_NAO_RECEBE
                : SQL_DISPONIVEL_POR_ID;
        return jdbc.query(
                        sql,
                        (linha, indice) -> new AtendenteDisponivelParaIa(
                                UUID.fromString(linha.getString("id")), linha.getString("nome"), linha.getString("email")),
                        usuarioId)
                .stream()
                .findFirst();
    }

    private static String comEscopo(String sql, int posicaoDoOrderBy, EscopoDoRodizio escopo) {
        if (escopo.eGlobal()) {
            return sql;
        }
        return sql.substring(0, posicaoDoOrderBy) + condicoes(escopo).strip() + "\n"
                + sql.substring(posicaoDoOrderBy);
    }

    private static String condicoes(EscopoDoRodizio escopo) {
        StringBuilder condicoes = new StringBuilder();
        if (escopo.setorId() != null) {
            condicoes.append(FILTRO_DO_SETOR);
        }
        if (escopo.excluirSetoresQueNaoRecebem()) {
            condicoes.append(FILTRO_SETOR_QUE_NAO_RECEBE);
        }
        return condicoes.toString();
    }

    /**
     * A chave e BOOLEAN para fechar os dois modos possiveis e aproveitar a validacao existente da
     * configuracao. Ausencia (por exemplo, durante uma atualizacao que ainda nao aplicou a
     * migration) conserva o comportamento historico de menor carga.
     */
    private boolean distribuicaoSequencialAtiva() {
        return configuracaoJdbc.query(
                SQL_ESTRATEGIA,
                resultados -> resultados.next() && Boolean.parseBoolean(resultados.getString("valor")),
                CHAVE_DISTRIBUICAO_SEQUENCIAL);
    }
}
