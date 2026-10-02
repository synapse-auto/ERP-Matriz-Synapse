package com.synapse.crm.campanhas.infrastructure;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import javax.sql.DataSource;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.synapse.crm.campanhas.application.CampanhaRepositorio;
import com.synapse.crm.campanhas.domain.Campanha;
import com.synapse.crm.campanhas.domain.CampoDoLead;
import com.synapse.crm.campanhas.domain.DiasDaSemana;
import com.synapse.crm.campanhas.domain.FiltroDePublico;
import com.synapse.crm.campanhas.domain.JanelaDeEnvio;
import com.synapse.crm.campanhas.domain.MapeamentoDeVariaveis;
import com.synapse.crm.campanhas.domain.PlanoDeLimite;
import com.synapse.crm.campanhas.domain.StatusDaCampanha;
import com.synapse.crm.core.infrastructure.persistencia.TransacaoObrigatoria;
import com.synapse.crm.sharedkernel.persistencia.Pools;

/**
 * {@code campanha_template} no pool do chat: o envio de campanha e o caminho que disputa conexao com mensagens,
 * entao fica no mesmo bulkhead e nunca no pool geral. O JSON do mapeamento e do filtro e montado e lido na mao
 * (arvore Jackson), para o formato guardado nao depender de como o Jackson enxerga os records do dominio.
 */
@Repository
class CampanhaRepositorioJdbc implements CampanhaRepositorio {

    private static final String COLUNAS =
            """
            id, nome, template_id, template_nome, template_idioma, template_categoria, template_corpo,
            template_parametros, mapeamento_variaveis, filtro_publico, status, desligada, limite_diario,
            janela_inicio, janela_fim, dias_da_semana, ritmo_por_minuto, rampa_incremento, rampa_teto,
            agendada_para, qtd_total, qtd_pendentes, qtd_enfileirados, qtd_enviados, qtd_entregues, qtd_lidos,
            qtd_respondidos, qtd_falhas, qtd_ignorados, qtd_conferencia, motivo_pausa, pausada_em, iniciada_em,
            concluida_em, criada_por, criada_em, ultimo_ciclo_em
            """;

    private static final String SQL_INSERIR =
            """
            INSERT INTO campanha_template (
                id, nome, template_id, template_nome, template_idioma, template_categoria, template_corpo,
                template_parametros, mapeamento_variaveis, filtro_publico, status, desligada, limite_diario,
                janela_inicio, janela_fim, dias_da_semana, ritmo_por_minuto, rampa_incremento, rampa_teto,
                agendada_para, criada_por, criada_em)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private static final String SQL_ATUALIZAR =
            """
            UPDATE campanha_template SET
                nome = ?, template_id = ?, template_nome = ?, template_idioma = ?, template_categoria = ?,
                template_corpo = ?, template_parametros = ?, mapeamento_variaveis = ?::jsonb,
                filtro_publico = ?::jsonb, status = ?, desligada = ?, limite_diario = ?, janela_inicio = ?,
                janela_fim = ?, dias_da_semana = ?, ritmo_por_minuto = ?, rampa_incremento = ?, rampa_teto = ?,
                agendada_para = ?, motivo_pausa = ?, pausada_em = ?, iniciada_em = ?, concluida_em = ?,
                atualizada_em = now()
            WHERE id = ?
            """;

    private static final String SQL_VARIAR =
            """
            UPDATE campanha_template SET
                qtd_pendentes = qtd_pendentes + ?, qtd_enfileirados = qtd_enfileirados + ?,
                qtd_enviados = qtd_enviados + ?, qtd_entregues = qtd_entregues + ?, qtd_lidos = qtd_lidos + ?,
                qtd_respondidos = qtd_respondidos + ?, qtd_falhas = qtd_falhas + ?,
                qtd_ignorados = qtd_ignorados + ?, qtd_conferencia = qtd_conferencia + ?
            WHERE id = ?
            """;

    /** Insere ou soma; so incrementa enquanto a contagem do dia esta abaixo do limite (sem linha = estourou). */
    private static final String SQL_VAGA_DA_CAMPANHA =
            """
            INSERT INTO campanha_template_dia (campanha_id, dia, enfileiradas) VALUES (?, ?, 1)
            ON CONFLICT (campanha_id, dia)
            DO UPDATE SET enfileiradas = campanha_template_dia.enfileiradas + 1
             WHERE campanha_template_dia.enfileiradas < ?
            RETURNING enfileiradas
            """;

    private static final String SQL_VAGA_DA_INSTANCIA =
            """
            INSERT INTO campanha_envio_dia (dia, enfileiradas) VALUES (?, 1)
            ON CONFLICT (dia)
            DO UPDATE SET enfileiradas = campanha_envio_dia.enfileiradas + 1
             WHERE campanha_envio_dia.enfileiradas < ?
            RETURNING enfileiradas
            """;

    private final JdbcTemplate chat;
    private final ObjectMapper json;

    CampanhaRepositorioJdbc(@Qualifier(Pools.CHAT_DATA_SOURCE) DataSource chatDataSource, ObjectMapper json) {
        this.chat = new JdbcTemplate(chatDataSource);
        this.json = json;
    }

    @Override
    public void inserir(Campanha c) {
        TransacaoObrigatoria.exigir("inserir campanha");
        chat.update(
                SQL_INSERIR,
                c.id(),
                c.nome(),
                c.template().id(),
                c.template().nome(),
                c.template().idioma(),
                c.template().categoria(),
                c.template().corpo(),
                c.template().parametros(),
                mapeamentoComoJson(c.mapeamento()),
                filtroComoJson(c.filtro()),
                c.status().name(),
                c.desligada(),
                c.limiteDiario(),
                c.janela().inicio(),
                c.janela().fim(),
                c.janela().dias().mascara(),
                c.ritmoPorMinuto(),
                c.rampa() == null ? null : c.rampa().incrementoPorDia(),
                c.rampa() == null ? null : c.rampa().teto(),
                instante(c.agendadaPara()),
                c.criadaPor(),
                Timestamp.from(c.criadaEm()));
    }

    @Override
    public void atualizar(Campanha c) {
        TransacaoObrigatoria.exigir("atualizar campanha");
        chat.update(
                SQL_ATUALIZAR,
                c.nome(),
                c.template().id(),
                c.template().nome(),
                c.template().idioma(),
                c.template().categoria(),
                c.template().corpo(),
                c.template().parametros(),
                mapeamentoComoJson(c.mapeamento()),
                filtroComoJson(c.filtro()),
                c.status().name(),
                c.desligada(),
                c.limiteDiario(),
                c.janela().inicio(),
                c.janela().fim(),
                c.janela().dias().mascara(),
                c.ritmoPorMinuto(),
                c.rampa() == null ? null : c.rampa().incrementoPorDia(),
                c.rampa() == null ? null : c.rampa().teto(),
                instante(c.agendadaPara()),
                c.motivoDePausa(),
                instante(c.pausadaEm()),
                instante(c.iniciadaEm()),
                instante(c.concluidaEm()),
                c.id());
    }

    @Override
    public Optional<Campanha> porId(UUID id) {
        TransacaoObrigatoria.exigir("ler campanha");
        return primeira(chat.query("SELECT " + COLUNAS + " FROM campanha_template WHERE id = ?", this::mapear, id));
    }

    @Override
    public Optional<Campanha> bloquearPorId(UUID id) {
        TransacaoObrigatoria.exigir("travar campanha");
        return primeira(chat.query(
                "SELECT " + COLUNAS + " FROM campanha_template WHERE id = ? FOR UPDATE", this::mapear, id));
    }

    @Override
    public Optional<Campanha> bloquearParaEnvio(UUID id) {
        TransacaoObrigatoria.exigir("travar campanha para envio");
        return primeira(chat.query(
                "SELECT " + COLUNAS + " FROM campanha_template WHERE id = ? FOR SHARE", this::mapear, id));
    }

    @Override
    public List<Campanha> listar(int pagina, int tamanho) {
        TransacaoObrigatoria.exigir("listar campanhas");
        return chat.query(
                "SELECT " + COLUNAS + " FROM campanha_template ORDER BY criada_em DESC LIMIT ? OFFSET ?",
                this::mapear,
                tamanho,
                pagina * tamanho);
    }

    @Override
    public long contar() {
        TransacaoObrigatoria.exigir("contar campanhas");
        Long total = chat.queryForObject("SELECT count(*) FROM campanha_template", Long.class);
        return total == null ? 0L : total;
    }

    @Override
    public List<UUID> idsParaProcessar(Instant agora) {
        TransacaoObrigatoria.exigir("listar campanhas ativas");
        return chat.queryForList(
                """
                SELECT id FROM campanha_template
                 WHERE NOT desligada
                   AND (status = 'EM_ANDAMENTO' OR (status = 'AGENDADA' AND agendada_para <= ?))
                 ORDER BY criada_em
                """,
                UUID.class,
                Timestamp.from(agora));
    }

    @Override
    public Optional<Lease> adquirirLease(UUID id, Instant agora, Instant leaseAte) {
        TransacaoObrigatoria.exigir("pegar lease da campanha");
        List<Lease> pegos = chat.query(
                "UPDATE campanha_template SET lease_ate = ?"
                        + " WHERE id = ? AND NOT desligada"
                        + "   AND (lease_ate IS NULL OR lease_ate <= ?)"
                        + "   AND (status = 'EM_ANDAMENTO' OR (status = 'AGENDADA' AND agendada_para <= ?))"
                        + " RETURNING " + COLUNAS,
                (rs, linha) -> new Lease(mapear(rs, linha), instante(rs, "ultimo_ciclo_em")),
                Timestamp.from(leaseAte),
                id,
                Timestamp.from(agora),
                Timestamp.from(agora));
        return primeira(pegos);
    }

    @Override
    public void liberarLease(UUID id, Instant ultimoCiclo) {
        TransacaoObrigatoria.exigir("liberar lease da campanha");
        chat.update(
                "UPDATE campanha_template SET lease_ate = NULL, ultimo_ciclo_em = ? WHERE id = ?",
                Timestamp.from(ultimoCiclo),
                id);
    }

    @Override
    public void variarContadores(UUID id, Variacao v) {
        TransacaoObrigatoria.exigir("variar contadores da campanha");
        if (v.vazia()) {
            return;
        }
        chat.update(
                SQL_VARIAR,
                v.pendentes(),
                v.enfileirados(),
                v.enviados(),
                v.entregues(),
                v.lidos(),
                v.respondidos(),
                v.falhas(),
                v.ignorados(),
                v.conferencia(),
                id);
    }

    @Override
    public boolean reservarVagaDoDia(UUID campanhaId, LocalDate dia, int limiteDaCampanha, int tetoDaInstancia) {
        TransacaoObrigatoria.exigir("reservar vaga do dia");
        boolean daCampanha = !chat.queryForList(
                        SQL_VAGA_DA_CAMPANHA, Integer.class, campanhaId, java.sql.Date.valueOf(dia), limiteDaCampanha)
                .isEmpty();
        if (!daCampanha) {
            return false;
        }
        return !chat.queryForList(SQL_VAGA_DA_INSTANCIA, Integer.class, java.sql.Date.valueOf(dia), tetoDaInstancia)
                .isEmpty();
    }

    @Override
    public List<DiaEnfileirado> enfileiradasPorDia(UUID campanhaId) {
        TransacaoObrigatoria.exigir("ler serie por dia");
        return chat.query(
                "SELECT dia, enfileiradas FROM campanha_template_dia WHERE campanha_id = ? ORDER BY dia",
                (rs, linha) -> new DiaEnfileirado(rs.getDate("dia").toLocalDate(), rs.getInt("enfileiradas")),
                campanhaId);
    }

    @Override
    public int enfileiradasNoDia(LocalDate dia) {
        TransacaoObrigatoria.exigir("ler enfileiradas do dia");
        List<Integer> valores = chat.queryForList(
                "SELECT enfileiradas FROM campanha_envio_dia WHERE dia = ?", Integer.class, java.sql.Date.valueOf(dia));
        return valores.isEmpty() ? 0 : valores.get(0);
    }

    @Override
    public Indicadores indicadores() {
        TransacaoObrigatoria.exigir("ler indicadores de campanhas");
        return chat.queryForObject(
                """
                SELECT coalesce(sum(qtd_enviados), 0), coalesce(sum(qtd_entregues), 0), coalesce(sum(qtd_lidos), 0),
                       coalesce(sum(qtd_respondidos), 0), coalesce(sum(qtd_falhas), 0)
                  FROM campanha_template
                """,
                (rs, linha) -> new Indicadores(rs.getLong(1), rs.getLong(2), rs.getLong(3), rs.getLong(4), rs.getLong(5)));
    }

    // --- mapeamento -----------------------------------------------------------------------------------

    private Campanha mapear(ResultSet rs, int linha) throws SQLException {
        Integer incremento = (Integer) rs.getObject("rampa_incremento");
        Integer tetoDaRampa = (Integer) rs.getObject("rampa_teto");
        return new Campanha(
                rs.getObject("id", UUID.class),
                rs.getString("nome"),
                new Campanha.TemplateSnapshot(
                        rs.getString("template_id"),
                        rs.getString("template_nome"),
                        rs.getString("template_idioma"),
                        rs.getString("template_categoria"),
                        rs.getString("template_corpo"),
                        rs.getInt("template_parametros")),
                mapeamentoDoJson(rs.getString("mapeamento_variaveis")),
                filtroDoJson(rs.getString("filtro_publico")),
                StatusDaCampanha.valueOf(rs.getString("status")),
                rs.getBoolean("desligada"),
                rs.getInt("limite_diario"),
                new JanelaDeEnvio(
                        rs.getObject("janela_inicio", LocalTime.class),
                        rs.getObject("janela_fim", LocalTime.class),
                        new DiasDaSemana(rs.getInt("dias_da_semana"))),
                rs.getInt("ritmo_por_minuto"),
                incremento == null ? null : new PlanoDeLimite.Rampa(incremento, tetoDaRampa),
                instante(rs, "agendada_para"),
                new Campanha.Contadores(
                        rs.getInt("qtd_total"),
                        rs.getInt("qtd_pendentes"),
                        rs.getInt("qtd_enfileirados"),
                        rs.getInt("qtd_enviados"),
                        rs.getInt("qtd_entregues"),
                        rs.getInt("qtd_lidos"),
                        rs.getInt("qtd_respondidos"),
                        rs.getInt("qtd_falhas"),
                        rs.getInt("qtd_ignorados"),
                        rs.getInt("qtd_conferencia")),
                rs.getString("motivo_pausa"),
                instante(rs, "pausada_em"),
                instante(rs, "iniciada_em"),
                instante(rs, "concluida_em"),
                rs.getObject("criada_por", UUID.class),
                instante(rs, "criada_em"));
    }

    private String mapeamentoComoJson(MapeamentoDeVariaveis mapeamento) {
        ArrayNode lista = json.createArrayNode();
        for (MapeamentoDeVariaveis.Variavel variavel : mapeamento.variaveis()) {
            lista.addObject()
                    .put("posicao", variavel.posicao())
                    .put("campo", variavel.campo().name())
                    .put("reserva", variavel.reserva());
        }
        return lista.toString();
    }

    private MapeamentoDeVariaveis mapeamentoDoJson(String texto) {
        List<MapeamentoDeVariaveis.Variavel> variaveis = new ArrayList<>();
        for (JsonNode no : ler(texto)) {
            variaveis.add(new MapeamentoDeVariaveis.Variavel(
                    no.path("posicao").asInt(),
                    CampoDoLead.valueOf(no.path("campo").asText()),
                    no.path("reserva").asText()));
        }
        return new MapeamentoDeVariaveis(variaveis);
    }

    private String filtroComoJson(FiltroDePublico filtro) {
        ObjectNode no = json.createObjectNode();
        ArrayNode tags = no.putArray("tagIds");
        filtro.tagIds().forEach(tag -> tags.add(tag.toString()));
        no.put("etapaId", filtro.etapaId() == null ? null : filtro.etapaId().toString());
        no.put("atendenteId", filtro.atendenteId() == null ? null : filtro.atendenteId().toString());
        no.put("cadastroDesde", filtro.cadastroDesde() == null ? null : filtro.cadastroDesde().toString());
        no.put("cadastroAte", filtro.cadastroAte() == null ? null : filtro.cadastroAte().toString());
        no.put("nuncaConversou", filtro.nuncaConversou());
        no.put("busca", filtro.busca());
        return no.toString();
    }

    private FiltroDePublico filtroDoJson(String texto) {
        JsonNode no = ler(texto);
        List<UUID> tags = new ArrayList<>();
        no.path("tagIds").forEach(tag -> tags.add(UUID.fromString(tag.asText())));
        return new FiltroDePublico(
                tags,
                uuidOuNulo(no, "etapaId"),
                uuidOuNulo(no, "atendenteId"),
                dataOuNula(no, "cadastroDesde"),
                dataOuNula(no, "cadastroAte"),
                no.path("nuncaConversou").asBoolean(false),
                no.path("busca").isNull() || no.path("busca").isMissingNode() ? null : no.path("busca").asText());
    }

    private JsonNode ler(String texto) {
        try {
            return json.readTree(texto);
        } catch (JsonProcessingException erro) {
            throw new IllegalStateException("JSON de campanha invalido no banco", erro);
        }
    }

    private static UUID uuidOuNulo(JsonNode no, String campo) {
        JsonNode valor = no.path(campo);
        return valor.isMissingNode() || valor.isNull() ? null : UUID.fromString(valor.asText());
    }

    private static LocalDate dataOuNula(JsonNode no, String campo) {
        JsonNode valor = no.path(campo);
        return valor.isMissingNode() || valor.isNull() ? null : LocalDate.parse(valor.asText());
    }

    private static Timestamp instante(Instant valor) {
        return valor == null ? null : Timestamp.from(valor);
    }

    private static Instant instante(ResultSet rs, String coluna) throws SQLException {
        Timestamp valor = rs.getTimestamp(coluna);
        return valor == null ? null : valor.toInstant();
    }

    private static <T> Optional<T> primeira(List<T> lista) {
        return lista.isEmpty() ? Optional.empty() : Optional.of(lista.get(0));
    }
}
