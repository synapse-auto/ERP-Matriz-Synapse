package com.synapse.crm.atendimento.infrastructure.persistencia;

import java.util.UUID;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.synapse.crm.atendimento.application.AtendenteDestinoInvalidoException.Motivo;
import com.synapse.crm.atendimento.application.AtendenteParaTransferenciaRepositorio;
import com.synapse.crm.sharedkernel.identidade.PapelUsuario;
import com.synapse.crm.sharedkernel.persistencia.Pools;

/** Lê o destino no mesmo pool/transação que grava lead e atendimento. */
@Repository
class AtendenteParaTransferenciaRepositorioJdbc implements AtendenteParaTransferenciaRepositorio {

    /** Espelha {@link com.synapse.crm.sharedkernel.identidade.PapelUsuario#recebeAtendimento()}. */
    private static final String ELEGIVEL = "ativo = TRUE AND papel IN ('ATENDENTE','SUBGESTOR')";
    /**
     * Critério específico da lista de destinos exibida no diálogo de transferência.
     *
     * <p>Atendentes ativos continuam disponíveis para transferência explícita mesmo fora do
     * rodízio da IA. Subgestores só aparecem quando estão efetivamente disponíveis para o rodízio,
     * porque a presença e o toggle são os sinais operacionais usados para oferecê-los na lista.
     * O critério de {@link #ativoAtendente(UUID)} permanece separado para não alterar a regra de
     * autorização da transferência nem o contrato da Automação.
     */
    private static final String ELEGIVEL_NA_LISTA = """
            (ativo = TRUE AND papel = 'ATENDENTE')
            OR (ativo = TRUE AND papel = 'SUBGESTOR'
                AND status_presenca = 'ONLINE'
                AND EXISTS (
                    SELECT 1
                      FROM disponibilidade_atendente_ia d
                     WHERE d.atendente_id = usuario.id
                       AND d.disponivel_para_ia = TRUE
                ))
            """;
    private static final String SQL = "SELECT id, nome FROM usuario WHERE id = ? AND " + ELEGIVEL;
    private static final String SQL_LISTAR =
            "SELECT id, nome, papel FROM usuario WHERE " + ELEGIVEL_NA_LISTA + " ORDER BY nome, id";
    private static final String SQL_BUSCAR_POR_NOME =
            "SELECT id, nome FROM usuario WHERE " + ELEGIVEL + " AND nome ILIKE ? ORDER BY nome, id";
    private static final String SQL_MOTIVO = """
            SELECT CASE
                WHEN NOT EXISTS (SELECT 1 FROM usuario WHERE id = ?) THEN 'INEXISTENTE'
                WHEN EXISTS (SELECT 1 FROM usuario WHERE id = ? AND ativo = FALSE) THEN 'INATIVO'
                ELSE 'PAPEL_NAO_ELEGIVEL'
            END
            """;

    private final JdbcTemplate chat;

    AtendenteParaTransferenciaRepositorioJdbc(
            @Qualifier(Pools.CHAT_DATA_SOURCE) DataSource chatDataSource) {
        this.chat = new JdbcTemplate(chatDataSource);
    }

    @Override
    public java.util.Optional<Destino> ativoAtendente(UUID atendenteId) {
        return chat.query(SQL, (linha, indice) -> new Destino(
                        linha.getObject("id", UUID.class), linha.getString("nome")), atendenteId)
                .stream()
                .findFirst();
    }

    @Override
    public java.util.List<Destino> listarAtivos() {
        return chat.query(
                SQL_LISTAR,
                (linha, indice) -> new Destino(
                        linha.getObject("id", UUID.class),
                        linha.getString("nome"),
                        PapelUsuario.valueOf(linha.getString("papel"))));
    }

    @Override
    public java.util.List<Destino> buscarPorNome(String busca) {
        return chat.query(
                        SQL_BUSCAR_POR_NOME,
                        (linha, indice) -> new Destino(
                                linha.getObject("id", UUID.class), linha.getString("nome")),
                        "%" + busca + "%")
                .stream()
                .toList();
    }

    @Override
    public Motivo motivoDaRecusa(UUID atendenteId) {
        String motivo = chat.queryForObject(SQL_MOTIVO, String.class, atendenteId, atendenteId);
        return Motivo.valueOf(motivo);
    }
}
