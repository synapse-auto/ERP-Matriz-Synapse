package com.synapse.crm.atendimento.infrastructure.persistencia.painel;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import com.synapse.crm.atendimento.application.painel.CartaoAtendimento;
import com.synapse.crm.atendimento.application.painel.ListaDoPainel;
import com.synapse.crm.atendimento.application.painel.PainelDeAtendimentosRepositorio;
import com.synapse.crm.atendimento.application.painel.VisaoAtendimento;
import com.synapse.crm.atendimento.domain.atendimento.StatusAtendimento;
import com.synapse.crm.core.infrastructure.persistencia.TransacaoObrigatoria;
import com.synapse.crm.sharedkernel.persistencia.Pools;

/**
 * Le pelo pool do chat, igual a {@code AtendimentoRepositorioJdbc} — mesma origem de dados do
 * caminho critico, mesmo motivo (RNF-CRM-01): um relatorio pesado nao pode roubar conexao do chat, e
 * o inverso tambem vale, esta consulta nao pode competir pelo pool geral.
 *
 * <p>{@code mensagem} nao tem RLS propria (so {@code lead}/{@code atendimento}/{@code lembrete}/
 * {@code mensagem_programada}, desde a V12). Seguro aqui porque o {@code FROM} sempre parte de
 * {@code atendimento}, que tem a politica — os dois {@code LEFT JOIN LATERAL} em {@code mensagem} so
 * alcancam linhas de atendimentos que a RLS ja deixou passar. O {@code ultima_lead} passou a
 * atravessar {@code atendimento} pelo {@code lead_id} (como o {@code nao_lidas} sempre fez, E114):
 * continua gated pela mesma RLS de {@code atendimento}, porque a linha base {@code a} so entra se o
 * lead for visivel, e os demais atendimentos do mesmo lead herdam essa visibilidade.
 */
@Repository
class PainelDeAtendimentosRepositorioJdbc implements PainelDeAtendimentosRepositorio {

    // E97: a foto entregue pela integracao ganha da URL externa digitada na ficha. Mesmo
    // formato de ChatInternoRepositorioJdbc/FeedbackRepositorioJdbc com o avatar do usuario:
    // caminho relativo autenticado, nunca URL de storage.
    // Visivel ao pacote (como ORIGEM e agrupar) so para o teste de equivalencia da E224 (B1), que recompoe a consulta
    // antiga e a compara com a nova. Nada fora deste pacote deve usar.
    static final String CAMPOS =
            """
            a.id AS atendimento_id, a.lead_id, l.nome AS lead_nome,
            CASE WHEN l.foto_referencia IS NOT NULL
                 THEN '/api/v1/leads/' || l.id::text || '/foto'
                 ELSE l.foto_url END AS lead_foto_url,
            l.empresa AS lead_empresa, l.codigo AS lead_codigo, c.tipo AS canal_tipo,
            l.etapa_atendimento_id, et.nome AS etapa_nome,
            et.cor_visual AS etapa_cor, a.status, dono.atendente_id, u.nome AS atendente_nome,
            a.em_negociacao, a.resultado_venda, a.valor_venda, a.venda_registrada_por_id,
            venda_usuario.nome AS venda_registrada_por_nome, a.venda_registrada_em, a.origem_resultado_venda,
            a.iniciado_em AS iniciado_em,
            ativo.id AS atendimento_ativo_id,
            ultima.conteudo AS ultima_mensagem_preview,
            ultima.remetente_tipo AS ultima_mensagem_remetente_tipo,
            ultima.enviado_em AS ultima_mensagem_em,
            -- E121: mesma fonte do envio (lead.ultima_mensagem_do_lead_em). A LATERAL da E114
            -- saiu de proposito — duas definicoes de "janela aberta" divergem com o tempo.
            l.ultima_mensagem_do_lead_em AS ultima_mensagem_do_lead_em,
            (
                SELECT COALESCE(SUM((
                    SELECT count(*) FROM mensagem nao_lida
                     WHERE nao_lida.atendimento_id = atendimento_do_lead.id
                       AND nao_lida.remetente_tipo = 'LEAD'
                       AND nao_lida.enviado_em > COALESCE(leitura_do_lead.lido_ate, 'epoch'::timestamptz)
                )), 0)
                  FROM atendimento atendimento_do_lead
                  LEFT JOIN atendimento_leitura leitura_do_lead
                    ON leitura_do_lead.atendimento_id = atendimento_do_lead.id
                   -- Gestao acompanha a leitura do dono exibido no cartao. Sem dono, a
                   -- leitura continua pessoal; atendentes/participantes mantem sua propria.
                   AND leitura_do_lead.usuario_id = COALESCE(
                       CASE WHEN app_enxerga_todos_os_leads() THEN dono.atendente_id END, ?)
                 WHERE atendimento_do_lead.lead_id = a.lead_id
            ) AS nao_lidas,
            ROW_NUMBER() OVER (
                PARTITION BY a.lead_id
                ORDER BY COALESCE(ultima.enviado_em, a.iniciado_em) DESC, a.iniciado_em DESC, a.id DESC
            ) AS linha_do_lead
            """;

    /**
     * Tudo que o cartao junta a partir de {@code a}. Fica separado do {@code FROM atendimento a} porque a fase 2 da
     * listagem parte das ids escolhidas (E224, B1) e a busca pontual parte de {@code atendimento}: o texto dos joins e
     * um so, e as duas formas nao podem divergir.
     */
    private static final String JUNCOES_DO_CARTAO =
            """
            JOIN lead l ON l.id = a.lead_id
            LEFT JOIN canal c ON c.id = a.canal_id
            LEFT JOIN etapa_atendimento et ON et.id = l.etapa_atendimento_id
            LEFT JOIN LATERAL (
                SELECT aberto.id, aberto.status, aberto.atendente_id FROM atendimento aberto
                 WHERE aberto.lead_id = a.lead_id AND aberto.status <> 'FINALIZADO'
                 ORDER BY COALESCE((
                     SELECT max(m_aberto.enviado_em) FROM mensagem m_aberto
                      WHERE m_aberto.atendimento_id = aberto.id
                 ), aberto.iniciado_em) DESC, aberto.iniciado_em DESC, aberto.id DESC
                 LIMIT 1
            ) ativo ON true
            -- E206: o cartao abre o atendimento ativo, entao o dono exibido vem dele. Sem isso, a
            -- linha da ultima mensagem (ja FINALIZADA) exibia o dono do ciclo anterior enquanto o
            -- cabecalho, via /estado do ativo, exibia o atual. O status segue sendo o da linha.
            CROSS JOIN LATERAL (
                SELECT CASE WHEN ativo.id IS NULL THEN a.atendente_id ELSE ativo.atendente_id END
                           AS atendente_id
            ) dono
            LEFT JOIN usuario u ON u.id = dono.atendente_id
            LEFT JOIN usuario venda_usuario ON venda_usuario.id = a.venda_registrada_por_id
            LEFT JOIN LATERAL (
                SELECT conteudo, remetente_tipo, enviado_em FROM mensagem m
                 WHERE m.atendimento_id = a.id ORDER BY m.enviado_em DESC LIMIT 1
            ) ultima ON true
            """;

    static final String ORIGEM = "FROM atendimento a\n" + JUNCOES_DO_CARTAO;

    /**
     * E209 — primeira fase da listagem: so o que decide QUAL atendimento representa o lead e em que
     * posicao ele aparece. O {@code ROW_NUMBER} por lead impede o Postgres de empurrar o
     * {@code LIMIT} para dentro da consulta, entao tudo que esta aqui roda para cada atendimento da
     * visao (inclusive o historico de leads com varios ciclos). Por isso nada do cartao entra aqui:
     * nao lidas, atendimento ativo, dono, etapa e previa da mensagem sao calculados depois, so para
     * os atendimentos escolhidos.
     *
     * <p>{@code sem_atendimento_aberto} e o mesmo {@link #GRUPO_FINALIZADO} da segunda fase: o
     * {@code LEFT JOIN LATERAL ativo} de {@link #ORIGEM} devolve linha se, e somente se, existir um
     * atendimento nao finalizado do lead que a RLS deixe ver — exatamente este {@code EXISTS}.
     */
    private static final String CAMPOS_ESCOLHA =
            """
            a.id AS atendimento_id,
            ultima.enviado_em AS ultima_mensagem_em,
            CASE WHEN EXISTS (
                SELECT 1 FROM atendimento aberto
                 WHERE aberto.lead_id = a.lead_id AND aberto.status <> 'FINALIZADO'
            ) THEN 0 ELSE 1 END AS sem_atendimento_aberto,
            ROW_NUMBER() OVER (
                PARTITION BY a.lead_id
                ORDER BY COALESCE(ultima.enviado_em, a.iniciado_em) DESC, a.iniciado_em DESC, a.id DESC
            ) AS linha_do_lead
            """;

    /**
     * O {@code JOIN lead} e filtro, nao projecao: {@code lead} tem RLS propria, e para o atendente
     * ela esconde o lead que um colega esta atendendo mesmo quando o ciclo FINALIZADO dele continua
     * visivel em {@code atendimento}. Sem este join a escolha incluiria leads que a segunda fase
     * descarta, e a pagina viria com menos cartoes do que o limite.
     */
    private static final String ORIGEM_ESCOLHA =
            """
            FROM atendimento a
            JOIN lead l ON l.id = a.lead_id
            LEFT JOIN LATERAL (
                SELECT enviado_em FROM mensagem m
                 WHERE m.atendimento_id = a.id ORDER BY m.enviado_em DESC LIMIT 1
            ) ultima ON true
            """;

    private static final String ORDEM_ESCOLHA =
            " ORDER BY sem_atendimento_aberto ASC, ultima_mensagem_em DESC NULLS LAST, atendimento_id DESC";

    private static final String GRUPO_FINALIZADO =
            "CASE WHEN atendimento_ativo_id IS NULL THEN 1 ELSE 0 END";

    private static final String ORDEM = " ORDER BY " + GRUPO_FINALIZADO
            + " ASC, ultima_mensagem_em DESC NULLS LAST, atendimento_id DESC";

    /**
     * As mesmas quatro condicoes de visao usadas em {@link #listar}, isoladas para que a contagem
     * (E17b §Bloco 6) monte {@code SELECT COUNT(*)} sobre exatamente o mesmo {@code WHERE} — nunca uma
     * segunda decisao de "o que e visivel" escrita a parte.
     */
    static final String WHERE_ATIVOS = " WHERE EXISTS (SELECT 1 FROM atendimento visivel"
            + " WHERE visivel.lead_id = a.lead_id AND visivel.status = 'EM_ATENDIMENTO'"
            + " AND visivel.atendente_id = ?)";

    static final String WHERE_PENDENTES_PROPRIOS = " WHERE EXISTS (SELECT 1 FROM atendimento visivel"
            + " LEFT JOIN LATERAL (SELECT remetente_tipo FROM mensagem m_visivel"
            + " WHERE m_visivel.atendimento_id = visivel.id"
            + " AND m_visivel.remetente_tipo IN ('LEAD','ATENDENTE')"
            + " ORDER BY m_visivel.enviado_em DESC LIMIT 1) ultima_visivel"
            + " ON true WHERE visivel.lead_id = a.lead_id AND visivel.status = 'EM_ATENDIMENTO'"
            + " AND ((visivel.atendente_id = ? AND ultima_visivel.remetente_tipo = 'LEAD')"
            + " OR EXISTS (SELECT 1 FROM pedido_entrada_atendimento convite"
            + " WHERE convite.atendimento_id = visivel.id"
            + " AND convite.solicitante_id = ? AND convite.tipo = 'CONVITE'"
            + " AND convite.status = 'PENDENTE'"
            + " AND convite.solicitado_em > now() - app_validade_pedido_entrada())) )";

    static final String WHERE_PENDENTES_TODOS = " WHERE EXISTS (SELECT 1 FROM atendimento visivel"
            + " LEFT JOIN LATERAL (SELECT remetente_tipo FROM mensagem m_visivel"
            + " WHERE m_visivel.atendimento_id = visivel.id"
            + " AND m_visivel.remetente_tipo IN ('LEAD','ATENDENTE')"
            + " ORDER BY m_visivel.enviado_em DESC LIMIT 1) ultima_visivel"
            + " ON true WHERE visivel.lead_id = a.lead_id AND visivel.status = 'EM_ATENDIMENTO'"
            + " AND ultima_visivel.remetente_tipo = 'LEAD')";

    static final String WHERE_POTENCIAIS = " WHERE EXISTS (SELECT 1 FROM atendimento visivel"
            + " WHERE visivel.lead_id = a.lead_id AND visivel.status = 'EM_IA')";

    /**
     * "Todos" mostra e conta so leads com atendimento aberto (EM_ATENDIMENTO/EM_IA) — o inverso exato
     * de {@link #WHERE_SEM_ATENDIMENTO_ABERTO} (E136). Quem quiser ver o historico com finalizados usa
     * a aba FINALIZADOS, dedicada. Lista e contagem usam esta mesma constante — nao dessincronizar de
     * novo (ver 194eded0/5712722b no historico do git).
     */
    static final String WHERE_TODOS_ATIVOS = " WHERE EXISTS (SELECT 1 FROM atendimento aberto"
            + " WHERE aberto.lead_id = a.lead_id"
            + " AND aberto.status IN ('EM_ATENDIMENTO', 'EM_IA'))";

    /**
     * Cartao finalizado = lead sem atendimento aberto (E136 / Bloco 0). Nao e
     * {@code a.status = 'FINALIZADO'} — isso duplicaria leads que ja tem outro aberto.
     */
    private static final String WHERE_SEM_ATENDIMENTO_ABERTO = " WHERE NOT EXISTS (SELECT 1 FROM atendimento aberto"
            + " WHERE aberto.lead_id = a.lead_id"
            + " AND aberto.status IN ('EM_ATENDIMENTO', 'EM_IA'))";

    /**
     * E145: finalizados formam o balcao de reativacao. A RLS ja limita as linhas alcancaveis; o
     * painel nao pode reaplicar o recorte do ultimo responsavel, senao o atendente nao consegue
     * sequer encontrar o cartao para assumir o lead de um colega.
     */
    private static final String WHERE_FINALIZADOS = WHERE_SEM_ATENDIMENTO_ABERTO;

    /**
     * E225: a lista sem paginacao ({@code GET /api/v1/atendimentos?visao=}) deixou de ser ilimitada. A primeira fase ganha a
     * mesma ordem e um {@code LIMIT} (o ultimo {@code ?} de cada consulta), entao devolve os {@code N} primeiros cartoes na
     * mesma ordem de antes; com {@code N} maior que o total o resultado e identico ao de antes. {@code N} e
     * {@code synapse.painel.listagem-maxima}. Quem precisa de mais usa a inbox paginada.
     */
    private static final String ESCOLHA_LIMITADA = ORDEM_ESCOLHA + " LIMIT ?";

    private static final String SQL_ATIVOS = cartoesDe(escolher(WHERE_ATIVOS) + ESCOLHA_LIMITADA);

    private static final String SQL_PENDENTES_PROPRIOS = cartoesDe(escolher(WHERE_PENDENTES_PROPRIOS) + ESCOLHA_LIMITADA);

    private static final String SQL_PENDENTES_TODOS = cartoesDe(escolher(WHERE_PENDENTES_TODOS) + ESCOLHA_LIMITADA);

    private static final String SQL_POTENCIAIS = cartoesDe(escolher(WHERE_POTENCIAIS) + ESCOLHA_LIMITADA);

    private static final String SQL_TODOS = cartoesDe(escolher(WHERE_TODOS_ATIVOS) + ESCOLHA_LIMITADA);

    private static final String SQL_FINALIZADOS = cartoesDe(escolher(WHERE_FINALIZADOS) + ESCOLHA_LIMITADA);

    private static final String SQL_POR_ATENDIMENTO = agrupar(CAMPOS + ORIGEM + " WHERE a.id = ?");

    private static final String SQL_POR_LEAD = agrupar(CAMPOS + ORIGEM + " WHERE a.lead_id = ?");

    private static final String COLUNAS_CARTAO =
            "atendimento_id, lead_id, lead_nome, lead_foto_url, lead_empresa, lead_codigo, canal_tipo, "
                    + "etapa_atendimento_id, etapa_nome, etapa_cor, status, atendente_id, atendente_nome, "
                    + "iniciado_em, atendimento_ativo_id, ultima_mensagem_preview, ultima_mensagem_remetente_tipo, "
                    + "ultima_mensagem_em, ultima_mensagem_do_lead_em, nao_lidas, em_negociacao, "
                    + "resultado_venda, valor_venda, venda_registrada_por_id, venda_registrada_por_nome, "
                    + "venda_registrada_em, origem_resultado_venda, linha_do_lead";

    /**
     * E225 (B5): as contagens das abas de andamento filtram direto as linhas abertas ({@code a.status IN (...)}) em vez
     * de, para cada linha de {@code atendimento}, procurar com {@code EXISTS} outro atendimento aberto do mesmo lead.
     * No HML a contagem de TODOS era 68% do tempo de todas as contagens (1.831 chamadas, 540 ms de media).
     *
     * <p><b>Equivalencia (provada pelo teste de integracao, nao so por argumento).</b> A contagem antiga contava os leads
     * (visiveis pela RLS de {@code lead}) que tinham alguma linha {@code a} visivel e um atendimento aberto
     * <em>visivel</em> do mesmo lead. O atendimento aberto e, ele proprio, uma linha de {@code atendimento}; filtrar
     * {@code a} pelo status aberto devolve exatamente os mesmos leads, e {@code COUNT(DISTINCT a.lead_id)} continua contando
     * cada lead uma vez (inclusive com dois atendimentos abertos no mesmo lead). {@code a} e {@code aberto}/{@code visivel}
     * sao a mesma tabela sob a mesma RLS: um lead cujo unico atendimento aberto e de um colega (invisivel) fica de fora nas
     * duas formas. Em PENDENTES, a LATERAL da ultima mensagem sai do {@code EXISTS} e passa a rodar so para as linhas
     * abertas. FINALIZADOS nao tem forma equivalente (e um {@code NOT EXISTS}) e continua como era.
     *
     * <p>A listagem continua com os {@code WHERE_*} por lead, que sao a fonte da fase 1; o teste de equivalencia e o que
     * impede lista e contagem de se afastarem.
     */
    static final String SQL_CONTAR_ATIVOS = contar(" WHERE a.status = 'EM_ATENDIMENTO' AND a.atendente_id = ?");

    static final String SQL_CONTAR_PENDENTES_PROPRIOS = contar(" LEFT JOIN LATERAL (SELECT remetente_tipo FROM mensagem"
            + " m_visivel WHERE m_visivel.atendimento_id = a.id AND m_visivel.remetente_tipo IN ('LEAD','ATENDENTE')"
            + " ORDER BY m_visivel.enviado_em DESC LIMIT 1) ultima_visivel ON true"
            + " WHERE a.status = 'EM_ATENDIMENTO'"
            + " AND ((a.atendente_id = ? AND ultima_visivel.remetente_tipo = 'LEAD')"
            + " OR EXISTS (SELECT 1 FROM pedido_entrada_atendimento convite"
            + " WHERE convite.atendimento_id = a.id"
            + " AND convite.solicitante_id = ? AND convite.tipo = 'CONVITE'"
            + " AND convite.status = 'PENDENTE'"
            + " AND convite.solicitado_em > now() - app_validade_pedido_entrada()))");

    static final String SQL_CONTAR_PENDENTES_TODOS = contar(" LEFT JOIN LATERAL (SELECT remetente_tipo FROM mensagem"
            + " m_visivel WHERE m_visivel.atendimento_id = a.id AND m_visivel.remetente_tipo IN ('LEAD','ATENDENTE')"
            + " ORDER BY m_visivel.enviado_em DESC LIMIT 1) ultima_visivel ON true"
            + " WHERE a.status = 'EM_ATENDIMENTO' AND ultima_visivel.remetente_tipo = 'LEAD'");

    static final String SQL_CONTAR_POTENCIAIS = contar(" WHERE a.status = 'EM_IA'");

    static final String SQL_CONTAR_TODOS = contar(" WHERE a.status IN ('EM_ATENDIMENTO', 'EM_IA')");

    static final String SQL_CONTAR_FINALIZADOS = contar(WHERE_FINALIZADOS);

    static String agrupar(String consultaInterna) {
        return "SELECT " + COLUNAS_CARTAO + " FROM (SELECT " + consultaInterna + ") cartoes"
                + " WHERE linha_do_lead = 1" + ORDEM;
    }

    /**
     * Primeira fase (E209): ids dos atendimentos que representam cada lead da visao. O chamador pode
     * acrescentar {@code AND ...} (cursor), ordem e limite — a consulta termina no {@code WHERE}.
     */
    private static String escolher(String filtro) {
        return "SELECT atendimento_id FROM (SELECT " + CAMPOS_ESCOLHA + ORIGEM_ESCOLHA + filtro
                + ") escolha WHERE linha_do_lead = 1";
    }

    /**
     * Segunda fase (E209): o cartao completo so dos atendimentos escolhidos. Como sobra um
     * atendimento por lead, o {@code ROW_NUMBER} de {@link #CAMPOS} vale 1 em toda linha; o
     * {@link #agrupar} continua aqui apenas para manter a mesma projecao e a mesma {@link #ORDEM}.
     * Tudo roda no mesmo comando, portanto no mesmo snapshot e sob a mesma RLS das duas fases.
     *
     * <p>E224 (B1): as ids escolhidas entram como item do {@code FROM}, com {@code JOIN} explicito a
     * {@code atendimento}, e nao mais como {@code a.id IN (subconsulta com LIMIT)}. Medido pelo responsavel no EXPLAIN de
     * 06/10, o {@code IN} era planejado como semi join dirigido pelas ~3.900 linhas de {@code atendimento} ja unidas
     * (as duas {@code LATERAL}, {@code ativo} e {@code ultima}, rodavam com {@code loops=3915}), e so depois filtrado
     * pelas 101 ids. Com a subconsulta no {@code FROM}, ela e uma relacao pequena (no maximo {@code LIMIT} linhas) e o
     * resto do cartao e consultado por id. O resultado e o mesmo: mesmas linhas, mesma ordem, mesmo
     * {@code linha_do_lead} (a janela continua vendo so os atendimentos escolhidos). A ordem dos {@code ?} no texto
     * tambem nao muda ({@code nao_lidas} vem antes das ids). Nao altera nenhuma politica de RLS: {@code atendimento}
     * e {@code lead} continuam sendo lidos pelas mesmas tabelas, sob o mesmo contexto.
     */
    static String cartoesDe(String atendimentosEscolhidos) {
        return agrupar(CAMPOS + "FROM (" + atendimentosEscolhidos + ") escolhidos\n"
                + "JOIN atendimento a ON a.id = escolhidos.atendimento_id\n" + JUNCOES_DO_CARTAO);
    }

    /**
     * E209 — {@code COUNT(DISTINCT a.lead_id)} e o mesmo numero que a listagem devolve, sem
     * {@code ROW_NUMBER} nem a lateral da ultima mensagem. Todas as condicoes de visao dependem so
     * de {@code a.lead_id} (sao {@code EXISTS} por lead), e {@code linha_do_lead = 1} escolhe
     * exatamente uma linha em cada particao nao vazia de lead — qualquer que seja a ordem. Logo, o
     * total de cartoes e o total de leads distintos entre as linhas que a RLS e o filtro deixam
     * passar. {@code lead_id} e {@code NOT NULL}, entao nenhum lead e descartado pelo
     * {@code DISTINCT}. Uma condicao nova que dependa da linha (e nao do lead) quebra essa
     * equivalencia: o teste de integracao que compara contagem e listagem reprova.
     *
     * <p>O {@code JOIN lead} aplica a RLS de {@code lead}, como a listagem faz. A contagem da E199
     * nao tinha esse join e, para atendente, contava em FINALIZADOS leads que um colega esta
     * atendendo (ciclo antigo FINALIZADO visivel, lead invisivel) — numero maior que a lista.
     */
    static String contar(String filtro) {
        return "SELECT COUNT(DISTINCT a.lead_id) FROM atendimento a JOIN lead l ON l.id = a.lead_id" + filtro;
    }

    private static final RowMapper<CartaoAtendimento> MAPEADOR =
            PainelDeAtendimentosRepositorioJdbc::paraCartao;

    private final JdbcTemplate chat;

    private final int listagemMaxima;

    PainelDeAtendimentosRepositorioJdbc(
            @Qualifier(Pools.CHAT_DATA_SOURCE) DataSource chatDataSource,
            @Value("${synapse.painel.listagem-maxima}") int listagemMaxima) {
        if (listagemMaxima < 1) {
            throw new IllegalArgumentException("synapse.painel.listagem-maxima precisa ser >= 1: " + listagemMaxima);
        }
        this.chat = new JdbcTemplate(chatDataSource);
        this.listagemMaxima = listagemMaxima;
    }

    @Override
    public ListaDoPainel listar(
            VisaoAtendimento visao, UUID usuarioId, boolean restritoAoProprioAtendente) {
        TransacaoObrigatoria.exigir("listar");
        // Busca UM a mais que o teto: se ele vier, havia mais do que cabe (sem COUNT). O extra e descartado.
        List<CartaoAtendimento> lidos = lerComTeto(visao, usuarioId, restritoAoProprioAtendente, listagemMaxima + 1);
        boolean truncada = lidos.size() > listagemMaxima;
        return new ListaDoPainel(truncada ? lidos.subList(0, listagemMaxima) : lidos, truncada, listagemMaxima);
    }

    private List<CartaoAtendimento> lerComTeto(
            VisaoAtendimento visao, UUID usuarioId, boolean restritoAoProprioAtendente, int limite) {
        return switch (visao) {
            case ATIVOS -> chat.query(SQL_ATIVOS, MAPEADOR, usuarioId, usuarioId, limite);
            case PENDENTES -> restritoAoProprioAtendente
                    ? chat.query(SQL_PENDENTES_PROPRIOS, MAPEADOR, usuarioId, usuarioId, usuarioId, limite)
                    : chat.query(SQL_PENDENTES_TODOS, MAPEADOR, usuarioId, limite);
            case POTENCIAIS -> chat.query(SQL_POTENCIAIS, MAPEADOR, usuarioId, limite);
            case TODOS -> chat.query(SQL_TODOS, MAPEADOR, usuarioId, limite);
            // E145: qualquer atendente pode encontrar e reativar um finalizado. O argumento de
            // restricao continua sendo aplicado nas visoes de andamento; aqui a RLS faz o recorte.
            case FINALIZADOS -> chat.query(SQL_FINALIZADOS, MAPEADOR, usuarioId, limite);
        };
    }

    @Override
    public Optional<CartaoAtendimento> porAtendimentoId(UUID atendimentoId, UUID usuarioId) {
        TransacaoObrigatoria.exigir("porAtendimentoId");
        return primeiro(chat.query(SQL_POR_ATENDIMENTO, MAPEADOR, usuarioId, atendimentoId));
    }

    @Override
    public Optional<CartaoAtendimento> porLeadId(UUID leadId, UUID usuarioId) {
        TransacaoObrigatoria.exigir("porLeadId");
        return primeiro(chat.query(SQL_POR_LEAD, MAPEADOR, usuarioId, leadId));
    }

    @Override
    public List<CartaoAtendimento> listarPaginado(VisaoAtendimento visao, UUID usuarioId,
            boolean restritoAoProprioAtendente, boolean depoisSemAtendimentoAberto,
            Instant depoisDe, UUID depoisDoId, int limite) {
        return listarPaginado(visao, usuarioId, restritoAoProprioAtendente, depoisSemAtendimentoAberto,
                depoisDe, depoisDoId, limite, null);
    }

    @Override
    public List<CartaoAtendimento> listarPaginado(VisaoAtendimento visao, UUID usuarioId,
            boolean restritoAoProprioAtendente, boolean depoisSemAtendimentoAberto,
            Instant depoisDe, UUID depoisDoId, int limite, UUID filtroAtendenteId) {
        TransacaoObrigatoria.exigir("listarPaginado");
        Escolha escolha = escolherPagina(visao, usuarioId, restritoAoProprioAtendente, depoisSemAtendimentoAberto,
                depoisDe, depoisDoId, limite, filtroAtendenteId);
        return chat.query(cartoesDe(escolha.sql()), MAPEADOR, escolha.parametros().toArray());
    }

    /** Primeira fase de uma pagina: o texto que escolhe as ids e os parametros, na ordem dos {@code ?} do texto final. */
    record Escolha(String sql, List<Object> parametros) {}

    /**
     * Monta a primeira fase de uma pagina (visao, cursor, ordem e LIMIT). E estatico e visivel ao pacote para que o
     * teste de equivalencia da E224 (B1) use exatamente a mesma escolha na consulta antiga e na nova.
     */
    static Escolha escolherPagina(VisaoAtendimento visao, UUID usuarioId,
            boolean restritoAoProprioAtendente, boolean depoisSemAtendimentoAberto,
            Instant depoisDe, UUID depoisDoId, int limite, UUID filtroAtendenteId) {
        String filtro = switch (visao) {
            case ATIVOS -> WHERE_ATIVOS;
            case PENDENTES -> restritoAoProprioAtendente ? WHERE_PENDENTES_PROPRIOS : WHERE_PENDENTES_TODOS;
            case POTENCIAIS -> WHERE_POTENCIAIS;
            case TODOS -> WHERE_TODOS_ATIVOS;
            case FINALIZADOS -> WHERE_FINALIZADOS;
        };
        if (filtroAtendenteId != null && visao == VisaoAtendimento.FINALIZADOS) {
            filtro += " AND a.atendente_id = ?";
        }
        // E209: cursor, ordem e LIMIT ficam na primeira fase; a segunda so monta os cartoes da
        // pagina. A ordem dos parametros continua a do texto: o `?` de nao_lidas (segunda fase)
        // vem antes do filtro da primeira.
        String sql = escolher(filtro);
        List<Object> parametros = new java.util.ArrayList<>();
        parametros.add(usuarioId);
        if (visao == VisaoAtendimento.ATIVOS) {
            parametros.add(usuarioId);
        } else if (visao == VisaoAtendimento.PENDENTES && restritoAoProprioAtendente) {
            parametros.add(usuarioId);
            parametros.add(usuarioId);
        }
        if (filtroAtendenteId != null && visao == VisaoAtendimento.FINALIZADOS) {
            parametros.add(filtroAtendenteId);
        }
        if (depoisDoId != null) {
            int grupoDoCursor = depoisSemAtendimentoAberto ? 1 : 0;
            sql += " AND (sem_atendimento_aberto > ? OR (sem_atendimento_aberto = ? AND (";
            parametros.add(grupoDoCursor);
            parametros.add(grupoDoCursor);
            if (depoisDe == null) {
                sql += "ultima_mensagem_em IS NULL AND atendimento_id < ?";
                parametros.add(depoisDoId);
            } else {
                sql += "ultima_mensagem_em < ? OR (ultima_mensagem_em = ? AND atendimento_id < ?)"
                        + " OR ultima_mensagem_em IS NULL";
                parametros.add(Timestamp.from(depoisDe));
                parametros.add(Timestamp.from(depoisDe));
                parametros.add(depoisDoId);
            }
            sql += ")))";
        }
        sql += ORDEM_ESCOLHA + " LIMIT ?";
        parametros.add(Math.min(101, Math.max(1, limite)));
        return new Escolha(sql, parametros);
    }

    @Override
    public long contar(VisaoAtendimento visao, UUID usuarioId, boolean restritoAoProprioAtendente) {
        TransacaoObrigatoria.exigir("contar");
        return switch (visao) {
            case ATIVOS -> queryForCount(SQL_CONTAR_ATIVOS, usuarioId);
            case PENDENTES -> restritoAoProprioAtendente
                    ? queryForCount(SQL_CONTAR_PENDENTES_PROPRIOS, usuarioId, usuarioId)
                    : queryForCount(SQL_CONTAR_PENDENTES_TODOS);
            case POTENCIAIS -> queryForCount(SQL_CONTAR_POTENCIAIS);
            case TODOS -> queryForCount(SQL_CONTAR_TODOS);
            case FINALIZADOS -> queryForCount(SQL_CONTAR_FINALIZADOS);
        };
    }

    private long queryForCount(String sql, Object... parametros) {
        Long total = chat.queryForObject(sql, Long.class, parametros);
        return total == null ? 0 : total;
    }

    private static <T> Optional<T> primeiro(List<T> itens) {
        return itens.isEmpty() ? Optional.empty() : Optional.of(itens.getFirst());
    }

    private static CartaoAtendimento paraCartao(ResultSet linha, int indice) throws SQLException {
        return new CartaoAtendimento(
                linha.getObject("atendimento_id", UUID.class),
                linha.getObject("lead_id", UUID.class),
                linha.getString("lead_nome"),
                linha.getString("lead_foto_url"),
                linha.getString("lead_empresa"),
                linha.getString("lead_codigo"),
                linha.getString("canal_tipo"),
                linha.getObject("etapa_atendimento_id", UUID.class),
                linha.getString("etapa_nome"),
                linha.getString("etapa_cor"),
                StatusAtendimento.valueOf(linha.getString("status")),
                linha.getObject("atendente_id", UUID.class),
                linha.getString("atendente_nome"),
                linha.getObject("atendimento_ativo_id", UUID.class),
                linha.getString("ultima_mensagem_preview"),
                linha.getString("ultima_mensagem_remetente_tipo"),
                instante(linha, "ultima_mensagem_em"),
                instante(linha, "ultima_mensagem_do_lead_em"),
                linha.getLong("nao_lidas"),
                linha.getBoolean("em_negociacao"),
                linha.getString("resultado_venda") == null
                        ? null
                        : com.synapse.crm.atendimento.domain.atendimento.ResultadoVenda.valueOf(
                                linha.getString("resultado_venda")),
                linha.getBigDecimal("valor_venda"),
                linha.getObject("venda_registrada_por_id", UUID.class),
                linha.getString("venda_registrada_por_nome"),
                instante(linha, "venda_registrada_em"),
                linha.getString("origem_resultado_venda") == null
                        ? null
                        : com.synapse.crm.atendimento.domain.atendimento.OrigemResultadoVenda.valueOf(
                                linha.getString("origem_resultado_venda")));
    }

    private static Instant instante(ResultSet linha, String coluna) throws SQLException {
        Timestamp valor = linha.getTimestamp(coluna);
        return valor == null ? null : valor.toInstant();
    }
}
