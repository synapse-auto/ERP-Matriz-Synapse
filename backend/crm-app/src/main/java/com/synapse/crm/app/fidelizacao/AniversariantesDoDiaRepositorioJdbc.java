package com.synapse.crm.app.fidelizacao;

import java.time.MonthDay;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.synapse.crm.automacaoconfig.application.fidelizacao.AniversariantesDoDiaRepositorio;
import com.synapse.crm.automacaoconfig.domain.fidelizacao.LeadAniversariante;

/**
 * Adaptador dos aniversariantes: mora em crm-app porque cruza dois modulos — a porta e de
 * crm-automacao-config, a tabela {@code lead} e de crm-core. Mesmo arranjo de
 * {@code HorarioComercialConfiguravel}, que tambem implementa aqui na composicao uma porta
 * declarada por outro modulo.
 *
 * <p>Pool geral: leitura de automacao nao e caminho de mensagem, entao nao disputa conexao com o
 * chat. A RLS de {@code lead} exige contexto de servico — quem o abre e o controller interno.
 *
 * <p>Nada de {@code to_date} sobre o valor guardado: um JSONB preenchido por importacao pode conter
 * qualquer coisa, e uma linha invalida derrubaria a consulta inteira em vez de ser ignorada. O
 * filtro exige o prefixo ISO {@code AAAA-MM-DD} e compara os cinco caracteres de {@code MM-DD} —
 * texto, sem conversao, entao valor vazio, nulo ou "quinta-feira" apenas nao entra na lista.
 */
@Repository
class AniversariantesDoDiaRepositorioJdbc implements AniversariantesDoDiaRepositorio {

    /** A convencao: chave reservada do campo customizado que guarda a data de nascimento. */
    static final String CHAVE_DATA_NASCIMENTO = "data_nascimento";

    private static final DateTimeFormatter MES_E_DIA = DateTimeFormatter.ofPattern("MM-dd");

    private static final String SQL =
            """
            SELECT l.id, l.nome, l.telefone
              FROM lead l
             WHERE EXISTS (
                   SELECT 1 FROM campo_customizado c
                    WHERE c.chave = ? AND c.tipo = 'DATA')
               AND l.telefone IS NOT NULL
               AND l.dados_customizados ->> ? ~ '^[0-9]{4}-[0-9]{2}-[0-9]{2}'
               AND substring(l.dados_customizados ->> ? from 6 for 5) = ?
             ORDER BY l.nome, l.id
            """;

    private final JdbcTemplate jdbc;

    AniversariantesDoDiaRepositorioJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<LeadAniversariante> doDia(MonthDay diaEMes) {
        return jdbc.query(
                SQL,
                (linha, posicao) -> new LeadAniversariante(
                        linha.getObject("id", UUID.class), linha.getString("nome"), linha.getString("telefone")),
                CHAVE_DATA_NASCIMENTO,
                CHAVE_DATA_NASCIMENTO,
                CHAVE_DATA_NASCIMENTO,
                diaEMes.format(MES_E_DIA));
    }
}
