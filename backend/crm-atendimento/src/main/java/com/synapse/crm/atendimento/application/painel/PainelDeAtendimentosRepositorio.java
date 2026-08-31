package com.synapse.crm.atendimento.application.painel;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Porta de leitura para a lista de conversas — separada de {@code AtendimentoRepositorio} de
 * proposito. Aquele repositorio documenta que nao existe {@code findAll()}: todo acesso e ancorado
 * (por lead, por id). Esta porta e o oposto por natureza — precisa devolver varias linhas — entao
 * ganha uma interface propria de leitura, mesmo padrao que {@code crm-relatorios} ja usa para read
 * models com SQL direto, em vez de forcar o repositorio de escrita a ganhar um metodo que nao serve
 * aos casos de uso de comando.
 */
public interface PainelDeAtendimentosRepositorio {

    /**
     * @param restritoAoProprioAtendente {@code true} quando quem pede so enxerga a propria carteira
     *     (papel ATENDENTE) — a interpretacao exata por visao mora no adaptador, porque e decisao de
     *     consulta, nao de negocio (ATIVOS e sempre "meu" independente da flag; POTENCIAIS e TODOS
     *     nunca sao restritos por ela). A RLS de {@code atendimento} continua sendo quem de fato
     *     impede alcancar o que nao e seu; esta flag so escolhe qual subconjunto do que a RLS ja
     *     permite a consulta devolve.
     */
    List<CartaoAtendimento> listar(
            VisaoAtendimento visao, UUID usuarioId, boolean restritoAoProprioAtendente);

    /**
     * Leitura limitada para composição da inbox; a chave é (sem atendimento aberto, última
     * mensagem, atendimento). O primeiro componente mantém os finalizados depois dos cartões
     * operacionais também quando a fronteira cai entre duas páginas.
     */
    List<CartaoAtendimento> listarPaginado(VisaoAtendimento visao, UUID usuarioId,
            boolean restritoAoProprioAtendente, boolean depoisSemAtendimentoAberto,
            Instant depoisDe, UUID depoisDoId, int limite);

    /**
     * Quantos cartoes {@link #listar} devolveria para a mesma visao — os badges das abas (E17b §Bloco
     * 6). Mesma assinatura, mesma decisao de "meu" vs. "de todos" por visao; o adaptador reaproveita
     * as mesmas condicoes de {@code WHERE}, so trocando a projecao por {@code COUNT(*)}.
     */
    long contar(VisaoAtendimento visao, UUID usuarioId, boolean restritoAoProprioAtendente);
}
