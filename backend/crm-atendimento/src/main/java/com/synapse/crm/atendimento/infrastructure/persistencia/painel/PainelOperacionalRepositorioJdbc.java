package com.synapse.crm.atendimento.infrastructure.persistencia.painel;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.synapse.crm.atendimento.application.painel.operacional.DestinatariosDoPainelOperacional;
import com.synapse.crm.atendimento.application.painel.operacional.FiltroOperacional;
import com.synapse.crm.atendimento.application.painel.operacional.PainelOperacional;
import com.synapse.crm.atendimento.application.painel.operacional.PainelOperacionalRepositorio;
import com.synapse.crm.atendimento.domain.mensagem.CitacaoDeMensagem;
import com.synapse.crm.core.domain.lead.VisibilidadeLead;
import com.synapse.crm.core.infrastructure.persistencia.TransacaoObrigatoria;
import com.synapse.crm.sharedkernel.persistencia.Pools;

/** Read model no pool geral. Mesma base para todos os números e para o drill-down. */
@Repository
class PainelOperacionalRepositorioJdbc implements PainelOperacionalRepositorio, DestinatariosDoPainelOperacional {
    private final NamedParameterJdbcTemplate jdbc;

    PainelOperacionalRepositorioJdbc(@Qualifier(Pools.GENERAL_DATA_SOURCE) DataSource fonte) {
        jdbc = new NamedParameterJdbcTemplate(fonte);
    }

    @Override
    public List<UUID> candidatosDaPresenca(UUID usuarioAlterado) {
        // Somente IDs de usuários ativos. Delegado limitado recebe só a alteração da própria presença.
        return jdbc.query("SELECT id FROM usuario WHERE ativo AND (papel IN ('GESTOR','SUBGESTOR','ADMINISTRADOR') OR id = :usuario)",
                new MapSqlParameterSource("usuario", usuarioAlterado), (r, n) -> r.getObject("id", UUID.class));
    }

    // Parte apenas dos ciclos abertos e encerrados no dia; NÃO do histórico completo.
    // Novas mensagens mantém a leitura por ciclo do dono atual, também nos ciclos anteriores.
    // Os acessos às mensagens se limitam a leads/ciclos que passaram pela Specification + RLS.
    static final String BASE = """
        -- Materializar o alcance ANTES de ler lead: evita avaliar políticas de participação
        -- em leads alheios que o recorte estrutural já descartou. OFFSET 0 preserva a barreira
        -- de planejamento; as duas tabelas continuam sob RLS, sem SECURITY DEFINER/BYPASSRLS.
        WITH convites_pessoais AS MATERIALIZED (
          SELECT convite.atendimento_id FROM pedido_entrada_atendimento convite
           WHERE convite.solicitante_id = :usuario AND convite.tipo = 'CONVITE' AND convite.status = 'PENDENTE'
             AND convite.solicitado_em > CAST(:agora AS timestamptz) - app_validade_pedido_entrada()
        ), compartilhados AS MATERIALIZED (
          SELECT p.atendimento_id FROM atendimento_participante p
           WHERE p.usuario_id = :usuario AND p.saiu_em IS NULL
          UNION
          SELECT atendimento_id FROM convites_pessoais
        ), ciclos AS MATERIALIZED (
          SELECT a.id, a.lead_id, a.atendente_id, a.status, a.iniciado_em, a.finalizado_em
            FROM atendimento a
           WHERE (a.status <> 'FINALIZADO' OR (a.finalizado_em >= :inicio AND a.finalizado_em < :fim
                 AND NOT EXISTS (SELECT 1 FROM atendimento aberto
                                  WHERE aberto.lead_id = a.lead_id AND aberto.status <> 'FINALIZADO')))
             AND (:amplo OR a.atendente_id = :usuario OR a.atendente_id IS NULL
                  OR a.id IN (SELECT atendimento_id FROM compartilhados))
        ), escolhidos AS MATERIALIZED (
          SELECT DISTINCT ON (a.lead_id) a.id, a.lead_id, a.atendente_id, a.status,
                 a.iniciado_em, a.finalizado_em, l.nome, l.empresa, l.foto_referencia, l.foto_url,
                 l.etapa_atendimento_id
            FROM ciclos a JOIN LATERAL (
              SELECT l.nome, l.empresa, l.foto_referencia, l.foto_url, l.etapa_atendimento_id
                FROM lead l WHERE l.id = a.lead_id
                  AND (:amplo OR l.atendente_responsavel_id = :usuario OR l.status_basico IN ('IA','FINALIZADO')
                       OR a.id IN (SELECT atendimento_id FROM compartilhados))
               OFFSET 0
            ) l ON true
           ORDER BY a.lead_id, (a.status = 'FINALIZADO'), a.iniciado_em DESC, a.id DESC
        ), nao_lidas AS (
          SELECT b.lead_id, count(m.id) AS quantidade
            FROM escolhidos b JOIN atendimento ciclo ON ciclo.lead_id = b.lead_id
            LEFT JOIN atendimento_leitura leitura ON leitura.atendimento_id = ciclo.id
              AND leitura.usuario_id = CASE WHEN :amplo AND b.atendente_id IS NOT NULL
                                            THEN b.atendente_id ELSE :usuario END
            JOIN mensagem m ON m.atendimento_id = ciclo.id AND m.remetente_tipo = 'LEAD'
              AND m.enviado_em > COALESCE(leitura.lido_ate, 'epoch'::timestamptz)
           GROUP BY b.lead_id
        ), mensagens_do_ciclo AS MATERIALIZED (
          SELECT m.id, m.atendimento_id, m.enviado_em, m.remetente_tipo, m.status_entrega
            FROM escolhidos b JOIN mensagem m ON m.atendimento_id = b.id
           WHERE b.status <> 'FINALIZADO'
        ), respostas AS (
          SELECT DISTINCT ON (m.atendimento_id) m.atendimento_id, m.enviado_em, m.id
            FROM mensagens_do_ciclo m
           WHERE m.remetente_tipo IN ('ATENDENTE','IA') AND m.status_entrega IN ('ENVIADO','ENTREGUE','LIDO')
           ORDER BY m.atendimento_id, m.enviado_em DESC, m.id DESC
        ), dialogos AS (
          SELECT DISTINCT ON (m.atendimento_id) m.atendimento_id, m.remetente_tipo
            FROM mensagens_do_ciclo m WHERE m.remetente_tipo IN ('LEAD','ATENDENTE')
           ORDER BY m.atendimento_id, m.enviado_em DESC, m.id DESC
        ), atividades AS (
          SELECT atendimento_id, max(enviado_em) AS enviado_em FROM mensagens_do_ciclo GROUP BY atendimento_id
        ), esperas AS (
          SELECT m.atendimento_id, min(m.enviado_em) AS enviado_em
            FROM mensagens_do_ciclo m LEFT JOIN respostas r ON r.atendimento_id = m.atendimento_id
           WHERE m.remetente_tipo = 'LEAD' AND (r.id IS NULL OR (m.enviado_em, m.id) > (r.enviado_em, r.id))
           GROUP BY m.atendimento_id
        ), base AS MATERIALIZED (
          SELECT b.id, b.lead_id, b.atendente_id, b.status, b.nome, b.empresa,
                 b.foto_referencia, b.foto_url, b.etapa_atendimento_id,
                 COALESCE(n.quantidade, 0) AS nao_lidas,
                 COALESCE(ultima.enviado_em, b.finalizado_em, b.iniciado_em) AS atividade_em,
                 CASE WHEN b.status <> 'FINALIZADO' THEN espera.enviado_em END AS espera_desde,
                 resposta.id IS NOT NULL AND espera.enviado_em IS NULL AS respondido,
                 b.id IN (SELECT atendimento_id FROM convites_pessoais) AS convite_pendente,
                 b.status = 'EM_ATENDIMENTO' AND (
                   ultimo_dialogo.remetente_tipo = 'LEAD' OR EXISTS (
                     SELECT 1 FROM convites_pessoais convite WHERE convite.atendimento_id = b.id
                   )) AS pendente
            FROM escolhidos b LEFT JOIN nao_lidas n ON n.lead_id = b.lead_id
            LEFT JOIN atividades ultima ON ultima.atendimento_id = b.id
            LEFT JOIN dialogos ultimo_dialogo ON ultimo_dialogo.atendimento_id = b.id
            LEFT JOIN esperas espera ON espera.atendimento_id = b.id
            LEFT JOIN respostas resposta ON resposta.atendimento_id = b.id
        )
        """;

    private static final String AGREGACAO = """
        , contagens AS (
          SELECT atendente_id,
                 COALESCE(sum(nao_lidas) FILTER (WHERE status <> 'FINALIZADO'), 0) AS novas_mensagens,
                 count(*) FILTER (WHERE status <> 'FINALIZADO' AND nao_lidas > 0) AS novas_conversas,
                 count(*) FILTER (WHERE status = 'EM_ATENDIMENTO') AS ativos,
                 count(*) FILTER (WHERE pendente) AS pendentes,
                 count(*) FILTER (WHERE status <> 'FINALIZADO' AND espera_desde <= :corte) AS sem_resposta,
                 count(*) FILTER (WHERE status = 'FINALIZADO') AS finalizados,
                 count(*) FILTER (WHERE status <> 'FINALIZADO') AS em_aberto,
                 count(*) FILTER (WHERE status = 'EM_IA') AS potenciais,
                 min(espera_desde) FILTER (WHERE status <> 'FINALIZADO' AND espera_desde <= :corte) AS espera_desde,
                 min(espera_desde + :limite * interval '1 minute')
                   FILTER (WHERE status <> 'FINALIZADO' AND espera_desde > :corte) AS proxima_transicao
            FROM filtrada GROUP BY atendente_id
        ), equipe AS (
          SELECT u.id, u.nome, CASE WHEN u.foto_referencia IS NOT NULL
                    THEN '/api/v1/usuarios/' || u.id::text || '/foto' END AS foto,
                 u.papel::text AS papel, u.status_presenca::text AS presenca, u.ativo AS elegivel
            FROM usuario u WHERE u.papel IN ('ATENDENTE','OPERADOR','SUBGESTOR','GESTOR','ADMINISTRADOR')
              AND (u.ativo OR EXISTS (SELECT 1 FROM contagens c WHERE c.atendente_id = u.id))
              AND (:amplo OR u.id = :usuario OR EXISTS (SELECT 1 FROM contagens c WHERE c.atendente_id = u.id))
          UNION ALL SELECT NULL::uuid, NULL::text, NULL::text, NULL::text, NULL::text, false
        )
        SELECT e.id, e.nome, e.foto, e.papel, e.presenca, e.elegivel,
               COALESCE(c.novas_mensagens, 0) AS novas_mensagens,
               COALESCE(c.novas_conversas, 0) AS novas_conversas,
               COALESCE(c.ativos, 0) AS ativos, COALESCE(c.pendentes, 0) AS pendentes,
               COALESCE(c.sem_resposta, 0) AS sem_resposta, COALESCE(c.finalizados, 0) AS finalizados,
               COALESCE(c.em_aberto, 0) AS em_aberto, COALESCE(c.potenciais, 0) AS potenciais,
               c.espera_desde, c.proxima_transicao
          FROM equipe e LEFT JOIN contagens c ON c.atendente_id IS NOT DISTINCT FROM e.id
         ORDER BY (e.id IS NULL), lower(e.nome), e.id
        """;

    @Override
    public int limiteMinutos() {
        TransacaoObrigatoria.exigir("limite do painel operacional");
        Integer limite = jdbc.queryForObject("SELECT valor::integer FROM configuracao_automacao WHERE chave = 'atendimentos.limite_sem_resposta_minutos'",
                new MapSqlParameterSource(), Integer.class);
        if (limite == null || limite <= 0) throw new IllegalStateException("limite do painel operacional inválido");
        return limite;
    }

    @Override
    public List<PainelOperacional.Linha> agregar(VisibilidadeLead visibilidade, UUID usuario,
            Instant agora, Instant inicio, Instant fim, int limite, FiltroOperacional filtro) {
        TransacaoObrigatoria.exigir("agregação do painel operacional");
        prepararPlanoLocal();
        return jdbc.query(baseFiltrada(filtro) + AGREGACAO,
                parametrosDoFiltro(parametros(visibilidade, usuario, agora, inicio, fim, limite), filtro), (r, n) ->
                new PainelOperacional.Linha(r.getObject("id", UUID.class), r.getString("nome"), r.getString("foto"),
                        r.getString("papel"), r.getString("presenca"), r.getObject("id") == null, r.getBoolean("elegivel"),
                        instante(r, "proxima_transicao"), new PainelOperacional.Contagens(
                                r.getLong("novas_mensagens"), r.getLong("novas_conversas"), r.getLong("ativos"),
                                r.getLong("pendentes"), r.getLong("sem_resposta"), r.getLong("finalizados"),
                                r.getLong("em_aberto"), r.getLong("potenciais"), instante(r, "espera_desde"))));
    }

    @Override
    public List<PainelOperacional.Conversa> listar(VisibilidadeLead visibilidade, UUID usuario,
            Instant agora, Instant inicio, Instant fim, int limite, FiltroOperacional filtro,
            Instant depoisDe, UUID depoisId, int tamanho) {
        TransacaoObrigatoria.exigir("fila do painel operacional");
        prepararPlanoLocal();
        var parametros = parametrosDoFiltro(parametros(visibilidade, usuario, agora, inicio, fim, limite), filtro)
                .addValue("depois", depoisDe == null ? null : Timestamp.from(depoisDe))
                .addValue("depoisId", depoisId).addValue("tamanho", tamanho)
                .addValue("limitePrevia", CitacaoDeMensagem.LIMITE_PREVIA);
        String criterio = switch (filtro.fila()) {
            case EM_ABERTO -> "b.status <> 'FINALIZADO'";
            case NOVAS -> "b.status <> 'FINALIZADO' AND b.nao_lidas > 0";
            case ATIVOS -> "b.status = 'EM_ATENDIMENTO'";
            case PENDENTES -> "b.pendente";
            case SEM_RESPOSTA -> "b.status <> 'FINALIZADO' AND b.espera_desde <= :corte";
            case FINALIZADOS -> "b.status = 'FINALIZADO'";
            case POTENCIAIS -> "b.status = 'EM_IA'";
        };
        String sql = baseFiltrada(filtro) + """
            , pagina AS MATERIALIZED (
              SELECT b.id, b.lead_id, b.nome, b.empresa, b.foto_referencia, b.foto_url,
                     b.status, b.atendente_id, b.nao_lidas, b.atividade_em, b.espera_desde,
                     b.etapa_atendimento_id, b.pendente, b.respondido, b.convite_pendente
                FROM filtrada b WHERE
            """ + criterio
                + " AND (:depois::timestamptz IS NULL OR (b.atividade_em, b.id) < (:depois, :depoisId::uuid))"
                + " ORDER BY b.atividade_em DESC, b.id DESC LIMIT :tamanho) "
                + """
            SELECT b.id, b.lead_id, b.nome, b.empresa,
                   CASE WHEN b.foto_referencia IS NOT NULL THEN '/api/v1/leads/' || b.lead_id::text || '/foto'
                        ELSE b.foto_url END AS foto,
                   b.status::text, b.atendente_id, u.nome AS atendente_nome,
                   b.nao_lidas, b.atividade_em, b.espera_desde, e.nome AS etapa_nome, e.cor_visual AS etapa_cor,
                   CASE WHEN b.pendente THEN 'PENDENTE' ELSE b.status::text END AS situacao,
                   ultima.previa, ultima.tipo::text AS ultima_tipo, ultima.enviado_em AS ultima_em, b.respondido,
                   canal.tipo::text AS canal_tipo, b.convite_pendente
              FROM pagina b LEFT JOIN usuario u ON u.id = b.atendente_id
              LEFT JOIN atendimento ciclo ON ciclo.id = b.id
              LEFT JOIN canal ON canal.id = ciclo.canal_id
              LEFT JOIN etapa_atendimento e ON e.id = b.etapa_atendimento_id
              LEFT JOIN LATERAL (
                SELECT CASE WHEN m.tipo = 'TEXTO' THEN left(m.conteudo, :limitePrevia) END AS previa,
                       m.tipo, m.enviado_em FROM mensagem m WHERE m.atendimento_id = b.id
                 ORDER BY m.enviado_em DESC, m.id DESC LIMIT 1
              ) ultima ON true
             ORDER BY b.atividade_em DESC, b.id DESC
            """;
        return jdbc.query(sql, parametros, (r, n) -> new PainelOperacional.Conversa(
                r.getObject("id", UUID.class), r.getObject("lead_id", UUID.class), r.getString("nome"),
                r.getString("foto"), r.getString("empresa"), r.getString("status"),
                r.getObject("atendente_id", UUID.class), r.getString("atendente_nome"),
                r.getLong("nao_lidas"), instante(r, "atividade_em"), instante(r, "espera_desde"),
                r.getString("etapa_nome"), r.getString("etapa_cor"), r.getString("situacao"),
                CitacaoDeMensagem.sanitizar(r.getString("previa")), r.getString("ultima_tipo"),
                instante(r, "ultima_em"), r.getBoolean("respondido"), r.getString("canal_tipo"), r.getBoolean("convite_pendente")));
    }

    private static String baseFiltrada(FiltroOperacional filtro) {
        String sinal = switch (filtro.sinal()) {
            case TODOS -> "true";
            case NOVAS -> "b.status <> 'FINALIZADO' AND b.nao_lidas > 0";
            case SEM_RESPOSTA -> "b.status <> 'FINALIZADO' AND b.espera_desde <= :corte";
        };
        return BASE + ", filtrada AS (SELECT b.id, b.lead_id, b.atendente_id, b.status, b.nome, b.empresa,"
                + " b.foto_referencia, b.foto_url, b.etapa_atendimento_id, b.nao_lidas, b.atividade_em, b.espera_desde, b.pendente, b.respondido, b.convite_pendente"
                + " FROM base b WHERE " + sinal
                + (filtro.entrada() ? " AND b.atendente_id IS NULL" : " AND (:dono::uuid IS NULL OR b.atendente_id = :dono)")
                + " AND (:etapa::uuid IS NULL OR b.etapa_atendimento_id = :etapa)"
                + " AND (b.nome ILIKE :busca ESCAPE '!' OR COALESCE(b.empresa, '') ILIKE :busca ESCAPE '!')) ";
    }

    private void prepararPlanoLocal() {
        // EXPLAIN local demonstrou custo de compilação JIT incompatível com leituras curtas.
        // Só esta transação de read model: não muda servidor, RLS, escrita ou pool do chat.
        jdbc.getJdbcTemplate().execute("SET LOCAL jit = off");
    }

    private static MapSqlParameterSource parametrosDoFiltro(MapSqlParameterSource parametros, FiltroOperacional filtro) {
        return parametros.addValue("busca", "%" + filtro.busca().replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%")
                .addValue("etapa", filtro.etapaId()).addValue("dono", filtro.atendenteId());
    }

    private static MapSqlParameterSource parametros(VisibilidadeLead visibilidade, UUID usuario,
            Instant agora, Instant inicio, Instant fim, int limite) {
        boolean amplo = switch (visibilidade) {
            case VisibilidadeLead.Ampla ignorada -> true;
            case VisibilidadeLead.DoAtendente restrita -> {
                if (!restrita.atendenteId().equals(usuario)) throw new IllegalArgumentException("Specification incompatível");
                yield false;
            }
        };
        return new MapSqlParameterSource("usuario", usuario).addValue("amplo", amplo)
                .addValue("agora", Timestamp.from(agora)).addValue("inicio", Timestamp.from(inicio))
                .addValue("fim", Timestamp.from(fim)).addValue("limite", limite)
                .addValue("corte", Timestamp.from(agora.minusSeconds(limite * 60L)));
    }

    private static Instant instante(ResultSet r, String coluna) throws SQLException {
        Timestamp valor = r.getTimestamp(coluna);
        return valor == null ? null : valor.toInstant();
    }
}
