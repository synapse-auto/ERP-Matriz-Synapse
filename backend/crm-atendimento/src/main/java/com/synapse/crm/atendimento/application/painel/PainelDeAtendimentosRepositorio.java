package com.synapse.crm.atendimento.application.painel;

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
}
