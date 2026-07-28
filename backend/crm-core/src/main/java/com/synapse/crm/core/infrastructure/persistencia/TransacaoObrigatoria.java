package com.synapse.crm.core.infrastructure.persistencia;

import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Recusa acesso a dados fora de transacao.
 *
 * <p>Fecha a brecha encontrada na E04. Um {@code JdbcTemplate} chamado sem transacao pega uma conexao
 * crua do pool: o {@code doBegin} do gerente nunca roda, entao nao ha {@code SET LOCAL ROLE
 * synapse_app} nem {@code app.usuario_id}. A consulta roda como <b>dona das tabelas</b> — e o
 * PostgreSQL ignora as politicas RLS para o dono. Resultado: uma leitura que deveria devolver os
 * leads de um atendente devolve os de todo mundo, sem erro, sem log, sem sintoma.
 *
 * <p>Era uma protecao que dependia de todo caso de uso lembrar de ser {@code @Transactional}. Este
 * projeto ja foi mordido tres vezes por protecao que dependia de alguem lembrar — as regras ArchUnit
 * que nunca rodaram, as politicas RLS contornadas pelo superusuario, o ArchUnit que importou zero
 * classes. Em todos, o que expos foi <b>falhar alto</b>.
 *
 * <p>Por que aqui e nao numa regra ArchUnit: a regra so pega o que ela sabe procurar — um caminho
 * novo, um {@code @Async}, um listener, uma chamada reflexiva passam batido. Uma excecao em runtime
 * pega qualquer caminho, inclusive os que ninguem previu.
 */
public final class TransacaoObrigatoria {

    private TransacaoObrigatoria() {}

    /**
     * @param operacao o que se tentou fazer, para a mensagem dizer onde consertar
     * @throws AcessoSemTransacaoException sempre que nao houver transacao real ativa
     */
    public static void exigir(String operacao) {
        // isActualTransactionActive() e nao isSynchronizationActive(): a segunda devolve
        // verdadeiro em situacoes de sincronizacao sem transacao de verdade, que e
        // exatamente o caso em que a conexao ainda vem crua.
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new AcessoSemTransacaoException(operacao);
        }
    }

    /** Acesso a dados tentado fora de transacao — ou seja, fora do alcance do RLS. */
    public static class AcessoSemTransacaoException extends IllegalStateException {

        public AcessoSemTransacaoException(String operacao) {
            super("'" + operacao + "' exige transacao ativa. Sem transacao a conexao nao recebe"
                    + " SET LOCAL ROLE nem app.usuario_id, entao a consulta rodaria como dona das"
                    + " tabelas e as politicas RLS seriam ignoradas. Anote o chamador com"
                    + " @Transactional (no caminho de mensagem, com o gerente do pool do chat).");
        }
    }
}
