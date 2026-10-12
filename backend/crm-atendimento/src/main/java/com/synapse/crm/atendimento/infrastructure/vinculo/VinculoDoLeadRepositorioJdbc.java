package com.synapse.crm.atendimento.infrastructure.vinculo;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.atendimento.application.VinculoDoLeadRepositorio;
import com.synapse.crm.sharedkernel.persistencia.Pools;

/**
 * Leitura no pool do chat (mesma transação do rodízio, em contexto de serviço: o join com {@code atendimento} passa
 * pela RLS como serviço); escrita no pool geral, depois do commit, como os demais listeners de reação.
 *
 * <p><b>Chave desligada não vai ao banco.</b> O valor de {@code ia.distribuicao.atendente_vinculado} é guardado em
 * memória por no máximo o intervalo de revalidação das permissões ({@code synapse.permissoes.revalidacao}, 2s por
 * padrão — a mesma propriedade, nenhuma nova): desligada, nem a busca do vínculo nem a gravação executam SQL. O
 * custo de saber o valor é uma leitura por chave primária por intervalo e por nó, não uma por chamada; ligar ou
 * desligar a chave vale em até esse intervalo.
 */
@Repository
class VinculoDoLeadRepositorioJdbc implements VinculoDoLeadRepositorio {

    static final String CHAVE = "ia.distribuicao.atendente_vinculado";

    /** Só executa com a chave ligada (guarda em {@link #habilitado()}); desligada não chega aqui. */
    private static final String SQL_VINCULADO_DO_ATENDIMENTO =
            """
            SELECT v.atendente_id
              FROM atendimento a
              JOIN lead_atendente_vinculado v ON v.lead_id = a.lead_id
             WHERE a.id = ?
            """;

    private static final String SQL_HABILITADO =
            "SELECT lower(btrim(valor)) = 'true' FROM configuracao_automacao WHERE chave = ?";

    private static final String SQL_REGISTRAR =
            """
            INSERT INTO lead_atendente_vinculado (lead_id, atendente_id, origem)
                 VALUES (?, ?, ?)
            ON CONFLICT (lead_id) DO UPDATE
                    SET atendente_id = EXCLUDED.atendente_id,
                        origem       = EXCLUDED.origem,
                        vinculado_em = now()
            """;

    private static final Logger log = LoggerFactory.getLogger(VinculoDoLeadRepositorioJdbc.class);

    private final JdbcTemplate chat;
    private final JdbcTemplate geral;
    private final Clock relogio;
    private final Duration validadeDaLeitura;

    private volatile Leitura leitura;

    VinculoDoLeadRepositorioJdbc(
            @Qualifier(Pools.CHAT_DATA_SOURCE) DataSource chatDataSource,
            @Qualifier(Pools.GENERAL_DATA_SOURCE) DataSource generalDataSource,
            Clock relogio,
            @Value("${synapse.permissoes.revalidacao:2s}") Duration validadeDaLeitura) {
        this.chat = new JdbcTemplate(chatDataSource);
        this.geral = new JdbcTemplate(generalDataSource);
        this.relogio = relogio;
        this.validadeDaLeitura = validadeDaLeitura;
    }

    @Override
    public Optional<UUID> vinculadoDoAtendimento(UUID atendimentoId) {
        if (!habilitado()) {
            return Optional.empty();
        }
        return chat.query(
                        SQL_VINCULADO_DO_ATENDIMENTO,
                        (linha, indice) -> linha.getObject("atendente_id", UUID.class),
                        atendimentoId)
                .stream()
                .findFirst();
    }

    @Override
    public boolean habilitado() {
        Instant agora = relogio.instant();
        Leitura atual = leitura;
        if (atual != null && agora.isBefore(atual.lidaEm().plus(validadeDaLeitura))) {
            return atual.ligada();
        }
        boolean ligada;
        try {
            ligada = Boolean.TRUE.equals(geral.query(
                    SQL_HABILITADO, resultados -> resultados.next() ? resultados.getBoolean(1) : Boolean.FALSE, CHAVE));
        } catch (RuntimeException erro) {
            // Falhar fechado e por um intervalo: sem saber o valor, o vinculo nao e honrado nem gravado, o rodizio
            // segue normal, e um banco instavel nao ganha uma tentativa de leitura a cada chamada.
            log.warn("Nao foi possivel ler a chave {}: {}. Vinculo ignorado ate a proxima leitura.", CHAVE, erro.toString());
            ligada = false;
        }
        leitura = new Leitura(ligada, agora);
        return ligada;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrar(UUID leadId, UUID atendenteId, String origem) {
        geral.update(SQL_REGISTRAR, leadId, atendenteId, origem);
    }

    private record Leitura(boolean ligada, Instant lidaEm) {}
}
