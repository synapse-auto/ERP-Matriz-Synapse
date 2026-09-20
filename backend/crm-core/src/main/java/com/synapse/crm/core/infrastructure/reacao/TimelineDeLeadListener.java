package com.synapse.crm.core.infrastructure.reacao;

import java.sql.Timestamp;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import javax.sql.DataSource;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.synapse.crm.core.domain.etapa.EtapaAtendimento;
import com.synapse.crm.core.domain.evento.EtapaDoLeadAlterada;
import com.synapse.crm.core.domain.evento.FichaDoLeadResetada;
import com.synapse.crm.sharedkernel.persistencia.Pools;

/** Persiste a mudanca de etapa somente depois que a alteracao do lead foi commitada. */
@Component
class TimelineDeLeadListener {

    private static final String SQL =
            """
            INSERT INTO evento_timeline
                (lead_id, tipo, descricao, origem, ator_id, dados, criado_em)
            VALUES (?, 'ETAPA_ALTERADA', ?, 'USUARIO', ?, ?::jsonb, ?)
            """;

    private static final String SQL_RESET_GERAL =
            """
            INSERT INTO evento_timeline
                (lead_id, tipo, descricao, origem, ator_id, dados, criado_em)
            VALUES (?, 'FICHA_RESETADA', ?, 'SISTEMA', NULL, ?::jsonb, ?)
            """;

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    TimelineDeLeadListener(
            @Qualifier(Pools.GENERAL_DATA_SOURCE) DataSource dataSource, ObjectMapper json) {
        this.jdbc = new JdbcTemplate(dataSource);
        this.json = json;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void aoAlterarEtapa(EtapaDoLeadAlterada evento) {
        String ator = jdbc.query(
                        "SELECT nome FROM usuario WHERE id = ?",
                        (linha, indice) -> linha.getString("nome"),
                        evento.atorId())
                .stream()
                .findFirst()
                .orElse(evento.atorId().toString());
        jdbc.update(
                SQL,
                evento.leadId(),
                descricao(ator, evento.etapaAnterior(), evento.etapaNova()),
                evento.atorId(),
                serializar(dados(evento)),
                Timestamp.from(evento.ocorridoEm()));
    }

    /**
     * E195: o {@code #resetgeral} nao tem usuario — quem zerou a ficha foi o sistema reagindo a uma
     * mensagem. Por isso {@code origem = 'SISTEMA'} e {@code ator_id} nulo, a mesma convencao de
     * {@code devolverParaIaPeloSistema}, em vez de inventar um usuario tecnico so para preencher a
     * coluna. O nome da etapa anterior e resolvido aqui, depois do commit, no pool geral.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void aoResetarFicha(FichaDoLeadResetada evento) {
        String etapaAnterior = evento.etapaAnteriorId()
                .flatMap(id -> jdbc
                        .query(
                                "SELECT nome FROM etapa_atendimento WHERE id = ?",
                                (linha, indice) -> linha.getString("nome"),
                                id)
                        .stream()
                        .findFirst())
                .orElse(null);
        Map<String, Object> dados = new LinkedHashMap<>();
        dados.put("etapa_anterior_id", evento.etapaAnteriorId().map(UUID::toString).orElse(null));
        dados.put("etapa_anterior_nome", etapaAnterior);
        dados.put("tinha_resumo_ia", evento.tinhaResumo());
        jdbc.update(
                SQL_RESET_GERAL,
                evento.leadId(),
                descricaoDoReset(etapaAnterior, evento.tinhaResumo()),
                serializar(dados),
                Timestamp.from(evento.ocorridoEm()));
    }

    private static String descricaoDoReset(String etapaAnterior, boolean tinhaResumo) {
        StringBuilder descricao = new StringBuilder("O sistema zerou a ficha do lead pelo comando de teste");
        if (etapaAnterior != null) {
            descricao.append("; etapa anterior: ").append(etapaAnterior);
        }
        if (tinhaResumo) {
            descricao.append("; resumo de IA removido");
        }
        return descricao.append('.').toString();
    }

    private static Map<String, Object> dados(EtapaDoLeadAlterada evento) {
        Map<String, Object> dados = new LinkedHashMap<>();
        dados.put("etapa_anterior_id", id(evento.etapaAnterior()));
        dados.put("etapa_anterior_nome", nome(evento.etapaAnterior()));
        dados.put("resultado_anterior", resultado(evento.etapaAnterior()));
        dados.put("etapa_nova_id", evento.etapaNova().id().toString());
        dados.put("etapa_nova_nome", evento.etapaNova().nome());
        dados.put("resultado_novo", evento.etapaNova().resultado().name());
        dados.put(
                "responsavel_id",
                evento.responsavelId() == null ? null : evento.responsavelId().toString());
        return dados;
    }

    private static String descricao(
            String ator, EtapaAtendimento anterior, EtapaAtendimento novaEtapa) {
        String origem = anterior == null ? "Sem etapa" : anterior.nome();
        return ator + " moveu o lead de " + origem + " para " + novaEtapa.nome() + ".";
    }

    private String serializar(Map<String, Object> dados) {
        try {
            return json.writeValueAsString(dados);
        } catch (JsonProcessingException erro) {
            throw new IllegalStateException("Falha ao serializar mudanca de etapa", erro);
        }
    }

    private static String id(EtapaAtendimento etapa) {
        return etapa == null ? null : etapa.id().toString();
    }

    private static String nome(EtapaAtendimento etapa) {
        return etapa == null ? null : etapa.nome();
    }

    private static String resultado(EtapaAtendimento etapa) {
        return etapa == null ? null : etapa.resultado().name();
    }
}
